package madoku.craft.mixin.client;

import madoku.craft.debug.MadokuDebug;
import madoku.craft.pet.PlayerEntitiesInventory;
import madoku.craft.pet.PlayerEntitiesSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenPetSlotMixin {
	private static ItemStack madokuCraft$lastCreativePetSelection = ItemStack.EMPTY;

	@Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$handlePetSlotClicksInCreative(Slot slot, int slotId, int button, ContainerInput clickType, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (slot == null) {
			debugCreativeEvent("pet.creative_slot_observed", client, null, null, slotId)
				.field("button", button)
				.field("click_input_type", clickType == null ? "null" : clickType.getClass().getName())
				.field("reason", "slot_null")
				.log();
			return;
		}
		if (slotId < 0) {
			debugCreativeEvent("pet.creative_slot_observed", client, slot, slot, slotId)
				.field("button", button)
				.field("click_input_type", clickType == null ? "null" : clickType.getClass().getName())
				.field("reason", "negative_slot_id")
				.log();
			return;
		}

		Slot actualSlot = unwrapCreativeSlot(slot);
		debugCreativeEvent("pet.creative_slot_observed", client, slot, actualSlot, slotId)
			.field("button", button)
			.field("click_input_type", clickType == null ? "null" : clickType.getClass().getName())
			.field("inventory_tab_open", ((CreativeModeInventoryScreenAccessor) this).madokuCraft$isInventoryOpen())
			.log();
		if (!(actualSlot.container instanceof PlayerEntitiesInventory)) {
			rememberCreativePetSelection(client, slot, actualSlot, slotId, clickType);
			return;
		}

		debugCreativeEvent("pet.creative_slot_clicked", client, slot, actualSlot, slotId)
			.field("button", button)
			.field("click_input_type", clickType == null ? "null" : clickType.getClass().getName())
			.log();
		if (client.player == null || client.gameMode == null) {
			debugCreativeEvent("pet.creative_click_forward_failed", client, slot, actualSlot, slotId)
				.field("reason", "client_state_missing")
				.log();
			return;
		}

		if (applyCreativePetSlotWrite(client, slot, actualSlot, slotId, button, clickType)) {
			debugCreativeEvent("pet.creative_slot_applied", client, slot, actualSlot, slotId)
				.field("button", button)
				.log();
			ci.cancel();
			return;
		}

		if (!invokeHandleContainerInput(client, slot, actualSlot, button, clickType)) {
			return;
		}
		debugCreativeEvent("pet.creative_click_forwarded", client, slot, actualSlot, slotId)
			.field("button", button)
			.log();
		ci.cancel();
	}

	private boolean invokeHandleContainerInput(Minecraft client, Slot rawSlot, Slot actualSlot, int button, ContainerInput clickType) {
		if (client == null || client.player == null || client.gameMode == null || clickType == null) {
			debugCreativeEvent("pet.creative_click_forward_failed", client, rawSlot, actualSlot, actualSlot == null ? -1 : actualSlot.index)
				.field("reason", "missing_input")
				.log();
			return false;
		}
		if (actualSlot == null) {
			debugCreativeEvent("pet.creative_click_forward_failed", client, rawSlot, null, -1)
				.field("reason", "actual_slot_missing")
				.log();
			return false;
		}
		MultiPlayerGameMode gameMode = client.gameMode;
		gameMode.handleContainerInput(
			((AbstractContainerScreenAccessor) this).madokuCraft$getMenu().containerId,
			actualSlot.index,
			button,
			clickType,
			client.player
		);
		return true;
	}

	private boolean applyCreativePetSlotWrite(Minecraft client, Slot rawSlot, Slot actualSlot, int slotId, int button, ContainerInput clickType) {
		if (client == null || client.player == null || client.gameMode == null || actualSlot == null) {
			return false;
		}

		AbstractContainerMenu menu = ((AbstractContainerScreenAccessor) this).madokuCraft$getMenu();
		if (menu == null) {
			return false;
		}

		if (clickType != ContainerInput.PICKUP) {
			return false;
		}

		ItemStack carried = menu.getCarried();
		ItemStack mainHand = client.player.getMainHandItem();
		ItemStack remembered = madokuCraft$lastCreativePetSelection;
		boolean hasDirectSource = PlayerEntitiesSystem.isValidPlayerEntity(carried) || PlayerEntitiesSystem.isValidPlayerEntity(mainHand);

		if (!hasDirectSource && actualSlot.hasItem()) {
			ItemStack current = actualSlot.getItem();
			ItemStack pickedUp = current.copyWithCount(1);
			menu.setCarried(pickedUp);
			actualSlot.setByPlayer(ItemStack.EMPTY);
			actualSlot.setChanged();
			menu.slotsChanged(actualSlot.container);
			client.gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, actualSlot.index);
			debugCreativeEvent("pet.creative_slot_write", client, rawSlot, actualSlot, slotId)
				.field("mode", "pickup")
				.field("stack", itemStackSummary(pickedUp))
				.field("reason", PlayerEntitiesSystem.isValidPlayerEntity(remembered) ? "remembered_ignored_for_pickup" : "no_direct_source")
				.log();
			return true;
		}

		ItemStack source = resolveCreativePlacementSource(carried, mainHand, remembered);
		if (source != null && !source.isEmpty() && actualSlot.mayPlace(source)) {
			ItemStack placed = source.copyWithCount(1);
			actualSlot.setByPlayer(placed);
			actualSlot.setChanged();
			menu.slotsChanged(actualSlot.container);
			client.gameMode.handleCreativeModeItemAdd(placed, actualSlot.index);
			debugCreativeEvent("pet.creative_slot_write", client, rawSlot, actualSlot, slotId)
				.field("mode", "place")
				.field("stack", itemStackSummary(placed))
				.field("source", itemStackSummary(source))
				.field("source_type", resolveCreativePlacementSourceType(carried, mainHand, remembered))
				.log();
			madokuCraft$lastCreativePetSelection = placed.copyWithCount(1);
			return true;
		}

		return false;
	}

	private void rememberCreativePetSelection(Minecraft client, Slot rawSlot, Slot actualSlot, int slotId, ContainerInput clickType) {
		if (clickType != ContainerInput.PICKUP || actualSlot == null) {
			return;
		}

		ItemStack slotStack = actualSlot.getItem();
		if (!PlayerEntitiesSystem.isValidPlayerEntity(slotStack)) {
			return;
		}

		madokuCraft$lastCreativePetSelection = slotStack.copyWithCount(1);
		debugCreativeEvent("pet.creative_selection_captured", client, rawSlot, actualSlot, slotId)
			.field("stack", itemStackSummary(slotStack))
			.log();
	}

	private static ItemStack resolveCreativePlacementSource(ItemStack carried, ItemStack mainHand, ItemStack remembered) {
		if (PlayerEntitiesSystem.isValidPlayerEntity(carried)) {
			return carried;
		}
		if (PlayerEntitiesSystem.isValidPlayerEntity(mainHand)) {
			return mainHand;
		}
		if (PlayerEntitiesSystem.isValidPlayerEntity(remembered)) {
			return remembered;
		}
		return ItemStack.EMPTY;
	}

	private static String resolveCreativePlacementSourceType(ItemStack carried, ItemStack mainHand, ItemStack remembered) {
		if (PlayerEntitiesSystem.isValidPlayerEntity(carried)) {
			return "menu_carried";
		}
		if (PlayerEntitiesSystem.isValidPlayerEntity(mainHand)) {
			return "main_hand";
		}
		if (PlayerEntitiesSystem.isValidPlayerEntity(remembered)) {
			return "remembered_selection";
		}
		return "none";
	}

	private static String itemStackSummary(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "empty";
		}
		return stack.getItem().toString();
	}

	private static MadokuDebug.EventBuilder debugCreativeEvent(String metricId, Minecraft client, Slot rawSlot, Slot actualSlot, int slotId) {
		MadokuDebug.EventBuilder builder = MadokuDebug.event(metricId, MadokuDebug.Domain.PET)
			.side(MadokuDebug.Side.CLIENT)
			.subject("creative_pet_slot:" + slotId)
			.field("slot_id", slotId);
		if (client != null && client.level != null) {
			builder
				.tick(client.level.getGameTime())
				.world(client.level.dimension().toString());
		}
		if (client != null && client.player != null) {
			builder.field("player_uuid", client.player.getUUID());
		}
		if (rawSlot != null) {
			builder
				.field("raw_slot_class", rawSlot.getClass().getSimpleName())
				.field("raw_slot_index", rawSlot.index)
				.field("raw_slot_x", rawSlot.x)
				.field("raw_slot_y", rawSlot.y)
				.field("raw_container", rawSlot.container.getClass().getSimpleName());
		}
		if (actualSlot != null) {
			builder
				.field("actual_slot_class", actualSlot.getClass().getSimpleName())
				.field("actual_slot_index", actualSlot.index)
				.field("actual_slot_x", actualSlot.x)
				.field("actual_slot_y", actualSlot.y)
				.field("actual_container", actualSlot.container.getClass().getSimpleName());
		}
		return builder;
	}

	private static Slot unwrapCreativeSlot(Slot slot) {
		if (slot instanceof CreativeModeSlotWrapperAccessor wrapperAccessor) {
			Slot target = wrapperAccessor.madokuCraft$getTarget();
			return target == null ? slot : target;
		}
		return slot;
	}
}

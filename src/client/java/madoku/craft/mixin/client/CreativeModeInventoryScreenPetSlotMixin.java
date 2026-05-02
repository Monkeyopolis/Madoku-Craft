package madoku.craft.mixin.client;

import madoku.craft.pet.PlayerEntitiesInventory;
import madoku.craft.pet.PlayerEntitiesSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
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
	@Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$handlePetSlotClicksInCreative(Slot slot, int slotId, int button, ContainerInput clickType, CallbackInfo ci) {
		if (slot == null || slotId < 0) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		Slot actualSlot = unwrapCreativeSlot(slot);
		if (!(actualSlot.container instanceof PlayerEntitiesInventory)) {
			return;
		}
		if (client.player == null || client.gameMode == null) {
			return;
		}

		if (applyCreativePetSlotWrite(client, actualSlot, clickType)) {
			ci.cancel();
		}
	}

	private boolean applyCreativePetSlotWrite(Minecraft client, Slot actualSlot, ContainerInput clickType) {
		if (client == null || client.player == null || client.gameMode == null || actualSlot == null) {
			return false;
		}

		AbstractContainerMenu menu = ((AbstractContainerScreenAccessor) this).madokuCraft$getMenu();
		if (menu == null || clickType != ContainerInput.PICKUP) {
			return false;
		}

		ItemStack carried = menu.getCarried();
		if (!PlayerEntitiesSystem.isValidPlayerEntity(carried)) {
			// Let vanilla creative handling remove/adjust occupied slots.
			// Ignore empty-slot no-source clicks to prevent ghost writes.
			return !actualSlot.hasItem();
		}

		if (!actualSlot.mayPlace(carried)) {
			return false;
		}

		ItemStack placed = carried.copyWithCount(1);
		actualSlot.setByPlayer(placed);
		actualSlot.setChanged();
		menu.slotsChanged(actualSlot.container);
		client.gameMode.handleCreativeModeItemAdd(placed, actualSlot.index);
		return true;
	}

	private static Slot unwrapCreativeSlot(Slot slot) {
		if (slot instanceof CreativeModeSlotWrapperAccessor wrapperAccessor) {
			Slot target = wrapperAccessor.madokuCraft$getTarget();
			return target == null ? slot : target;
		}
		return slot;
	}
}

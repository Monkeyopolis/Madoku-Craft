package madoku.craft.mixin;

import madoku.craft.pet.PlayerEntitiesHolder;
import madoku.craft.pet.PlayerEntitiesInventory;
import madoku.craft.pet.PlayerEntitiesSystem;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerCreativePetSlotMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$handleCreativePetSlots(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
		if (player == null || packet == null || !player.isCreative()) {
			return;
		}

		int slotNum = packet.slotNum();
		if (slotNum < PlayerEntitiesSystem.FIRST_SLOT_INDEX || slotNum >= PlayerEntitiesSystem.FIRST_SLOT_INDEX + PlayerEntitiesSystem.SLOT_COUNT) {
			return;
		}

		if (!(player instanceof PlayerEntitiesHolder holder)) {
			return;
		}

		PlayerEntitiesInventory inventory = holder.madokuCraft$getPlayerEntitiesInventory();
		if (inventory == null) {
			return;
		}

		int petSlot = slotNum - PlayerEntitiesSystem.FIRST_SLOT_INDEX;
		ItemStack packetStack = packet.itemStack();
		ItemStack carried = player.containerMenu == null ? ItemStack.EMPTY : player.containerMenu.getCarried();
		ItemStack beforeSlotStack = inventory.getItem(petSlot).copy();
		ItemStack resolved = ItemStack.EMPTY;
		boolean validPacketStack = false;
		boolean canPlaceFromCarried = false;
		boolean canPlaceFromInventory = false;
		String action = "ignore_empty_packet";
		if (packetStack.isEmpty()) {
			if (beforeSlotStack.isEmpty()) {
				action = "noop_empty_packet";
				PlayerEntitiesSystem.debugCreativePetSlotPacket(
					player,
					slotNum,
					petSlot,
					packetStack,
					carried,
					beforeSlotStack,
					beforeSlotStack,
					validPacketStack,
					canPlaceFromCarried,
					canPlaceFromInventory,
					action
				);
				madokuCraft$resyncMenus();
				ci.cancel();
				return;
			}

			action = "clear_from_empty_packet";
			inventory.setItem(petSlot, ItemStack.EMPTY);
			inventory.setChanged();
			PlayerEntitiesSystem.debugCreativePetSlotPacket(
				player,
				slotNum,
				petSlot,
				packetStack,
				carried,
				beforeSlotStack,
				ItemStack.EMPTY,
				validPacketStack,
				canPlaceFromCarried,
				canPlaceFromInventory,
				action
			);
			madokuCraft$resyncMenus();
			ci.cancel();
			return;
		}

		validPacketStack = PlayerEntitiesSystem.isValidPlayerEntity(packetStack);
		canPlaceFromCarried = PlayerEntitiesSystem.isValidPlayerEntity(carried)
			&& ItemStack.isSameItemSameComponents(packetStack, carried);
		canPlaceFromInventory = false;
		if (!validPacketStack) {
			action = "reject_auth_failed";
			PlayerEntitiesSystem.debugCreativePetSlotPacket(
				player,
				slotNum,
				petSlot,
				packetStack,
				carried,
				beforeSlotStack,
				resolved,
				validPacketStack,
				canPlaceFromCarried,
				canPlaceFromInventory,
				action
			);
			madokuCraft$resyncMenus();
			ci.cancel();
			return;
		}

		// Creative packet ordering does not reliably preserve carried stack state.
		// Trust validated packet stack for this custom slot; client-side mixin gates intent.
		action = "apply_from_packet";
		resolved = packetStack.copyWithCount(1);
		if (ItemStack.isSameItemSameComponents(beforeSlotStack, resolved) && beforeSlotStack.getCount() == resolved.getCount()) {
			action = "noop_same_stack";
			PlayerEntitiesSystem.debugCreativePetSlotPacket(
				player,
				slotNum,
				petSlot,
				packetStack,
				carried,
				beforeSlotStack,
				beforeSlotStack,
				validPacketStack,
				canPlaceFromCarried,
				canPlaceFromInventory,
				action
			);
			madokuCraft$resyncMenus();
			ci.cancel();
			return;
		}

		inventory.setItem(petSlot, resolved);
		inventory.setChanged();
		PlayerEntitiesSystem.debugCreativePetSlotPacket(
			player,
			slotNum,
			petSlot,
			packetStack,
			carried,
			beforeSlotStack,
			resolved,
			validPacketStack,
			canPlaceFromCarried,
			canPlaceFromInventory,
			action
		);
		player.inventoryMenu.broadcastChanges();
		if (player.containerMenu != null && player.containerMenu != player.inventoryMenu) {
			player.containerMenu.broadcastChanges();
		}
		ci.cancel();
	}

	private void madokuCraft$resyncMenus() {
		if (player == null) {
			return;
		}
		player.inventoryMenu.broadcastChanges();
		if (player.containerMenu != null && player.containerMenu != player.inventoryMenu) {
			player.containerMenu.broadcastChanges();
		}
	}
}

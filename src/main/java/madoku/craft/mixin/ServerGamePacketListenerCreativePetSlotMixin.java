package madoku.craft.mixin;

import madoku.craft.pet.PetComponentsManager.PetHolder;
import madoku.craft.pet.PetComponentsManager.PetInventory;
import madoku.craft.pet.PetEntitiesManager;
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
		if (slotNum < PetEntitiesManager.FIRST_SLOT_INDEX || slotNum >= PetEntitiesManager.FIRST_SLOT_INDEX + PetEntitiesManager.SLOT_COUNT) {
			return;
		}

		if (!(player instanceof PetHolder holder)) {
			return;
		}

		PetInventory inventory = holder.madokuCraft$getPetInventory();
		if (inventory == null) {
			return;
		}

		int petSlot = slotNum - PetEntitiesManager.FIRST_SLOT_INDEX;
		ItemStack packetStack = packet.itemStack();
		ItemStack beforeSlotStack = inventory.getItem(petSlot).copy();
		ItemStack resolved = ItemStack.EMPTY;
		boolean validPacketStack = false;
		if (packetStack.isEmpty()) {
			if (beforeSlotStack.isEmpty()) {
				madokuCraft$resyncMenus();
				ci.cancel();
				return;
			}

			inventory.setItem(petSlot, ItemStack.EMPTY);
			inventory.setChanged();
			madokuCraft$resyncMenus();
			ci.cancel();
			return;
		}

		validPacketStack = PetEntitiesManager.isValid(packetStack);
		if (!validPacketStack) {
			madokuCraft$resyncMenus();
			ci.cancel();
			return;
		}

		// Creative packet ordering does not reliably preserve carried stack state.
		// Trust validated packet stack for this custom slot; client-side mixin gates intent.
		resolved = packetStack.copyWithCount(1);
		if (ItemStack.isSameItemSameComponents(beforeSlotStack, resolved) && beforeSlotStack.getCount() == resolved.getCount()) {
			madokuCraft$resyncMenus();
			ci.cancel();
			return;
		}

		inventory.setItem(petSlot, resolved);
		inventory.setChanged();
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



package madoku.craft.mixin.pet;

import madoku.craft.pet.PetComponentsAPIManager.PetHolder;
import madoku.craft.pet.PetComponentsAPIManager.PetInventory;
import madoku.craft.pet.PetEntitiesAPIManager;
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
		if (slotNum < PetEntitiesAPIManager.FIRST_SLOT_INDEX || slotNum >= PetEntitiesAPIManager.FIRST_SLOT_INDEX + PetEntitiesAPIManager.SLOT_COUNT) {
			return;
		}

		if (!(player instanceof PetHolder holder)) {
			return;
		}

		PetInventory inventory = holder.madokuCraft$getPetInventory();
		if (inventory == null) {
			return;
		}

		int petSlot = slotNum - PetEntitiesAPIManager.FIRST_SLOT_INDEX;
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
			madokuCraft$resyncMenus();
			ci.cancel();
			return;
		}

		validPacketStack = PetEntitiesAPIManager.isValid(packetStack);
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



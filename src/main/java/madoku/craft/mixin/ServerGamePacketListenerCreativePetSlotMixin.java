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
		if (player == null || packet == null) {
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
		ItemStack resolved = PlayerEntitiesSystem.isValidPlayerEntity(packetStack)
			? packetStack.copyWithCount(1)
			: ItemStack.EMPTY;

		inventory.setItem(petSlot, resolved);
		inventory.setChanged();
		player.inventoryMenu.broadcastChanges();
		if (player.containerMenu != null && player.containerMenu != player.inventoryMenu) {
			player.containerMenu.broadcastChanges();
		}
		ci.cancel();
	}
}

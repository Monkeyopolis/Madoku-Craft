package madoku.craft.mixin.pet;

import madoku.craft.pet.PetAbilitiesManager;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerPetLeftClickMixin {
	@Inject(method = "handleAnimate", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$preventWebStunnedPlayerSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
		ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
		if (PetAbilitiesManager.isWebStunned(listener.getPlayer())) {
			ci.cancel();
		}
	}

	@Inject(method = "handleAttack", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$preventWebStunnedPlayerAttack(
		net.minecraft.network.protocol.game.ServerboundAttackPacket packet,
		CallbackInfo ci
	) {
		ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
		if (PetAbilitiesManager.isWebStunned(listener.getPlayer())) {
			ci.cancel();
			return;
		}
		PetAbilitiesManager.handlePlayerLeftClick(listener.getPlayer());
	}

	@Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$preventWebStunnedPlayerInteraction(
		net.minecraft.network.protocol.game.ServerboundInteractPacket packet,
		CallbackInfo ci
	) {
		ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
		if (PetAbilitiesManager.isWebStunned(listener.getPlayer())) {
			ci.cancel();
		}
	}

	@Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$preventWebStunnedPlayerItemUse(
		net.minecraft.network.protocol.game.ServerboundUseItemPacket packet,
		CallbackInfo ci
	) {
		ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
		if (PetAbilitiesManager.isWebStunned(listener.getPlayer())) {
			ci.cancel();
		}
	}

	@Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$preventWebStunnedPlayerBlockUse(
		net.minecraft.network.protocol.game.ServerboundUseItemOnPacket packet,
		CallbackInfo ci
	) {
		ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
		if (PetAbilitiesManager.isWebStunned(listener.getPlayer())) {
			ci.cancel();
		}
	}
}


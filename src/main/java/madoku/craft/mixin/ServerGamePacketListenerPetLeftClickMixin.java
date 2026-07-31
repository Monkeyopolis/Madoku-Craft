package madoku.craft.mixin;

import madoku.craft.pet.PetAbilitiesManager;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerPetLeftClickMixin {
	@Inject(method = "handleAnimate", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$handleMainHandSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
		if (PetAbilitiesManager.isWebStunned(((ServerGamePacketListenerImpl) (Object) this).getPlayer())) {
			ci.cancel();
			return;
		}
		if (packet.getHand() == InteractionHand.MAIN_HAND) {
			PetAbilitiesManager.handlePlayerLeftClick(((ServerGamePacketListenerImpl) (Object) this).getPlayer());
		}
	}

	@Inject(method = "handleAttack", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$preventWebStunnedPlayerAttack(
		net.minecraft.network.protocol.game.ServerboundAttackPacket packet,
		CallbackInfo ci
	) {
		if (PetAbilitiesManager.isWebStunned(((ServerGamePacketListenerImpl) (Object) this).getPlayer())) {
			ci.cancel();
		}
	}

	@Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$preventWebStunnedPlayerInteraction(
		net.minecraft.network.protocol.game.ServerboundInteractPacket packet,
		CallbackInfo ci
	) {
		if (PetAbilitiesManager.isWebStunned(((ServerGamePacketListenerImpl) (Object) this).getPlayer())) {
			ci.cancel();
		}
	}

	@Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$preventWebStunnedPlayerItemUse(
		net.minecraft.network.protocol.game.ServerboundUseItemPacket packet,
		CallbackInfo ci
	) {
		if (PetAbilitiesManager.isWebStunned(((ServerGamePacketListenerImpl) (Object) this).getPlayer())) {
			ci.cancel();
		}
	}

	@Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$preventWebStunnedPlayerBlockUse(
		net.minecraft.network.protocol.game.ServerboundUseItemOnPacket packet,
		CallbackInfo ci
	) {
		if (PetAbilitiesManager.isWebStunned(((ServerGamePacketListenerImpl) (Object) this).getPlayer())) {
			ci.cancel();
		}
	}
}

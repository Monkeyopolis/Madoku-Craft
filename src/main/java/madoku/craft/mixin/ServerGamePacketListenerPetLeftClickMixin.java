package madoku.craft.mixin;

import madoku.craft.pet.PetAbilitiesManager;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerPetLeftClickMixin {
	private static final long MADOKU_CRAFT_BLOCK_ACTION_SUPPRESSION_TICKS = 10L;
	private static final long MADOKU_CRAFT_INTERACTION_SUPPRESSION_TICKS = 1L;
	@org.spongepowered.asm.mixin.Unique
	private boolean madokuCraft$destroyingBlock;
	@org.spongepowered.asm.mixin.Unique
	private long madokuCraft$suppressSwingUntilTick = Long.MIN_VALUE;

	@Inject(method = "handleAnimate", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$handleMainHandSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
		ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
		if (PetAbilitiesManager.isWebStunned(listener.getPlayer())) {
			ci.cancel();
			return;
		}
		if (packet.getHand() != InteractionHand.MAIN_HAND || madokuCraft$destroyingBlock) {
			return;
		}
		long gameTime = listener.getPlayer().level().getGameTime();
		if (gameTime < madokuCraft$suppressSwingUntilTick) {
			return;
		}
		PetAbilitiesManager.handlePlayerLeftClick(listener.getPlayer());
	}

	@Inject(method = "handlePlayerAction", at = @At("HEAD"))
	private void madokuCraft$trackBlockAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
		switch (packet.getAction()) {
			case START_DESTROY_BLOCK -> {
				madokuCraft$destroyingBlock = true;
				madokuCraft$suppressSwingUntilTick = listener.getPlayer().level().getGameTime() + MADOKU_CRAFT_BLOCK_ACTION_SUPPRESSION_TICKS;
			}
			case ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK -> {
				madokuCraft$destroyingBlock = false;
				madokuCraft$suppressSwingUntilTick = listener.getPlayer().level().getGameTime() + MADOKU_CRAFT_BLOCK_ACTION_SUPPRESSION_TICKS;
			}
			default -> {
			}
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
		long gameTime = listener.getPlayer().level().getGameTime();
		if (madokuCraft$destroyingBlock || gameTime < madokuCraft$suppressSwingUntilTick) {
			return;
		}
		PetAbilitiesManager.handlePlayerLeftClick(listener.getPlayer());
		madokuCraft$suppressSwingUntilTick = listener.getPlayer().level().getGameTime() + MADOKU_CRAFT_INTERACTION_SUPPRESSION_TICKS;
	}

	@Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$preventWebStunnedPlayerInteraction(
		net.minecraft.network.protocol.game.ServerboundInteractPacket packet,
		CallbackInfo ci
	) {
		ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
		if (PetAbilitiesManager.isWebStunned(listener.getPlayer())) {
			ci.cancel();
			return;
		}
		madokuCraft$suppressSwingUntilTick = listener.getPlayer().level().getGameTime() + MADOKU_CRAFT_INTERACTION_SUPPRESSION_TICKS;
	}

	@Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$preventWebStunnedPlayerItemUse(
		net.minecraft.network.protocol.game.ServerboundUseItemPacket packet,
		CallbackInfo ci
	) {
		ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
		if (PetAbilitiesManager.isWebStunned(listener.getPlayer())) {
			ci.cancel();
			return;
		}
		madokuCraft$suppressSwingUntilTick = listener.getPlayer().level().getGameTime() + MADOKU_CRAFT_INTERACTION_SUPPRESSION_TICKS;
	}

	@Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$preventWebStunnedPlayerBlockUse(
		net.minecraft.network.protocol.game.ServerboundUseItemOnPacket packet,
		CallbackInfo ci
	) {
		ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
		if (PetAbilitiesManager.isWebStunned(listener.getPlayer())) {
			ci.cancel();
			return;
		}
		madokuCraft$suppressSwingUntilTick = listener.getPlayer().level().getGameTime() + MADOKU_CRAFT_INTERACTION_SUPPRESSION_TICKS;
	}
}

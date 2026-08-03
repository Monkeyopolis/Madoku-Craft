package madoku.craft.mixin;

import madoku.craft.pet.MadokuPetManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTeleportPetMixin {
	@Inject(
		method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
		at = @At("TAIL")
	)
	private void madokuCraft$reconcilePetsAfterTeleport(
		TeleportTransition transition,
		CallbackInfoReturnable<ServerPlayer> cir
	) {
		if (cir.getReturnValue() != null) {
			MadokuPetManager.handlePlayerTeleport((ServerPlayer) (Object) this);
		}
	}

	@Inject(method = "teleportTo(DDD)V", at = @At("TAIL"))
	private void madokuCraft$reconcilePetsAfterAbsoluteTeleport(
		double x,
		double y,
		double z,
		CallbackInfo ci
	) {
		MadokuPetManager.handlePlayerTeleport((ServerPlayer) (Object) this);
	}

	@Inject(method = "teleportRelative(DDD)V", at = @At("TAIL"))
	private void madokuCraft$reconcilePetsAfterRelativeTeleport(
		double x,
		double y,
		double z,
		CallbackInfo ci
	) {
		MadokuPetManager.handlePlayerTeleport((ServerPlayer) (Object) this);
	}

	@Inject(
		method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",
		at = @At("TAIL")
	)
	private void madokuCraft$reconcilePetsAfterLevelTeleport(
		ServerLevel level,
		double x,
		double y,
		double z,
		Set<Relative> relative,
		float yaw,
		float pitch,
		boolean asPassenger,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (cir.getReturnValue()) {
			MadokuPetManager.handlePlayerTeleport((ServerPlayer) (Object) this);
		}
	}

	@Inject(method = "snapTo(DDD)V", at = @At("TAIL"))
	private void madokuCraft$reconcilePetsAfterPositionSnap(
		double x,
		double y,
		double z,
		CallbackInfo ci
	) {
		MadokuPetManager.handlePlayerTeleport((ServerPlayer) (Object) this);
	}
}

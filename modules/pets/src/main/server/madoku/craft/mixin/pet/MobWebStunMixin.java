package madoku.craft.mixin.pet;

import madoku.craft.java.pet.PetAbilitiesAPIManager;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobWebStunMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disableWebStunnedMobAi(CallbackInfo ci) {
		Mob mob = (Mob) (Object) this;
		if (PetAbilitiesAPIManager.isWebStunned(mob)) {
			Vec3 movement = mob.getDeltaMovement();
			double downwardVelocity = movement.y;
			if (mob.isNoGravity()) {
				downwardVelocity = Math.max(-0.5D, Math.min(-0.08D, movement.y - 0.08D));
			}
			mob.setDeltaMovement(new Vec3(0.0D, downwardVelocity, 0.0D));
			mob.getNavigation().stop();
			ci.cancel();
		}
	}

	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disableWebStunnedMobAttack(
		net.minecraft.server.level.ServerLevel level,
		net.minecraft.world.entity.Entity target,
		org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir
	) {
		if (PetAbilitiesAPIManager.isWebStunned((Mob) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}

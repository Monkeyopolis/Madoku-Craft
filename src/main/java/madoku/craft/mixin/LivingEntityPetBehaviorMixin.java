package madoku.craft.mixin;

import madoku.craft.pet.PetComponentsManager;
import madoku.craft.pet.PetAbilitiesManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityPetBehaviorMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$stopWebStunnedMovement(Vec3 travelVector, CallbackInfo ci) {
		LivingEntity entity = (LivingEntity) (Object) this;
		if (PetAbilitiesManager.isWebStunned(entity)) {
			entity.setDeltaMovement(Vec3.ZERO);
			ci.cancel();
		}
	}

	@Inject(method = "getSpeed", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$scaleWebSlowMovement(CallbackInfoReturnable<Float> cir) {
		LivingEntity entity = (LivingEntity) (Object) this;
		cir.setReturnValue(PetAbilitiesManager.scaleWebMovementSpeed(entity, cir.getReturnValue()));
	}

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$preventWebStunnedAttacks(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (source != null && source.getEntity() instanceof LivingEntity attacker && PetAbilitiesManager.isWebStunned(attacker)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disableManagedPetPickability(CallbackInfoReturnable<Boolean> cir) {
		if (PetComponentsManager.isManaged((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disableManagedPetPushability(CallbackInfoReturnable<Boolean> cir) {
		if (PetComponentsManager.isManaged((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "push", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$cancelManagedPetPush(Entity entity, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (PetComponentsManager.isManaged(self) || PetComponentsManager.isManaged(entity)) {
			ci.cancel();
		}
	}
}


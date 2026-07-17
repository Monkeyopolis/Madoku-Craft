package madoku.craft.mixin;

import madoku.craft.pet.PlayerEntitiesSystem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityPetBehaviorMixin {
	@Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$hidePetSelection(CallbackInfoReturnable<Boolean> cir) {
		if (PlayerEntitiesSystem.isManagedPet((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disablePetPushing(CallbackInfoReturnable<Boolean> cir) {
		if (PlayerEntitiesSystem.isManagedPet((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isAttackable", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disablePetAttackability(CallbackInfoReturnable<Boolean> cir) {
		if (PlayerEntitiesSystem.isManagedPet((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "skipAttackInteraction", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$skipPetAttackInteraction(Entity attacker, CallbackInfoReturnable<Boolean> cir) {
		if (PlayerEntitiesSystem.isManagedPet((Entity) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "canBeHitByProjectile", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disablePetProjectileHits(CallbackInfoReturnable<Boolean> cir) {
		if (PlayerEntitiesSystem.isManagedPet((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "igniteForSeconds", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disablePetIgniteForSeconds(float seconds, CallbackInfo ci) {
		if (PlayerEntitiesSystem.isManagedPet((Entity) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "igniteForTicks", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disablePetIgniteForTicks(int ticks, CallbackInfo ci) {
		if (PlayerEntitiesSystem.isManagedPet((Entity) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disablePetCollision(Entity other, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (PlayerEntitiesSystem.isManagedPet(self) || PlayerEntitiesSystem.isManagedPet(other)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canBeCollidedWith", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disablePetBeingCollidedWith(Entity other, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (PlayerEntitiesSystem.isManagedPet(self) || PlayerEntitiesSystem.isManagedPet(other)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$reduceManagedPetSoundAtSource(SoundEvent soundEvent, float volume, float pitch, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (!PlayerEntitiesSystem.isManagedPet(self)) {
			return;
		}
		float adjustedVolume = PlayerEntitiesSystem.soundVolume(self, volume);
		if (Math.abs(adjustedVolume - volume) < 1.0E-4F) {
			return;
		}
		if (!self.isSilent()) {
			self.level().playSound(null, self.getX(), self.getY(), self.getZ(), soundEvent, self.getSoundSource(), adjustedVolume, pitch);
		}
		ci.cancel();
	}
}


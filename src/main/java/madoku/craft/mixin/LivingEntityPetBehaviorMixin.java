package madoku.craft.mixin;

import madoku.craft.pet.PlayerEntitiesSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityPetBehaviorMixin {
	@Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disableManagedPetPickability(CallbackInfoReturnable<Boolean> cir) {
		if (PlayerEntitiesSystem.isManagedPet((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disableManagedPetPushability(CallbackInfoReturnable<Boolean> cir) {
		if (PlayerEntitiesSystem.isManagedPet((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "push", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$cancelManagedPetPush(Entity entity, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (PlayerEntitiesSystem.isManagedPet(self) || PlayerEntitiesSystem.isManagedPet(entity)) {
			ci.cancel();
		}
	}
}


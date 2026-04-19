package madoku.craft.mixin;

import madoku.craft.pet.PlayerEntitiesSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobPetSoundMixin {
	@Inject(method = "getAmbientSoundInterval", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$slowManagedPetAmbientSounds(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (PlayerEntitiesSystem.isManagedPet(self)) {
			cir.setReturnValue(PlayerEntitiesSystem.ambientSoundInterval(self, cir.getReturnValueI()));
		}
	}
}

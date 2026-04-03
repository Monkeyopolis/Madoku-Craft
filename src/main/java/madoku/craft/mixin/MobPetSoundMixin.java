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
		if (PlayerEntitiesSystem.isManagedPet((Entity) (Object) this)) {
			cir.setReturnValue(PlayerEntitiesSystem.ambientSoundInterval(cir.getReturnValueI()));
		}
	}
}

package madoku.craft.mixin;

import madoku.craft.mob.system.MadokuMob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.monster.zombie.ZombifiedPiglin")
public abstract class ZombifiedPiglinAggressionTickMixin {
	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void madoku$applyCustomZombifiedPiglinAggression(CallbackInfo ci) {
		MadokuMob.applyCustomZombifiedPiglinAggressionTick((Zombie) (Object) this);
	}
}

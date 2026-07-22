package madoku.craft.mixin;

import madoku.craft.mob.system.EntityBehaviorsManager;

import madoku.craft.mob.system.EntityBehaviorsManager.DrownedBehavior;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class DrownedRuntimeTickMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void madokuCraft$tickRangedDrownedRuntime(CallbackInfo ci) {
		if (!((Object) this instanceof Drowned drowned)) {
			return;
		}
		EntityBehaviorsManager.DrownedBehavior.tickRangedDrownedRuntime(drowned);
	}
}

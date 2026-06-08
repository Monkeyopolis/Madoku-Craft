package madoku.craft.mixin;

import madoku.craft.mob.system.MadokuMobSkeleton;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class SkeletonRuntimeTickMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void madokuCraft$tickRangedSkeletonRuntime(CallbackInfo ci) {
		if (!((Object) this instanceof AbstractSkeleton skeleton)) {
			return;
		}
		MadokuMobSkeleton.tickRangedSkeletonRuntime(skeleton);
	}
}

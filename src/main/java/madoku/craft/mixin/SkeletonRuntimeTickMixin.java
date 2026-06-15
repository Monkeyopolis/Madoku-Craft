package madoku.craft.mixin;

import madoku.craft.mob.system.MadokuMobBogged;
import madoku.craft.mob.system.MadokuMobParched;
import madoku.craft.mob.system.MadokuMobSkeleton;
import madoku.craft.mob.system.MadokuMobStray;
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
		if (skeleton.getType() == net.minecraft.world.entity.EntityType.STRAY) {
			MadokuMobStray.tickRangedSkeletonRuntime(skeleton);
			return;
		}
		if (skeleton.getType() == net.minecraft.world.entity.EntityType.BOGGED) {
			MadokuMobBogged.tickRangedSkeletonRuntime(skeleton);
			return;
		}
		if (skeleton.getType() == net.minecraft.world.entity.EntityType.PARCHED) {
			MadokuMobParched.tickRangedSkeletonRuntime(skeleton);
			return;
		}
		if (skeleton.getType() == net.minecraft.world.entity.EntityType.WITHER_SKELETON) {
			MadokuMobSkeleton.tickRangedSkeletonRuntime(skeleton);
			return;
		}
		MadokuMobSkeleton.tickRangedSkeletonRuntime(skeleton);
	}
}

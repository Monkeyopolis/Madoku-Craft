package madoku.craft.mixin;

import madoku.craft.mob.system.MadokuMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.monster.piglin.PiglinAi")
public abstract class PiglinZombifiedAvoidReplaceMixin {
	@Inject(
		method = "isNearZombified(Lnet/minecraft/world/entity/monster/piglin/Piglin;)Z",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void madoku$disableVanillaZombifiedProximityCheck(Piglin piglin, CallbackInfoReturnable<Boolean> cir) {
		if (MadokuMob.shouldReplaceVanillaPiglinZombifiedAvoid()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(
		method = "setAvoidTargetAndDontHuntForAWhile(Lnet/minecraft/world/entity/monster/piglin/Piglin;Lnet/minecraft/world/entity/LivingEntity;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void madoku$disableVanillaAvoidTargetForZombified(
		Piglin piglin,
		LivingEntity target,
		CallbackInfo ci
	) {
		if (MadokuMob.shouldReplaceVanillaPiglinZombifiedAvoid() && target != null && target.getType() == net.minecraft.world.entity.EntityType.ZOMBIFIED_PIGLIN) {
			ci.cancel();
		}
	}
}

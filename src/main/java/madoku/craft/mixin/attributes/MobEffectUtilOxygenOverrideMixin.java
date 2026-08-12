package madoku.craft.mixin.attributes;

import madoku.craft.attributes.oxygen.MadokuOxygenManager;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.effect.MobEffectUtil")
public abstract class MobEffectUtilOxygenOverrideMixin {
	@Inject(method = "hasWaterBreathing", at = @At("HEAD"), cancellable = true)
	private static void madokuCraft$disableVanillaWaterBreathing(
		LivingEntity entity,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (MadokuOxygenManager.shouldSuppressVanillaBreathingEffects(entity)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "shouldEffectsRefillAirsupply", at = @At("HEAD"), cancellable = true)
	private static void madokuCraft$disableVanillaAirsupplyRefill(
		LivingEntity entity,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (entity != null
			&& entity.isInWater()
			&& MadokuOxygenManager.shouldSuppressVanillaBreathingEffects(entity)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "hasDigSpeed", at = @At("HEAD"), cancellable = true)
	private static void madokuCraft$disableVanillaDigSpeed(
		LivingEntity entity,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (MadokuOxygenManager.shouldSuppressVanillaConduitMiningSpeed(entity)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "getDigSpeedAmplification", at = @At("HEAD"), cancellable = true)
	private static void madokuCraft$disableVanillaDigSpeedAmplification(
		LivingEntity entity,
		CallbackInfoReturnable<Integer> cir
	) {
		if (MadokuOxygenManager.shouldSuppressVanillaConduitMiningSpeed(entity)) {
			cir.setReturnValue(0);
		}
	}
}

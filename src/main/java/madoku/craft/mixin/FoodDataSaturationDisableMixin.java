package madoku.craft.mixin;

import madoku.craft.hunger.MadokuHungerManager;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FoodData.class)
public abstract class FoodDataSaturationDisableMixin {
	@Shadow
	private float saturationLevel;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void madokuCraft$clearInitialSaturation(CallbackInfo ci) {
		if (!MadokuHungerManager.isEnabled()) {
			return;
		}
		saturationLevel = 0.0f;
	}

	@ModifyArg(
		method = "eat(IF)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/food/FoodData;add(IF)V"
		),
		index = 1
	)
	private float madokuCraft$removeSaturationFromEatByValues(float saturation) {
		if (!MadokuHungerManager.isEnabled()) {
			return saturation;
		}
		return 0.0f;
	}

	@ModifyArg(
		method = "eat(Lnet/minecraft/world/food/FoodProperties;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/food/FoodData;add(IF)V"
		),
		index = 1
	)
	private float madokuCraft$removeSaturationFromEatByProperties(float saturation) {
		if (!MadokuHungerManager.isEnabled()) {
			return saturation;
		}
		return 0.0f;
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void madokuCraft$clearLoadedSaturation(ValueInput input, CallbackInfo ci) {
		if (!MadokuHungerManager.isEnabled()) {
			return;
		}
		saturationLevel = 0.0f;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
	private void madokuCraft$clearSavedSaturation(ValueOutput output, CallbackInfo ci) {
		if (!MadokuHungerManager.isEnabled()) {
			return;
		}
		saturationLevel = 0.0f;
	}

	@Inject(method = "setSaturation(F)V", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disableSaturationSet(float saturation, CallbackInfo ci) {
		if (!MadokuHungerManager.isEnabled()) {
			return;
		}
		saturationLevel = 0.0f;
		ci.cancel();
	}

	@Inject(method = "getSaturationLevel", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$hideSaturationLevel(CallbackInfoReturnable<Float> cir) {
		if (!MadokuHungerManager.isEnabled()) {
			return;
		}
		cir.setReturnValue(0.0f);
	}
}

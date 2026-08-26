package madoku.craft.mixin.attributes;

import madoku.craft.attributes.MadokuExperienceManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerExperienceCurveMixin {
	@Inject(method = "getXpNeededForNextLevel", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$useConfiguredExperienceCurve(CallbackInfoReturnable<Integer> callbackInfo) {
		if (MadokuExperienceManager.isEnabled()) {
			callbackInfo.setReturnValue(MadokuExperienceManager.getXpNeededForNextLevel((Player) (Object) this));
		}
	}

    @Inject(method = "getBaseExperienceReward", at = @At("HEAD"), cancellable = true)
    private void madokuCraft$preventExperienceDrops(ServerLevel level, CallbackInfoReturnable<Integer> callbackInfo) {
		if (MadokuExperienceManager.isEnabled()) {
			callbackInfo.setReturnValue(0);
		}
	}

	@Inject(method = "giveExperiencePoints", at = @At("TAIL"))
	private void madokuCraft$clampExperienceLevelAfterPoints(int experience, CallbackInfo callbackInfo) {
		MadokuExperienceManager.clampPlayerLevel((Player) (Object) this);
	}

	@Inject(method = "giveExperienceLevels", at = @At("TAIL"))
	private void madokuCraft$clampExperienceLevelAfterLevels(int levels, CallbackInfo callbackInfo) {
		MadokuExperienceManager.clampPlayerLevel((Player) (Object) this);
	}
}

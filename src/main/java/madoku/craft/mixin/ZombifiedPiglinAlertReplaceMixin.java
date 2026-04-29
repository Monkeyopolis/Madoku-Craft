package madoku.craft.mixin;

import madoku.craft.mob.system.MadokuMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.monster.zombie.ZombifiedPiglin")
public abstract class ZombifiedPiglinAlertReplaceMixin {
	@Inject(method = "maybeAlertOthers", at = @At("HEAD"), cancellable = true)
	private void madoku$replaceVanillaZombifiedPiglinAlertBroadcast(CallbackInfo ci) {
		if (MadokuMob.shouldReplaceVanillaZombifiedPiglinBroadcast()) {
			ci.cancel();
		}
	}
}

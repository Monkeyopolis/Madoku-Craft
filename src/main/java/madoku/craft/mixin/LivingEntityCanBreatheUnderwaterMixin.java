package madoku.craft.mixin;

import madoku.craft.attributes.oxygen.MadokuOxygenManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityCanBreatheUnderwaterMixin {
	@Inject(method = "canBreatheUnderwater", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$disableVanillaBreatheUnderwater(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player player && MadokuOxygenManager.shouldSuppressVanillaBreathingEffects(player)) {
			cir.setReturnValue(false);
		}
	}
}

package madoku.craft.mixin.attributes;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import madoku.craft.attributes.MadokuOxygenManager;

@Mixin(Entity.class)
public abstract class EntityAirSupplyMaximumMixin {
	@Inject(method = "getMaxAirSupply", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$useConfiguredPlayerAirMaximum(CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof Player player && MadokuOxygenManager.isEnabled()) {
			cir.setReturnValue(MadokuOxygenManager.getMaximumOxygenTicksForEntity(player));
		}
	}
}

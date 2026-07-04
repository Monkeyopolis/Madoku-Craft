package madoku.craft.mixin;

import madoku.craft.oxygen.MadokuOxygenManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityAirSupplyOverrideMixin {
	@Shadow
	protected abstract int increaseAirSupply(int airSupply);

	@Shadow
	protected abstract int decreaseAirSupply(int airSupply);

	@Redirect(
		method = "baseTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;increaseAirSupply(I)I")
	)
	private int madokuCraft$preventVanillaAirRefill(LivingEntity entity, int airSupply) {
		if (entity instanceof Player && MadokuOxygenManager.isEnabled()) {
			return airSupply;
		}
		return this.increaseAirSupply(airSupply);
	}

	@Redirect(
		method = "baseTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;decreaseAirSupply(I)I")
	)
	private int madokuCraft$preventVanillaAirDrain(LivingEntity entity, int airSupply) {
		if (entity instanceof Player && MadokuOxygenManager.isEnabled()) {
			return airSupply;
		}
		return this.decreaseAirSupply(airSupply);
	}
}

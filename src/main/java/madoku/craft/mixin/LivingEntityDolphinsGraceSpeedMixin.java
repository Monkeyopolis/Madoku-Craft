package madoku.craft.mixin;

import madoku.craft.attributes.oxygen.MadokuOxygenManager;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDolphinsGraceSpeedMixin {
	@ModifyArg(
		method = "travelInWater",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"),
		index = 0
	)
	private float madokuCraft$scaleDolphinsGraceSwimSpeed(float vanillaSpeed) {
		LivingEntity entity = (LivingEntity) (Object) this;
		if (!(entity instanceof Player)) {
			return vanillaSpeed;
		}

		double bonus = MadokuOxygenManager.resolveDolphinsGraceSwimmingSpeedBonus(entity);
		if (bonus <= 0.0d) {
			return vanillaSpeed;
		}
		return (float) (vanillaSpeed * (1.0d + bonus));
	}

	@Redirect(
		method = "travelInWater",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z")
	)
	private boolean madokuCraft$disableVanillaDolphinsGraceSpeed(LivingEntity entity, Holder<MobEffect> effect) {
		if (entity instanceof Player
			&& effect == MobEffects.DOLPHINS_GRACE
			&& MadokuOxygenManager.shouldOverrideVanillaEffect(entity, effect.value())) {
			return false;
		}
		return entity.hasEffect(effect);
	}
}

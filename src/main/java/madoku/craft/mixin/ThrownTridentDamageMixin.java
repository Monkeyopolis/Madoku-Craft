package madoku.craft.mixin;

import madoku.craft.mob.system.MadokuMobManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ThrownTrident.class)
public abstract class ThrownTridentDamageMixin {
	@Redirect(
		method = "onHitEntity",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
		)
	)
	@SuppressWarnings("deprecation")
	private boolean madokuCraft$applyFixedTridentDamage(Entity entity, DamageSource source, float originalDamage) {
		ThrownTrident trident = (ThrownTrident) (Object) this;
		float resolvedDamage = MadokuMobManager.resolveProjectileDamageOverride(trident, originalDamage);
		boolean hit = entity.hurtOrSimulate(source, resolvedDamage);
		if (hit) {
			MadokuMobManager.clearProjectileHoming(trident);
		}
		MadokuMobManager.clearInvulnerabilityBypass(trident);
		if (hit && entity instanceof LivingEntity livingEntity) {
			MadokuMobManager.applyWitherSkeletonArrowHitEffect(livingEntity, trident.getOwner());
		}
		return hit;
	}
}


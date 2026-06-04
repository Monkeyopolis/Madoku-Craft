package madoku.craft.mixin;

import madoku.craft.mob.system.MadokuMobManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowDamageMixin {
	@Shadow
	protected abstract void doKnockback(LivingEntity target, DamageSource source);

	@Redirect(
		method = "onHitEntity",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
		)
	)
	@SuppressWarnings("deprecation")
	private boolean madokuCraft$applyFixedArrowDamage(Entity entity, DamageSource source, float originalDamage) {
		AbstractArrow arrow = (AbstractArrow) (Object) this;
		if (MadokuMobManager.shouldBypassInvulnerability(arrow) && entity instanceof LivingEntity livingEntity) {
			livingEntity.invulnerableTime = 0;
			livingEntity.hurtTime = 0;
		}
		float resolvedDamage = MadokuMobManager.resolveProjectileDamageOverride(arrow, originalDamage);
		boolean hit = entity.hurtOrSimulate(source, resolvedDamage);
		if (hit && MadokuMobManager.isManagedHomingArrow(arrow)) {
			MadokuMobManager.clearProjectileHoming(arrow);
		}
		MadokuMobManager.clearInvulnerabilityBypass(arrow);
		if (hit && entity instanceof LivingEntity livingEntity) {
			MadokuMobManager.applyWitherSkeletonArrowHitEffect(livingEntity, arrow.getOwner());
		}
		return hit;
	}

	@Redirect(
		method = "onHitEntity",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;doKnockback(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)V"
		)
	)
	private void madokuCraft$skipHomingArrowKnockback(AbstractArrow arrow, LivingEntity target, DamageSource source) {
		if (!MadokuMobManager.isManagedHomingArrow(arrow)) {
			this.doKnockback(target, source);
		}
	}
}


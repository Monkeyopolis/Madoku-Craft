package madoku.craft.mixin;

import madoku.craft.mob.system.MadokuMob;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowDamageMixin {
	@Redirect(
		method = "onHitEntity",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
		)
	)
	private boolean madokuCraft$applyFixedArrowDamage(Entity entity, DamageSource source, float originalDamage) {
		AbstractArrow arrow = (AbstractArrow) (Object) this;
		float resolvedDamage = MadokuMob.resolveProjectileDamageOverride(arrow, originalDamage);
		boolean hit = entity.hurt(source, resolvedDamage);
		if (hit && entity instanceof LivingEntity livingEntity) {
			MadokuMob.applyWitherSkeletonArrowHitEffect(livingEntity, arrow.getOwner());
		}
		return hit;
	}
}

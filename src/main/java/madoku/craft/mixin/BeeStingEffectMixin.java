package madoku.craft.mixin;

import madoku.craft.mob.system.EntityBehaviorsManager;

import madoku.craft.mob.system.EntityBehaviorsManager.BeeBehavior;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.bee.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Bee.class)
public abstract class BeeStingEffectMixin {
	@Redirect(
		method = "doHurtTarget",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"
		)
	)
	private boolean madokuCraft$applyBeeStingEffect(
		LivingEntity target,
		MobEffectInstance effect,
		Entity attacker
	) {
		return EntityBehaviorsManager.BeeBehavior.applyStingingAttackEffect((Bee) (Object) this, target, effect, attacker);
	}
}

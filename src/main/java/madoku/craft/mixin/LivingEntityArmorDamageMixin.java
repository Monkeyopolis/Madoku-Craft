package madoku.craft.mixin;

import madoku.craft.armor.MadokuArmor;
import madoku.craft.mob.system.MadokuMobManager;
import madoku.craft.pet.PlayerEntitiesSystem;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityArmorDamageMixin {
	@Shadow
	protected abstract void hurtArmor(DamageSource source, float amount);

	@Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyMadokuArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
		LivingEntity entity = (LivingEntity) (Object) this;
		boolean skeletonIgnoresArmor = MadokuMobManager.shouldSkeletonMeleeIgnoreArmor(source);
		boolean mobIgnoresArmor = MadokuMobManager.shouldBypassArmorForMobDamage(source);
		boolean fallDamage = source != null && source.is(DamageTypeTags.IS_FALL);
		boolean bypassesArmor = source != null && source.is(DamageTypeTags.BYPASSES_ARMOR) && !fallDamage;
		boolean shouldHandlePetAbilities = entity instanceof net.minecraft.server.level.ServerPlayer;
		if (!MadokuArmor.isEnabled() && !fallDamage && !shouldHandlePetAbilities && !skeletonIgnoresArmor && !mobIgnoresArmor) {
			return;
		}

		if (MadokuArmor.isEnabled() && !skeletonIgnoresArmor && !mobIgnoresArmor && source != null && !bypassesArmor) {
			this.hurtArmor(source, amount);
		}

		float damageAfterArmor;
		if (MadokuArmor.isEnabled() && !skeletonIgnoresArmor && !mobIgnoresArmor && !bypassesArmor) {
			damageAfterArmor = MadokuArmor.applyCustomArmorDamage(entity, source, amount);
		} else {
			damageAfterArmor = amount;
		}

		damageAfterArmor = PlayerEntitiesSystem.applyFallDamageAbilityReduction(entity, source, damageAfterArmor);
		damageAfterArmor = PlayerEntitiesSystem.applyIncomingDamageBlockAbility(entity, source, damageAfterArmor);
		cir.setReturnValue(damageAfterArmor);
	}
}


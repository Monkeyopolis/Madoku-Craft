package madoku.craft.mixin.attributes;

import madoku.craft.attributes.MadokuArmorManager;
import madoku.craft.core.enchant.EnchantBooksManager;
import madoku.craft.mob.MobEntityManager;
import madoku.craft.pet.PetAbilitiesManager;
import net.minecraft.core.Holder;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityArmorDamageMixin {
	@Shadow
	protected abstract void hurtArmor(DamageSource source, float amount);

	@Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyMadokuArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
		LivingEntity entity = (LivingEntity) (Object) this;
		boolean skeletonIgnoresArmor = MobEntityManager.shouldSkeletonMeleeIgnoreArmor(source);
		boolean mobIgnoresArmor = MobEntityManager.shouldBypassArmorForMobDamage(source);
		boolean fallDamage = source != null && source.is(DamageTypeTags.IS_FALL);
		boolean bypassesArmor = source != null && source.is(DamageTypeTags.BYPASSES_ARMOR) && !fallDamage;
		boolean shouldHandlePetAbilities = entity instanceof net.minecraft.server.level.ServerPlayer;
		boolean shouldOverrideVanillaArmor = MadokuArmorManager.shouldOverrideVanillaArmorDamage(source);
		if (!shouldOverrideVanillaArmor && !fallDamage && !shouldHandlePetAbilities && !skeletonIgnoresArmor && !mobIgnoresArmor) {
			float damageAfterArmor = PetAbilitiesManager.applyDamageVulnerabilities(entity, amount);
			damageAfterArmor = EnchantBooksManager.applyConfiguredSmiteVulnerability(entity, source, damageAfterArmor);
			EnchantBooksManager.capturePostArmorDamage(damageAfterArmor);
			cir.setReturnValue(damageAfterArmor);
			return;
		}

		if (shouldOverrideVanillaArmor && !skeletonIgnoresArmor && !mobIgnoresArmor && source != null && !bypassesArmor) {
			this.hurtArmor(source, amount);
		}

		float damageAfterArmor;
		if (shouldOverrideVanillaArmor && !skeletonIgnoresArmor && !mobIgnoresArmor && !bypassesArmor) {
			damageAfterArmor = MadokuArmorManager.applyCustomArmorDamage(entity, source, amount);
		} else {
			damageAfterArmor = amount;
		}

		damageAfterArmor = PetAbilitiesManager.applyFallDamage(entity, source, damageAfterArmor);
		damageAfterArmor = PetAbilitiesManager.applyDamageBlock(entity, source, damageAfterArmor);
		damageAfterArmor = PetAbilitiesManager.applyDamageVulnerabilities(entity, damageAfterArmor);
		damageAfterArmor = EnchantBooksManager.applyConfiguredSmiteVulnerability(entity, source, damageAfterArmor);
		EnchantBooksManager.capturePostArmorDamage(damageAfterArmor);
		cir.setReturnValue(damageAfterArmor);
	}

	@Redirect(
		method = "getDamageAfterMagicAbsorb",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getDamageProtection(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)F"
		)
	)
	private float madokuCraft$resolveConfiguredDamageProtection(
		net.minecraft.server.level.ServerLevel serverLevel,
		LivingEntity entity,
		DamageSource source
	) {
		return EnchantBooksManager.resolveDamageProtection(serverLevel, entity, source);
	}

	@Redirect(
		method = "getDamageAfterMagicAbsorb",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterMagicAbsorb(FF)F"
		)
	)
	private float madokuCraft$applyConfiguredDamageReduction(float damage, float vanillaProtection) {
		return EnchantBooksManager.applyConfiguredDamageReduction(damage, vanillaProtection);
	}

	@Redirect(
		method = "getDamageAfterMagicAbsorb",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z")
	)
	private boolean madokuCraft$overrideVanillaResistanceCheck(LivingEntity instance, Holder<MobEffect> effect) {
		if (effect != null
			&& effect.value() == MobEffects.RESISTANCE.value()
			&& MadokuArmorManager.isResistanceEnabled()) {
			return false;
		}
		return instance.getEffect(effect) != null;
	}
}



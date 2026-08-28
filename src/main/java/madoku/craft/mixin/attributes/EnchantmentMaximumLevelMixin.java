package madoku.craft.mixin.attributes;

import madoku.craft.core.enchant.BooksConfigManager;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Enchantment.class)
public abstract class EnchantmentMaximumLevelMixin {
	@Inject(method = "getMaxLevel", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$useConfiguredMaximumLevel(CallbackInfoReturnable<Integer> callbackInfo) {
		callbackInfo.setReturnValue(
			BooksConfigManager.getConfiguredMaximumLevel(
				(Enchantment) (Object) this,
				callbackInfo.getReturnValue()
			)
		);
	}

	@Inject(method = "canEnchant", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$useConfiguredCompatibleItems(
		ItemStack stack,
		CallbackInfoReturnable<Boolean> callbackInfo
	) {
		callbackInfo.setReturnValue(
			BooksConfigManager.resolveConfiguredCanEnchant(
				(Enchantment) (Object) this,
				stack,
				callbackInfo.getReturnValue()
			)
		);
	}

	/** Prevents configured Bane of Arthropods from retaining vanilla bonus damage. */
	@Inject(
		method = "modifyDamage(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lorg/apache/commons/lang3/mutable/MutableFloat;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replaceBaneOfArthropodsDamage(
		ServerLevel serverLevel,
		int level,
		ItemStack stack,
		Entity entity,
		DamageSource source,
		MutableFloat damage,
		CallbackInfo callbackInfo
	) {
		if (BooksConfigManager.shouldOverrideBaneOfArthropods((Enchantment) (Object) this)) {
			callbackInfo.cancel();
		}
	}

	/** Prevents configured Bane of Arthropods from retaining vanilla target restrictions and slowness. */
	@Inject(
		method = "doPostAttack(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/enchantment/EnchantedItemInUse;Lnet/minecraft/world/item/enchantment/EnchantmentTarget;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replaceBaneOfArthropodsPostAttack(
		ServerLevel serverLevel,
		int level,
		EnchantedItemInUse itemSource,
		EnchantmentTarget target,
		Entity entity,
		DamageSource source,
		CallbackInfo callbackInfo
	) {
		if (BooksConfigManager.shouldOverrideBaneOfArthropods((Enchantment) (Object) this)) {
			callbackInfo.cancel();
		}
	}

	/** Replaces vanilla Breach's armor-effectiveness contribution in the vanilla armor path. */
	@Inject(
		method = "modifyArmorEffectivness(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lorg/apache/commons/lang3/mutable/MutableFloat;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replaceBreachArmorEffectiveness(
		ServerLevel serverLevel,
		int level,
		ItemStack stack,
		Entity entity,
		DamageSource source,
		MutableFloat armorEffectiveness,
		CallbackInfo callbackInfo
	) {
		double penetration = BooksConfigManager.getConfiguredBreachArmorPenetration(
			(Enchantment) (Object) this,
			stack,
			level
		);
		if (penetration < 0.0D) return;

		armorEffectiveness.add((float) (-penetration / 100.0D));
		callbackInfo.cancel();
	}

	@Inject(method = "areCompatible", at = @At("RETURN"), cancellable = true)
	private static void madokuCraft$useConfiguredConflictSettings(
		Holder<Enchantment> first,
		Holder<Enchantment> second,
		CallbackInfoReturnable<Boolean> callbackInfo
	) {
		callbackInfo.setReturnValue(
			BooksConfigManager.resolveConfiguredCompatibility(
				first,
				second,
				callbackInfo.getReturnValue()
			)
		);
	}
}

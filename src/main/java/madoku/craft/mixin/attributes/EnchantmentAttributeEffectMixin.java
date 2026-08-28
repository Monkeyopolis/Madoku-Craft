package madoku.craft.mixin.attributes;

import madoku.craft.core.enchant.EnchantBooksManager;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentAttributeEffect.class)
public abstract class EnchantmentAttributeEffectMixin {
	@Inject(method = "getModifier", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$applyConfiguredAttribute(
		int level,
		StringRepresentable slot,
		CallbackInfoReturnable<AttributeModifier> callbackInfo
	) {
		EnchantmentAttributeEffect effect = (EnchantmentAttributeEffect) (Object) this;
		String effectId = effect.id().toString();
		if ("minecraft:enchantment.aqua_affinity".equals(effectId)) {
			callbackInfo.setReturnValue(
				EnchantBooksManager.applyConfiguredAquaAffinityModifier(level, callbackInfo.getReturnValue())
			);
		} else if ("minecraft:enchantment.depth_strider".equals(effectId)) {
			callbackInfo.setReturnValue(
				EnchantBooksManager.applyConfiguredDepthStriderModifier(level, callbackInfo.getReturnValue())
			);
		} else if ("minecraft:enchantment.efficiency".equals(effectId)) {
			callbackInfo.setReturnValue(
				EnchantBooksManager.applyConfiguredEfficiencyModifier(level, callbackInfo.getReturnValue())
			);
		} else if ("minecraft:enchantment.fire_protection".equals(effectId)) {
			callbackInfo.setReturnValue(
				EnchantBooksManager.applyConfiguredFireProtectionModifier(
					level,
					slot == null ? "null" : slot.toString(),
					callbackInfo.getReturnValue()
				)
			);
		}
	}
}

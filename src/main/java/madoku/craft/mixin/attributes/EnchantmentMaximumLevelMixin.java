package madoku.craft.mixin.attributes;

import madoku.craft.core.enchant.EnchantBooksManager;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
public abstract class EnchantmentMaximumLevelMixin {
	@Inject(method = "getMaxLevel", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$useConfiguredMaximumLevel(CallbackInfoReturnable<Integer> callbackInfo) {
		callbackInfo.setReturnValue(
			EnchantBooksManager.getConfiguredMaximumLevel(
				(Enchantment) (Object) this,
				callbackInfo.getReturnValue()
			)
		);
	}
}

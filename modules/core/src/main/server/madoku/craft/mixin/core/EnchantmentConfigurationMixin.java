package madoku.craft.mixin.core;

import madoku.craft.java.core.enchant.BooksConfigAPIManager;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies Madoku Enchant configuration to vanilla enchantment rules. */
@Mixin(Enchantment.class)
public abstract class EnchantmentConfigurationMixin {
	@Inject(method = "getMaxLevel", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$useConfiguredMaximumLevel(CallbackInfoReturnable<Integer> callbackInfo) {
		callbackInfo.setReturnValue(
			BooksConfigAPIManager.getConfiguredMaximumLevel(
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
			BooksConfigAPIManager.resolveConfiguredCanEnchant(
				(Enchantment) (Object) this,
				stack,
				callbackInfo.getReturnValue()
			)
		);
	}

	@Inject(method = "areCompatible", at = @At("RETURN"), cancellable = true)
	private static void madokuCraft$useConfiguredConflictSettings(
		Holder<Enchantment> first,
		Holder<Enchantment> second,
		CallbackInfoReturnable<Boolean> callbackInfo
	) {
		callbackInfo.setReturnValue(
			BooksConfigAPIManager.resolveConfiguredCompatibility(
				first,
				second,
				callbackInfo.getReturnValue()
			)
		);
	}
}

package madoku.craft.mixin.core;

import madoku.craft.java.core.enchant.EnchantBooksAPIManager;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ApplyBonusCount.class)
public abstract class ApplyBonusCountFortuneMixin {
	@Shadow @Final private Holder<Enchantment> enchantment;

	@Inject(method = "run", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$applyConfiguredFortune(
		ItemStack stack,
		LootContext lootContext,
		CallbackInfoReturnable<ItemStack> callbackInfo
	) {
		if (EnchantBooksAPIManager.applyConfiguredFortune(enchantment, stack, lootContext)
			|| EnchantBooksAPIManager.applyConfiguredLooting(enchantment, stack, lootContext)) {
			callbackInfo.setReturnValue(stack);
		}
	}
}


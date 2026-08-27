package madoku.craft.mixin.core;

import madoku.craft.core.enchant.EnchantBooksManager;
import madoku.craft.core.rarity.MadokuRarityManager;
import madoku.craft.core.recipes.MadokuRecipesManager;
import madoku.craft.items.MadokuItemsManager;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SmithingTransformRecipe.class, AnvilMenu.class})
public class SmithingTransformRecipeRarityMixin {
	@Inject(method = "assemble", at = @At("RETURN"), cancellable = true, require = 0)
	private void madokuCraft$transferRarity(
		SmithingRecipeInput input,
		CallbackInfoReturnable<ItemStack> cir
	) {
		ItemStack upgradedResult = MadokuRecipesManager.createSmithingUpgradeResult(input.base(), cir.getReturnValue());
		cir.setReturnValue(upgradedResult);
	}

	@Inject(method = "createResult()V", at = @At("TAIL"), require = 0)
	private void madokuCraft$preserveRarityAfterRename(CallbackInfo ci) {
		if (!((Object) this instanceof AnvilMenu menu)) {
			return;
		}

		ItemStack source = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
		ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
		EnchantBooksManager.removeIncompatibleConfiguredEnchantments(source, result);
		MadokuRarityManager.preserveRarityOnRename(source, result);
		MadokuItemsManager.updateDurabilityLore(result);
	}
}

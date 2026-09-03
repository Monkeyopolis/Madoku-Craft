package madoku.craft.mixin.core;

import madoku.craft.java.core.enchant.EnchantBooksAPIManager;
import madoku.craft.java.core.rarity.RarityAPIManager;
import madoku.craft.java.core.recipes.RecipesAPIManager;
import madoku.craft.java.items.ItemsAPIManager;
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
		ItemStack upgradedResult = RecipesAPIManager.createSmithingUpgradeResult(input.base(), cir.getReturnValue());
		cir.setReturnValue(upgradedResult);
	}

	@Inject(method = "createResult()V", at = @At("TAIL"), require = 0)
	private void madokuCraft$preserveRarityAfterRename(CallbackInfo ci) {
		if (!((Object) this instanceof AnvilMenu menu)) {
			return;
		}

		ItemStack source = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
		ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
		EnchantBooksAPIManager.removeIncompatibleConfiguredEnchantments(source, result);
		RarityAPIManager.preserveRarityOnRename(source, result);
		ItemsAPIManager.updateDurabilityLore(result);
	}
}


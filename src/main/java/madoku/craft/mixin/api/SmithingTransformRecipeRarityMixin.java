package madoku.craft.mixin.api;

import madoku.craft.api.rarity.MadokuRarityManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SmithingTransformRecipe.class)
public class SmithingTransformRecipeRarityMixin {
	@Inject(method = "assemble", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$transferRarity(
		SmithingRecipeInput input,
		CallbackInfoReturnable<ItemStack> cir
	) {
		ItemStack upgradedResult = MadokuRarityManager.createSmithingUpgradeResult(input.base(), cir.getReturnValue());
		cir.setReturnValue(upgradedResult);
	}
}

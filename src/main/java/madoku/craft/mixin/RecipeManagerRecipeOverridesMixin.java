package madoku.craft.mixin;

import madoku.craft.api.recipes.MadokuRecipesManager;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerRecipeOverridesMixin {
	@Shadow
	@Mutable
	private RecipeMap recipes;

	@Unique
	private boolean madokuCraft$rebuildingRecipeCaches;

	@Shadow
	public abstract void finalizeRecipeLoading(FeatureFlagSet featureFlags);

	@Inject(method = "finalizeRecipeLoading", at = @At("TAIL"))
	private void madoku$applyRecipeConfig(FeatureFlagSet featureFlags, CallbackInfo ci) {
		if (this.madokuCraft$rebuildingRecipeCaches) {
			return;
		}
		if (this.recipes == null) {
			return;
		}

		List<RecipeHolder<?>> resolvedRecipes = MadokuRecipesManager.applyRecipeOverrides(this.recipes.values());
		this.recipes = RecipeMap.create(resolvedRecipes);
		this.madokuCraft$rebuildingRecipeCaches = true;
		try {
			this.finalizeRecipeLoading(featureFlags);
		} finally {
			this.madokuCraft$rebuildingRecipeCaches = false;
		}
	}
}


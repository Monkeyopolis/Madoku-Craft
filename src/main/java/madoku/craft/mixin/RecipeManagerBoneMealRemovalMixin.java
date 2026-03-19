package madoku.craft.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerBoneMealRemovalMixin {
	private static final String BONE_MEAL_RECIPE_ID = "minecraft:bone_meal";

	@Shadow
	@Mutable
	private RecipeMap recipes;

	@Inject(method = "finalizeRecipeLoading", at = @At("TAIL"))
	private void madoku$removeBoneMealRecipe(FeatureFlagSet featureFlags, CallbackInfo ci) {
		if (this.recipes == null) {
			return;
		}

		List<RecipeHolder<?>> filteredRecipes = new ArrayList<>();
		boolean changed = false;
		for (RecipeHolder<?> holder : this.recipes.values()) {
			if (holder != null && isBoneMealRecipe(holder.id().identifier())) {
				changed = true;
				continue;
			}
			filteredRecipes.add(holder);
		}

		if (!changed) {
			return;
		}

		this.recipes = RecipeMap.create(filteredRecipes);
	}

	private static boolean isBoneMealRecipe(Identifier identifier) {
		if (identifier == null) {
			return false;
		}
		return identifier != null && BONE_MEAL_RECIPE_ID.equals(identifier.toString());
	}
}

package madoku.craft.java.core.recipes;

import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;

import java.util.Optional;

public final class FormatSmithingManager {
	private static volatile boolean initialized;
	private FormatSmithingManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }
	public static String category() { return RecipesConfigManager.CATEGORY_SMITHING; }

	static Recipe<?> buildOverride(JsonObject root, SmithingRecipe recipe, String resultItemId, int resultCount) {
		Ingredient base = RecipesFormatManager.readIngredient(root.get(RecipesConfigManager.FIELD_BASE));
		if (base == null || base.isEmpty()) base = recipe.baseIngredient();
		if (base == null || base.isEmpty()) return null;
		Optional<Ingredient> template = RecipesFormatManager.readOptionalIngredient(root.get(RecipesConfigManager.FIELD_TEMPLATE));
		if (template.isEmpty()) template = recipe.templateIngredient();
		Optional<Ingredient> addition = RecipesFormatManager.readOptionalIngredient(root.get(RecipesConfigManager.FIELD_ADDITION));
		if (addition.isEmpty()) addition = recipe.additionIngredient();
		Recipe.CommonInfo commonInfo = new Recipe.CommonInfo(recipe.showNotification());
		if (recipe instanceof SmithingTransformRecipe) {
			ItemStackTemplate result = RecipesFormatManager.buildResultTemplate(root, resultItemId, resultCount);
			if (result == null) return null;
			return new SmithingTransformRecipe(commonInfo, template, base, addition, result);
		}
		if (recipe instanceof SmithingTrimRecipe trim) {
			if (template.isEmpty() || addition.isEmpty()) return null;
			Object rawPattern = RecipesFormatManager.readSmithingTrimPattern(trim);
			if (!(rawPattern instanceof net.minecraft.core.Holder<?> holder)) return null;
			@SuppressWarnings("unchecked")
			net.minecraft.core.Holder<net.minecraft.world.item.equipment.trim.TrimPattern> pattern =
				(net.minecraft.core.Holder<net.minecraft.world.item.equipment.trim.TrimPattern>) holder;
			return new SmithingTrimRecipe(commonInfo, template.get(), base, addition.get(), pattern);
		}
		return null;
	}

	static void writeDefaults(JSONFormatAPIManager.ObjectBuilder root, SmithingRecipe recipe) {
		recipe.templateIngredient().ifPresent(ingredient -> {
			String itemId = RecipesFormatManager.firstItemIdFromIngredient(ingredient);
			if (!itemId.isBlank()) root.put(RecipesConfigManager.FIELD_TEMPLATE, itemId);
		});
		String baseItemId = RecipesFormatManager.firstItemIdFromIngredient(recipe.baseIngredient());
		if (!baseItemId.isBlank()) root.put(RecipesConfigManager.FIELD_BASE, baseItemId);
		recipe.additionIngredient().ifPresent(ingredient -> {
			String itemId = RecipesFormatManager.firstItemIdFromIngredient(ingredient);
			if (!itemId.isBlank()) root.put(RecipesConfigManager.FIELD_ADDITION, itemId);
		});
	}
}


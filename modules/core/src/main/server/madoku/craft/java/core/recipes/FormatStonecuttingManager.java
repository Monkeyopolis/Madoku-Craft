package madoku.craft.java.core.recipes;

import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

public final class FormatStonecuttingManager {
	private static volatile boolean initialized;
	private FormatStonecuttingManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }
	public static String category() { return RecipesConfigManager.CATEGORY_STONECUTTING; }

	static Recipe<?> buildOverride(JsonObject root, StonecutterRecipe recipe, String resultItemId, int resultCount) {
		Ingredient input = RecipesFormatManager.readIngredient(root.get(RecipesConfigManager.FIELD_INPUT));
		if (input == null || input.isEmpty()) input = recipe.input();
		ItemStackTemplate result = RecipesFormatManager.buildResultTemplate(root, resultItemId, resultCount);
		if (input == null || input.isEmpty() || result == null) return null;
		return new StonecutterRecipe(new Recipe.CommonInfo(recipe.showNotification()), input, result);
	}

	static void writeDefaults(JSONFormatAPIManager.ObjectBuilder root, StonecutterRecipe recipe) {
		RecipesFormatManager.writeSingleInputDefaults(root, recipe);
	}
}


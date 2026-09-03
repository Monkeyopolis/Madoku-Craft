package madoku.craft.java.core.recipes;

import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;

public final class FormatSmokingManager {
	private static volatile boolean initialized;
	private FormatSmokingManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }
	public static String category() { return RecipesConfigManager.CATEGORY_SMOKING; }

	static Recipe<?> buildOverride(JsonObject root, SmokingRecipe recipe, String resultItemId, int resultCount) {
		if (!(recipe instanceof SingleItemRecipe single)) return null;
		Ingredient input = RecipesFormatManager.readIngredient(root.get(RecipesConfigManager.FIELD_INPUT));
		if (input == null || input.isEmpty()) input = single.input();
		ItemStackTemplate result = RecipesFormatManager.buildResultTemplate(root, resultItemId, resultCount);
		if (input == null || input.isEmpty() || result == null) return null;
		return new SmokingRecipe(new Recipe.CommonInfo(recipe.showNotification()),
			new net.minecraft.world.item.crafting.AbstractCookingRecipe.CookingBookInfo(recipe.category(), RecipesFormatManager.readRecipeGroup(root, recipe.group())),
			input, result, recipe.experience(), recipe.cookingTime());
	}

	static void writeDefaults(JSONFormatAPIManager.ObjectBuilder root, SmokingRecipe recipe) {
		RecipesFormatManager.writeSingleInputDefaults(root, recipe);
	}
}


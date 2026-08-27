package madoku.craft.core.recipes;

import com.google.gson.JsonObject;

import madoku.craft.core.json.JSONFormatManager;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;

public final class FormatSmeltingManager {
	private static volatile boolean initialized;
	private FormatSmeltingManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }
	public static String category() { return RecipesConfigManager.CATEGORY_SMELTING; }

	static Recipe<?> buildOverride(JsonObject root, SmeltingRecipe recipe, String resultItemId, int resultCount) {
		if (!(recipe instanceof SingleItemRecipe single)) return null;
		Ingredient input = RecipesFormatManager.readIngredient(root.get(RecipesConfigManager.FIELD_INPUT));
		if (input == null || input.isEmpty()) input = single.input();
		ItemStackTemplate result = RecipesFormatManager.buildResultTemplate(root, resultItemId, resultCount);
		if (input == null || input.isEmpty() || result == null) return null;
		return new SmeltingRecipe(new Recipe.CommonInfo(recipe.showNotification()),
			new net.minecraft.world.item.crafting.AbstractCookingRecipe.CookingBookInfo(recipe.category(), RecipesFormatManager.readRecipeGroup(root, recipe.group())),
			input, result, recipe.experience(), recipe.cookingTime());
	}

	static void writeDefaults(JSONFormatManager.ObjectBuilder root, SmeltingRecipe recipe) {
		RecipesFormatManager.writeSingleInputDefaults(root, recipe);
	}
}

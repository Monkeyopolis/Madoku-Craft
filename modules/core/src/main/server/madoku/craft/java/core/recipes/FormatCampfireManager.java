package madoku.craft.java.core.recipes;

import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;

public final class FormatCampfireManager {
	private static volatile boolean initialized;
	private FormatCampfireManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }
	public static String category() { return RecipesConfigManager.CATEGORY_CAMPFIRE; }

	static Recipe<?> buildOverride(JsonObject root, CampfireCookingRecipe recipe, String resultItemId, int resultCount) {
		if (!(recipe instanceof SingleItemRecipe single)) return null;
		Ingredient input = RecipesFormatManager.readIngredient(root.get(RecipesConfigManager.FIELD_INPUT));
		if (input == null || input.isEmpty()) input = single.input();
		ItemStackTemplate result = RecipesFormatManager.buildResultTemplate(root, resultItemId, resultCount);
		if (input == null || input.isEmpty() || result == null) return null;
		return new CampfireCookingRecipe(new Recipe.CommonInfo(recipe.showNotification()),
			new net.minecraft.world.item.crafting.AbstractCookingRecipe.CookingBookInfo(recipe.category(), RecipesFormatManager.readRecipeGroup(root, recipe.group())),
			input, result, recipe.experience(), recipe.cookingTime());
	}

	static void writeDefaults(JSONFormatAPIManager.ObjectBuilder root, CampfireCookingRecipe recipe) {
		RecipesFormatManager.writeSingleInputDefaults(root, recipe);
	}
}


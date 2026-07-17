package madoku.craft.api.recipes;

import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;

public final class FormatBlastingManager {
	private static volatile boolean initialized;
	private FormatBlastingManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }
	public static String category() { return RecipesConfigManager.CATEGORY_BLASTING; }

	static Recipe<?> buildOverride(JsonObject root, BlastingRecipe recipe, String resultItemId, int resultCount) {
		if (!(recipe instanceof SingleItemRecipe single)) return null;
		Ingredient input = RecipesFormatManager.readIngredient(root.get(RecipesConfigManager.FIELD_INPUT));
		if (input == null || input.isEmpty()) input = single.input();
		ItemStack result = RecipesFormatManager.buildResultTemplate(root, resultItemId, resultCount);
		if (input == null || input.isEmpty() || result == null) return null;
		return new BlastingRecipe(recipe.group(), recipe.category(), input, result, recipe.experience(), recipe.cookingTime());
	}

	static void writeDefaults(JSONFormatManager.ObjectBuilder root, BlastingRecipe recipe) {
		RecipesFormatManager.writeSingleInputDefaults(root, recipe);
	}
}

package madoku.craft.api.recipes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;

public final class FormatCraftingManager {
	private static volatile boolean initialized;
	private FormatCraftingManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }
	public static String category() { return RecipesConfigManager.CATEGORY_CRAFTING; }

	static Recipe<?> buildOverride(JsonObject root, CraftingRecipe recipe, String resultItemId, int resultCount) {
		String shape = readString(root, RecipesConfigManager.FIELD_CRAFTING_SHAPE, defaultCraftingShape(recipe))
			.trim().toLowerCase(Locale.ROOT);
		return RecipesConfigManager.CRAFTING_SHAPE_SHAPELESS.equals(shape)
			? buildShapelessOverride(root, recipe, resultItemId, resultCount)
			: buildShapedOverride(root, recipe, resultItemId, resultCount);
	}

	static void writeDefaults(JSONFormatManager.ObjectBuilder root, CraftingRecipe recipe) {
		if (recipe instanceof ShapedRecipe shaped) {
			root.put(RecipesConfigManager.FIELD_CRAFTING_SHAPE, RecipesConfigManager.CRAFTING_SHAPE_SHAPED);
			writeShapedDefaults(root, shaped);
		} else if (recipe instanceof ShapelessRecipe shapeless) {
			root.put(RecipesConfigManager.FIELD_CRAFTING_SHAPE, RecipesConfigManager.CRAFTING_SHAPE_SHAPELESS);
			writeCraftingIngredientsDefaults(root, RecipesFormatManager.readShapelessIngredients(shapeless));
		}
	}

	private static Recipe<?> buildShapedOverride(JsonObject root, CraftingRecipe recipe, String resultItemId, int resultCount) {
		List<String> rows = RecipesFormatManager.readPatternRows(root.get(RecipesConfigManager.FIELD_PATTERN));
		if (rows.isEmpty()) return null;
		JsonObject keyRoot = RecipesFormatManager.readObject(root, RecipesConfigManager.FIELD_KEY);
		Map<Character, Ingredient> key = RecipesFormatManager.readShapedKey(rows, keyRoot);
		if (key == null) return null;
		List<String> normalizedRows = RecipesFormatManager.normalizePatternRows(rows, keyRoot);
		if (normalizedRows.isEmpty()) return null;
		ShapedRecipePattern pattern;
		try { pattern = ShapedRecipePattern.of(key, normalizedRows); }
		catch (RuntimeException exception) { return null; }
		ItemStack result = RecipesFormatManager.buildResultTemplate(root, resultItemId, resultCount);
		if (result == null) return null;
		return new ShapedRecipe(recipe.group(), recipe.category(), pattern, result, recipe.showNotification());
	}

	private static Recipe<?> buildShapelessOverride(JsonObject root, CraftingRecipe recipe, String resultItemId, int resultCount) {
		List<Ingredient> ingredients = RecipesFormatManager.readIngredientList(root.get(RecipesConfigManager.FIELD_INGREDIENTS));
		if (ingredients.isEmpty()) ingredients = RecipesFormatManager.readDefaultCraftingIngredients(recipe);
		if (ingredients.isEmpty()) return null;
		ItemStack result = RecipesFormatManager.buildResultTemplate(root, resultItemId, resultCount);
		if (result == null) return null;
		return new ShapelessRecipe(recipe.group(), recipe.category(), result, List.copyOf(ingredients));
	}

	private static void writeShapedDefaults(JSONFormatManager.ObjectBuilder root, ShapedRecipe recipe) {
		List<Optional<Ingredient>> ingredients = recipe.getIngredients();
		int width = Math.max(1, recipe.getWidth());
		int height = Math.max(1, recipe.getHeight());
		Map<String, Character> symbolBySignature = new java.util.LinkedHashMap<>();
		Map<Character, String> firstItemIdBySymbol = new java.util.LinkedHashMap<>();
		JSONFormatManager.ArrayBuilder pattern = JSONFormatManager.array();
		int symbolIndex = 0;
		for (int row = 0; row < height; row++) {
			StringBuilder rowBuilder = new StringBuilder(width);
			for (int column = 0; column < width; column++) {
				int index = row * width + column;
				Optional<Ingredient> optional = index < ingredients.size() ? ingredients.get(index) : Optional.empty();
				if (optional == null || optional.isEmpty()) { rowBuilder.append('-'); continue; }
				String itemId = RecipesFormatManager.firstItemIdFromIngredient(optional.get());
				if (itemId.isBlank()) { rowBuilder.append('-'); continue; }
				Character symbol = symbolBySignature.get(itemId);
				if (symbol == null) {
					symbol = symbolForIndex(symbolIndex++);
					symbolBySignature.put(itemId, symbol);
					firstItemIdBySymbol.put(symbol, itemId);
				}
				rowBuilder.append(symbol);
			}
			pattern.add(rowBuilder.toString());
		}
		JSONFormatManager.ObjectBuilder key = JSONFormatManager.object();
		for (Map.Entry<Character, String> entry : firstItemIdBySymbol.entrySet()) key.put(String.valueOf(entry.getKey()), entry.getValue());
		root.put(RecipesConfigManager.FIELD_PATTERN, pattern.build());
		root.put(RecipesConfigManager.FIELD_KEY, key.build());
		writeCraftingIngredientsDefaults(root, RecipesFormatManager.readShapedIngredients(recipe));
	}

	private static void writeCraftingIngredientsDefaults(JSONFormatManager.ObjectBuilder root, List<Ingredient> ingredientList) {
		if (ingredientList == null || ingredientList.isEmpty()) return;
		JSONFormatManager.ArrayBuilder ingredients = JSONFormatManager.array();
		for (Ingredient ingredient : ingredientList) {
			String itemId = RecipesFormatManager.firstItemIdFromIngredient(ingredient);
			if (!itemId.isBlank()) ingredients.add(itemId);
		}
		JsonArray built = ingredients.build();
		if (!built.isEmpty()) root.put(RecipesConfigManager.FIELD_INGREDIENTS, built);
	}

	private static String defaultCraftingShape(CraftingRecipe recipe) {
		return recipe instanceof ShapelessRecipe ? RecipesConfigManager.CRAFTING_SHAPE_SHAPELESS : RecipesConfigManager.CRAFTING_SHAPE_SHAPED;
	}

	private static String readString(JsonObject root, String key, String fallback) {
		if (root == null || root.get(key) == null || !root.get(key).isJsonPrimitive() || !root.get(key).getAsJsonPrimitive().isString()) return fallback;
		return root.get(key).getAsString();
	}

	private static char symbolForIndex(int index) {
		String symbols = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
		return symbols.charAt(Math.max(0, index) % symbols.length());
	}
}

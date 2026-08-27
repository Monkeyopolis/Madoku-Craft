package madoku.craft.core.recipes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.json.MadokuJSONManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Runtime group that selects the JSON format for a recipe process category. */
public final class RecipesFormatManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(RecipesFormatManager.class);
	private static final String EMPTY_SLOT_SYMBOL = "-";
	private static final String SHAPELESS_INGREDIENTS_FIELD_NAME = "ingredients";
	private static final String SMITHING_TRIM_PATTERN_FIELD_NAME = "pattern";
	private static volatile boolean initialized;

	private RecipesFormatManager() { }

	static void initialize() {
		FormatSmithingManager.initialize();
		FormatCraftingManager.initialize();
		FormatSmeltingManager.initialize();
		FormatBlastingManager.initialize();
		FormatSmokingManager.initialize();
		FormatCampfireManager.initialize();
		FormatStonecuttingManager.initialize();
		initialized = true;
	}

	static void reset() {
		FormatSmithingManager.reset();
		FormatCraftingManager.reset();
		FormatSmeltingManager.reset();
		FormatBlastingManager.reset();
		FormatSmokingManager.reset();
		FormatCampfireManager.reset();
		FormatStonecuttingManager.reset();
		initialized = false;
	}

	public static boolean isInitialized() { return initialized; }

	public static String normalizeCategory(String category) {
		if (category == null || category.isBlank()) return RecipesConfigManager.CATEGORY_OTHER;
		return switch (category.trim().toLowerCase()) {
			case "smithing" -> FormatSmithingManager.category();
			case "crafting" -> FormatCraftingManager.category();
			case "smelting" -> FormatSmeltingManager.category();
			case "blasting" -> FormatBlastingManager.category();
			case "smoking" -> FormatSmokingManager.category();
			case "campfire" -> FormatCampfireManager.category();
			case "stonecutting" -> FormatStonecuttingManager.category();
			default -> RecipesConfigManager.CATEGORY_OTHER;
		};
	}

	static Recipe<?> buildOverride(JsonObject root, Recipe<?> recipe, String resultItemId, int resultCount) {
		if (recipe instanceof CraftingRecipe crafting) return FormatCraftingManager.buildOverride(root, crafting, resultItemId, resultCount);
		if (recipe instanceof StonecutterRecipe stonecutter) return FormatStonecuttingManager.buildOverride(root, stonecutter, resultItemId, resultCount);
		if (recipe instanceof SmithingRecipe smithing) return FormatSmithingManager.buildOverride(root, smithing, resultItemId, resultCount);
		if (recipe instanceof SmeltingRecipe smelting) return FormatSmeltingManager.buildOverride(root, smelting, resultItemId, resultCount);
		if (recipe instanceof BlastingRecipe blasting) return FormatBlastingManager.buildOverride(root, blasting, resultItemId, resultCount);
		if (recipe instanceof SmokingRecipe smoking) return FormatSmokingManager.buildOverride(root, smoking, resultItemId, resultCount);
		if (recipe instanceof CampfireCookingRecipe campfire) return FormatCampfireManager.buildOverride(root, campfire, resultItemId, resultCount);
		return null;
	}

	static void writeDefaults(JSONFormatManager.ObjectBuilder root, Recipe<?> recipe) {
		if (recipe instanceof CraftingRecipe crafting) {
			FormatCraftingManager.writeDefaults(root, crafting);
		} else if (recipe instanceof SmithingRecipe smithing) {
			FormatSmithingManager.writeDefaults(root, smithing);
		} else if (recipe instanceof SmeltingRecipe smelting) {
			FormatSmeltingManager.writeDefaults(root, smelting);
		} else if (recipe instanceof BlastingRecipe blasting) {
			FormatBlastingManager.writeDefaults(root, blasting);
		} else if (recipe instanceof SmokingRecipe smoking) {
			FormatSmokingManager.writeDefaults(root, smoking);
		} else if (recipe instanceof CampfireCookingRecipe campfire) {
			FormatCampfireManager.writeDefaults(root, campfire);
		} else if (recipe instanceof StonecutterRecipe stonecutter) {
			FormatStonecuttingManager.writeDefaults(root, stonecutter);
		}
	}

	static ItemStackTemplate buildResultTemplate(JsonObject root, String defaultItemId, int defaultCount) {
		String resultItemId = readString(root, RecipesConfigManager.FIELD_RESULT_ITEM_ID, defaultItemId);
		Item item = resolveItem(resultItemId);
		if (item == null) return null;
		int count = Math.max(1, readInt(root, RecipesConfigManager.FIELD_RESULT_COUNT, defaultCount));
		return new ItemStackTemplate(item, count);
	}

	static Ingredient readIngredient(JsonElement element) {
		List<Item> items = new ArrayList<>();
		collectIngredientItems(element, items);
		return items.isEmpty() ? null : Ingredient.of(items.toArray(Item[]::new));
	}

	static List<Ingredient> readIngredientList(JsonElement element) {
		List<Ingredient> ingredients = new ArrayList<>();
		if (element == null || element.isJsonNull()) return ingredients;
		if (element instanceof JsonArray array) {
			for (JsonElement entry : array) {
				Ingredient ingredient = readIngredient(entry);
				if (ingredient != null && !ingredient.isEmpty()) ingredients.add(ingredient);
			}
			return ingredients;
		}
		Ingredient single = readIngredient(element);
		if (single != null && !single.isEmpty()) ingredients.add(single);
		return ingredients;
	}

	static Optional<Ingredient> readOptionalIngredient(JsonElement element) {
		Ingredient ingredient = readIngredient(element);
		return ingredient == null || ingredient.isEmpty() ? Optional.empty() : Optional.of(ingredient);
	}

	static List<String> readPatternRows(JsonElement element) {
		if (!(element instanceof JsonArray array) || array.isEmpty()) return List.of();
		List<String> rows = new ArrayList<>(array.size());
		for (JsonElement row : array) {
			if (!(row instanceof JsonPrimitive primitive) || !primitive.isString()) return List.of();
			rows.add(primitive.getAsString());
		}
		return rows;
	}

	static JsonObject readObject(JsonObject root, String key) {
		return root != null && root.get(key) instanceof JsonObject object ? object : new JsonObject();
	}

	static Map<Character, Ingredient> readShapedKey(List<String> patternRows, JsonObject keyRoot) {
		Map<Character, Ingredient> key = new java.util.LinkedHashMap<>();
		for (char symbol : collectRequiredPatternKeys(patternRows)) {
			Ingredient ingredient = readIngredient(keyRoot.get(String.valueOf(symbol)));
			if (ingredient == null || ingredient.isEmpty()) return null;
			key.put(symbol, ingredient);
		}
		return key;
	}

	static List<String> normalizePatternRows(List<String> patternRows, JsonObject keyRoot) {
		if (patternRows.isEmpty()) return List.of();
		List<String> normalized = new ArrayList<>(patternRows.size());
		for (String row : patternRows) {
			if (row == null || row.isEmpty()) {
				normalized.add(" ");
				continue;
			}
			StringBuilder builder = new StringBuilder(row.length());
			for (int index = 0; index < row.length(); index++) {
				char symbol = row.charAt(index);
				if (isEmptyPatternSymbol(symbol) || !keyRoot.has(String.valueOf(symbol))) builder.append(' ');
				else builder.append(symbol);
			}
			normalized.add(builder.toString());
		}
		return normalized;
	}

	static List<Ingredient> readDefaultCraftingIngredients(CraftingRecipe recipe) {
		if (recipe instanceof ShapelessRecipe shapeless) return readShapelessIngredients(shapeless);
		if (recipe instanceof ShapedRecipe shaped) return readShapedIngredients(shaped);
		return List.of();
	}

	static List<Ingredient> readShapelessIngredients(ShapelessRecipe recipe) {
		try {
			Field field = ShapelessRecipe.class.getDeclaredField(SHAPELESS_INGREDIENTS_FIELD_NAME);
			field.setAccessible(true);
			Object raw = field.get(recipe);
			if (!(raw instanceof List<?> list) || list.isEmpty()) return List.of();
			List<Ingredient> resolved = new ArrayList<>();
			for (Object value : list) if (value instanceof Ingredient ingredient && !ingredient.isEmpty()) resolved.add(ingredient);
			return List.copyOf(resolved);
		} catch (ReflectiveOperationException exception) {
			LOGGER.warn("Failed to read shapeless recipe ingredients for recipe overrides.", exception);
			return List.of();
		}
	}

	static List<Ingredient> readShapedIngredients(ShapedRecipe recipe) {
		List<Ingredient> resolved = new ArrayList<>();
		for (Optional<Ingredient> optional : recipe.getIngredients()) {
			if (optional != null && optional.isPresent() && !optional.get().isEmpty()) resolved.add(optional.get());
		}
		return List.copyOf(resolved);
	}

	static Object readSmithingTrimPattern(SmithingTrimRecipe recipe) {
		try {
			Field field = SmithingTrimRecipe.class.getDeclaredField(SMITHING_TRIM_PATTERN_FIELD_NAME);
			field.setAccessible(true);
			return field.get(recipe);
		} catch (ReflectiveOperationException exception) {
			LOGGER.warn("Failed to read smithing trim pattern for recipe overrides.", exception);
			return null;
		}
	}

	static void writeSingleInputDefaults(JSONFormatManager.ObjectBuilder root, SingleItemRecipe recipe) {
		String itemId = firstItemIdFromIngredient(recipe.input());
		if (!itemId.isBlank()) root.put(RecipesConfigManager.FIELD_INPUT, itemId);
	}

	static String firstItemIdFromIngredient(Ingredient ingredient) {
		Item item = firstItemFromIngredient(ingredient);
		if (item == null) return "";
		var id = BuiltInRegistries.ITEM.getKey(item);
		return id == null ? "" : MadokuJSONManager.normalizeRegistryIdentifierForJson(id.toString());
	}

	private static Item firstItemFromIngredient(Ingredient ingredient) {
		if (ingredient == null || ingredient.isEmpty()) return null;
		for (Item candidate : BuiltInRegistries.ITEM) {
			if (candidate != null && ingredient.test(new ItemStack(candidate))) return candidate;
		}
		return null;
	}

	private static void collectIngredientItems(JsonElement element, List<Item> into) {
		if (element == null || element.isJsonNull() || into == null) return;
		if (element instanceof JsonPrimitive primitive && primitive.isString()) {
			Item item = resolveItem(primitive.getAsString());
			if (item != null) into.add(item);
			return;
		}
		if (element instanceof JsonArray array) {
			for (JsonElement entry : array) collectIngredientItems(entry, into);
			return;
		}
		if (!(element instanceof JsonObject object)) return;
		JsonElement itemElement = object.get("item");
		if (itemElement instanceof JsonPrimitive primitive && primitive.isString()) {
			Item item = resolveItem(primitive.getAsString());
			if (item != null) into.add(item);
		}
		collectIngredientItems(object.get("items"), into);
	}

	private static Set<Character> collectRequiredPatternKeys(List<String> patternRows) {
		Set<Character> required = new LinkedHashSet<>();
		for (String row : patternRows) {
			if (row == null) continue;
			for (int index = 0; index < row.length(); index++) {
				char symbol = row.charAt(index);
				if (!isEmptyPatternSymbol(symbol)) required.add(symbol);
			}
		}
		return required;
	}

	private static boolean isEmptyPatternSymbol(char symbol) { return symbol == ' ' || symbol == EMPTY_SLOT_SYMBOL.charAt(0); }

	private static Item resolveItem(String itemId) {
		if (itemId == null || itemId.isBlank()) return null;
		var identifier = net.minecraft.resources.Identifier.tryParse(
			MadokuJSONManager.normalizeRegistryIdentifierForLookup(itemId)
		);
		if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
			return null;
		}
		return BuiltInRegistries.ITEM.getValue(identifier);
	}

	static String readRecipeGroup(JsonObject root, String fallback) {
		String group = readString(root, RecipesConfigManager.FIELD_RECIPE_GROUP, fallback);
		return group == null ? "" : group.trim();
	}

	private static String readString(JsonObject root, String key, String fallback) {
		if (root == null || !(root.get(key) instanceof JsonPrimitive primitive) || !primitive.isString()) return fallback;
		return primitive.getAsString();
	}

	private static int readInt(JsonObject root, String key, int fallback) {
		if (root == null || !(root.get(key) instanceof JsonPrimitive primitive) || !primitive.isNumber()) return fallback;
		return primitive.getAsInt();
	}
}

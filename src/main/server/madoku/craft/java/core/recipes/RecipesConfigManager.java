package madoku.craft.java.core.recipes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import madoku.craft.java.core.MadokuCoreManager;
import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;

public final class RecipesConfigManager {
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_RECIPE_ID = "recipe-id";
	public static final String FIELD_RECIPE_GROUP = "recipe-group";
	public static final String FIELD_RECIPE_TYPE_ID = "recipe-type-id";
	public static final String FIELD_RESULT_ITEM_ID = "result-item-id";
	public static final String FIELD_RESULT_COUNT = "result-count";
	public static final String FIELD_CATEGORIES = "categories";
	public static final String FIELD_CUSTOM_RECIPE = "custom-recipe";
	public static final String FIELD_CRAFTING_SHAPE = "crafting-shape";
	public static final String FIELD_PATTERN = "pattern";
	public static final String FIELD_KEY = "key";
	public static final String FIELD_INPUT = "input";
	public static final String FIELD_INGREDIENTS = "ingredients";
	public static final String FIELD_TEMPLATE = "template";
	public static final String FIELD_BASE = "base";
	public static final String FIELD_ADDITION = "addition";

	public static final String CATEGORY_BLOCK = "block";
	public static final String CATEGORY_ITEM = "item";
	public static final String BLOCKS_FOLDER_NAME = "blocks";
	public static final String ITEMS_FOLDER_NAME = "items";
	public static final String CATEGORY_CRAFTING = "crafting";
	public static final String CATEGORY_SMELTING = "smelting";
	public static final String CATEGORY_BLASTING = "blasting";
	public static final String CATEGORY_SMOKING = "smoking";
	public static final String CATEGORY_CAMPFIRE = "campfire";
	public static final String CATEGORY_SMITHING = "smithing";
	public static final String CATEGORY_STONECUTTING = "stonecutting";
	public static final String CATEGORY_OTHER = "other";
	public static final String CRAFTING_SHAPE_SHAPED = "shaped";
	public static final String CRAFTING_SHAPE_SHAPELESS = "shapeless";

	private static volatile boolean initialized;

	private RecipesConfigManager() {
	}

	static void initialize() {
		ConfigSmithingManager.initialize();
		ConfigCraftingManager.initialize();
		ConfigSmeltingManager.initialize();
		ConfigBlastingManager.initialize();
		ConfigSmokingManager.initialize();
		ConfigCampfireManager.initialize();
		ConfigStonecuttingManager.initialize();
		initialized = true;
	}

	static void reset() {
		ConfigSmithingManager.reset();
		ConfigCraftingManager.reset();
		ConfigSmeltingManager.reset();
		ConfigBlastingManager.reset();
		ConfigSmokingManager.reset();
		ConfigCampfireManager.reset();
		ConfigStonecuttingManager.reset();
		initialized = false;
	}
	public static boolean isInitialized() { return initialized; }

	public static String folderForOutputCategory(String outputCategory) {
		return CATEGORY_BLOCK.equals(outputCategory) ? BLOCKS_FOLDER_NAME : ITEMS_FOLDER_NAME;
	}

	public static final String ROOT_FOLDER_NAME = "madoku-recipes";
	public static final String SETTINGS_FILE_NAME = "madoku-recipes";
	public static final String RECIPE_GROUP = "recipe";
	public static final String SETTINGS_GROUP = "settings";

	public static java.nio.file.Path getRootDirectory() {
		return JSONAPIManager.getOrCreateGlobalSystemDirectory(
			MadokuCoreManager.CORE_FOLDER_NAME + "/" + ROOT_FOLDER_NAME
		);
	}

	public static JsonObject nestRecipeDefaults(JsonObject flatDefaults, String processCategory) {
		JsonObject source = flatDefaults == null ? new JsonObject() : flatDefaults;
		JsonObject root = new JsonObject();
		if (source.has(FIELD_ENABLED)) root.add(FIELD_ENABLED, source.get(FIELD_ENABLED).deepCopy());

		JsonObject recipe = new JsonObject();
		copy(source, recipe, FIELD_RECIPE_ID);
		copy(source, recipe, FIELD_RECIPE_GROUP);
		copy(source, recipe, FIELD_CUSTOM_RECIPE);
		copy(source, recipe, FIELD_CATEGORIES);
		root.add(RECIPE_GROUP, recipe);

		String category = processCategory == null || processCategory.isBlank() ? CATEGORY_OTHER : processCategory;
		JsonObject format = new JsonObject();
		for (var entry : source.entrySet()) {
			String key = entry.getKey();
			if (FIELD_ENABLED.equals(key) || FIELD_RECIPE_ID.equals(key) || FIELD_RECIPE_GROUP.equals(key) || FIELD_CUSTOM_RECIPE.equals(key)
				|| FIELD_CATEGORIES.equals(key) || FIELD_RECIPE_TYPE_ID.equals(key)) continue;
			String outputKey = FIELD_CRAFTING_SHAPE.equals(key) ? "type" : key;
			format.add(outputKey, entry.getValue().deepCopy());
		}
		root.add(category, format);
		return root;
	}

	public static JsonObject flattenRecipeConfig(JsonObject nestedRoot) {
		JsonObject source = nestedRoot == null ? new JsonObject() : nestedRoot;
		JsonObject flat = new JsonObject();
		copy(source, flat, FIELD_ENABLED);
		JsonObject recipe = object(source, RECIPE_GROUP);
		copy(recipe, flat, FIELD_RECIPE_ID);
		copy(recipe, flat, FIELD_RECIPE_GROUP);
		copy(recipe, flat, FIELD_CUSTOM_RECIPE);
		copy(recipe, flat, FIELD_CATEGORIES);
		for (var group : source.entrySet()) {
			if (FIELD_ENABLED.equals(group.getKey()) || RECIPE_GROUP.equals(group.getKey()) || !(group.getValue() instanceof JsonObject format)) continue;
			for (var entry : format.entrySet()) {
				String key = "type".equals(entry.getKey()) ? FIELD_CRAFTING_SHAPE : entry.getKey();
				flat.add(key, entry.getValue().deepCopy());
			}
		}
		return flat;
	}

	private static void copy(JsonObject source, JsonObject target, String key) {
		if (source != null && source.has(key)) target.add(key, source.get(key).deepCopy());
	}

	private static JsonObject object(JsonObject source, String key) {
		if (source != null && source.get(key) instanceof JsonObject value) return value;
		return new JsonObject();
	}

	public static JsonObject buildRecipeSystemDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENABLED, true)
			.build();
	}

	public static JsonObject buildBaseRecipeDefaults(
		String recipeId,
		String recipeGroup,
		String recipeTypeId,
		String resultItemId,
		int resultCount,
		String outputCategory,
		String processCategory,
		boolean enabled
	) {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENABLED, enabled)
			.put(FIELD_RECIPE_ID, recipeId == null ? "" : recipeId)
			.put(FIELD_RECIPE_GROUP, recipeGroup == null ? "" : recipeGroup)
			.put(FIELD_RECIPE_TYPE_ID, recipeTypeId == null ? "" : recipeTypeId)
			.put(FIELD_RESULT_ITEM_ID, JSONAPIManager.normalizeRegistryIdentifierForJson(resultItemId))
			.put(FIELD_RESULT_COUNT, Math.max(1, resultCount))
			.put(FIELD_CATEGORIES, buildCategoryArray(outputCategory, processCategory))
			.put(FIELD_CUSTOM_RECIPE, false)
			.build();
	}

	private static JsonArray buildCategoryArray(String outputCategory, String processCategory) {
		JSONFormatAPIManager.ArrayBuilder categories = JSONFormatAPIManager.array();
		if (outputCategory != null && !outputCategory.isBlank()) {
			categories.add(outputCategory);
		}
		if (processCategory != null && !processCategory.isBlank()) {
			categories.add(processCategory);
		}
		return categories.build();
	}
}




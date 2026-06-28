package madoku.craft.recipe.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import madoku.craft.config.JsonFormatBuilder;

public final class MadokuRecipeConfig {
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_RECIPE_ID = "recipe-id";
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

	private MadokuRecipeConfig() {
	}

	public static JsonObject buildRecipeSystemDefaults() {
		return JsonFormatBuilder.object()
			.put(FIELD_ENABLED, true)
			.build();
	}

	public static JsonObject buildBaseRecipeDefaults(
		String recipeId,
		String recipeTypeId,
		String resultItemId,
		int resultCount,
		String outputCategory,
		String processCategory,
		boolean enabled
	) {
		return JsonFormatBuilder.object()
			.put(FIELD_ENABLED, enabled)
			.put(FIELD_RECIPE_ID, recipeId == null ? "" : recipeId)
			.put(FIELD_RECIPE_TYPE_ID, recipeTypeId == null ? "" : recipeTypeId)
			.put(FIELD_RESULT_ITEM_ID, resultItemId == null ? "" : resultItemId)
			.put(FIELD_RESULT_COUNT, Math.max(1, resultCount))
			.put(FIELD_CATEGORIES, buildCategoryArray(outputCategory, processCategory))
			.put(FIELD_CUSTOM_RECIPE, false)
			.build();
	}

	private static JsonArray buildCategoryArray(String outputCategory, String processCategory) {
		JsonFormatBuilder.ArrayBuilder categories = JsonFormatBuilder.array();
		if (outputCategory != null && !outputCategory.isBlank()) {
			categories.add(outputCategory);
		}
		if (processCategory != null && !processCategory.isBlank()) {
			categories.add(processCategory);
		}
		return categories.build();
	}
}


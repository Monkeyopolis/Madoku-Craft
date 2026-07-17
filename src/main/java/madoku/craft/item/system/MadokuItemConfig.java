package madoku.craft.item.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MadokuItemConfig {
	public static final String FIELD_ITEM_ID = "item-id";
	public static final String FIELD_FUEL_ENABLED = "fuel-enabled";
	public static final String FIELD_MISC_ENABLED = "misc-enabled";
	public static final String FIELD_FARMING_ENABLED = "farming-enabled";
	public static final String FIELD_COMPOSTER_ENABLED = "composter-enabled";
	public static final String FIELD_TOOL_ENABLED = "tool-enabled";
	public static final String FIELD_ARMOR_ENABLED = "armor-enabled";
	public static final String FIELD_CATEGORY = "categories";
	public static final String FIELD_CATEGORY_NAME = "category";
	public static final String FIELD_CATEGORY_WEIGHT = "weight";
	public static final String FIELD_STACK = "stack";
	public static final String STACK_SINGLE = "single";
	public static final String STACK_MULTI = "multi";

	public static final String CATEGORY_FUEL = "fuel";
	public static final String CATEGORY_MISC = "misc";
	public static final String CATEGORY_TOOL = "tool";
	public static final String CATEGORY_ARMOR = "armor";
	public static final String CATEGORY_COMPOSTER = "composter";
	public static final String CATEGORY_FARMING = "farming";
	public static final String FIELD_COMPOSTER_ADJUSTMENT = "composter-adjustment";

	public static final String FIELD_FUEL_TICKS = "fuel-ticks";
	public static final String FIELD_DURABILITY = "durability";
	public static final String FIELD_ATTACK_DAMAGE = "attack-damage";
	public static final String FIELD_ATTACK_SPEED = "attack-speed";
	public static final String FIELD_MINING_SPEED = "mining-speed";
	public static final String FIELD_MATERIAL_LEVEL = "material-level";
	public static final String FIELD_ARMOR = "armor";
	public static final String FIELD_ARMOR_TOUGHNESS = "armor-toughness";
	public static final String FIELD_REACH_MIN = "reach-min";
	public static final String FIELD_REACH_MAX = "reach-max";

	public static final int TOOL_INT_UNSET = -1;
	public static final double TOOL_DOUBLE_UNSET = -1.0d;

	private MadokuItemConfig() {
	}

	public static JsonObject buildCategoryFeatureDefaults() {
		return JSONFormatManager.object()
			.put(FIELD_FUEL_ENABLED, true)
			.put(FIELD_MISC_ENABLED, true)
			.put(FIELD_FARMING_ENABLED, true)
			.put(FIELD_COMPOSTER_ENABLED, true)
			.put(FIELD_TOOL_ENABLED, true)
			.put(FIELD_ARMOR_ENABLED, true)
			.build();
	}

	public static Map<String, JsonObject> buildDefaultFuelFileDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		for (Map.Entry<String, Double> entry : buildDefaultFuelTicks().entrySet()) {
			String itemId = entry.getKey();
			String fileKey = fileKeyFromItemId(itemId);
			if (fileKey.isBlank()) {
				continue;
			}
			defaults.put(fileKey, buildFuelItemDefaults(itemId, entry.getValue(), defaultStackForItem(itemId)));
		}
		return defaults;
	}

	public static Map<String, JsonObject> buildDefaultMiscFileDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		for (String itemId : buildDefaultMiscItems().keySet()) {
			String fileKey = fileKeyFromItemId(itemId);
			if (fileKey.isBlank()) {
				continue;
			}
			defaults.put(fileKey, buildMiscItemDefaults(itemId, defaultStackForItem(itemId)));
		}
		return defaults;
	}

	public static Map<String, JsonObject> buildDefaultFarmingFileDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		for (String itemId : buildDefaultFarmingItems().keySet()) {
			String fileKey = fileKeyFromItemId(itemId);
			if (fileKey.isBlank()) {
				continue;
			}
			defaults.put(fileKey, buildFarmingItemDefaults(itemId, defaultStackForItem(itemId)));
		}
		return defaults;
	}

	public static Map<String, JsonObject> buildDefaultToolFileDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		for (Map.Entry<String, JsonObject> entry : buildDefaultToolItemProfiles().entrySet()) {
			String fileKey = fileKeyFromItemId(entry.getKey());
			if (fileKey.isBlank()) {
				continue;
			}
			defaults.put(fileKey, entry.getValue());
		}
		return defaults;
	}

	public static Map<String, JsonObject> buildDefaultSpearFileDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		for (Map.Entry<String, JsonObject> entry : buildDefaultSpearItemProfiles().entrySet()) {
			String fileKey = fileKeyFromItemId(entry.getKey());
			if (fileKey.isBlank()) {
				continue;
			}
			defaults.put(fileKey, entry.getValue());
		}
		return defaults;
	}

	public static Map<String, JsonObject> buildDefaultToolsCategoryFileDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>(buildDefaultToolFileDefaults());
		defaults.putAll(buildDefaultSpearFileDefaults());
		return defaults;
	}

	public static Map<String, JsonObject> buildDefaultArmorFileDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		for (Map.Entry<String, JsonObject> entry : buildDefaultArmorItemProfiles().entrySet()) {
			String fileKey = fileKeyFromItemId(entry.getKey());
			if (fileKey.isBlank()) {
				continue;
			}
			defaults.put(fileKey, entry.getValue());
		}
		return defaults;
	}

	public static JsonObject buildFuelItemDefaults(String itemId, double fuelTicks) {
		return buildFuelItemDefaults(itemId, fuelTicks, defaultStackForItem(itemId));
	}

	public static JsonObject buildFuelItemDefaults(String itemId, double fuelTicks, String stackValue) {
		return JSONFormatManager.object()
			.putAll(buildBaseDefaults(itemId, stackValue, category(CATEGORY_FUEL, 100)))
			.put(FIELD_FUEL_TICKS, fuelTicks)
			.build();
	}

	public static JsonObject buildMiscItemDefaults(String itemId, String stackValue) {
		String normalizedItemId = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
		if ("minecraft:bone_meal".equals(normalizedItemId)) {
			return buildBaseDefaults(itemId, stackValue, category(CATEGORY_MISC, 100), category(CATEGORY_FARMING, 75));
		}
		return buildBaseDefaults(itemId, stackValue, category(CATEGORY_MISC, 100));
	}

	public static JsonObject buildFarmingItemDefaults(String itemId, String stackValue) {
		return JSONFormatManager.object()
			.putAll(buildBaseDefaults(
				itemId,
				stackValue,
				category(CATEGORY_FARMING, 100),
				category(CATEGORY_COMPOSTER, 90),
				category(CATEGORY_MISC, 80)
			))
			.put(FIELD_COMPOSTER_ADJUSTMENT, defaultComposterAdjustmentForFarmingItem(itemId))
			.build();
	}

	public static JsonObject buildToolItemDefaults(String itemId) {
		return buildToolItemDefaults(
			itemId,
			TOOL_INT_UNSET,
			TOOL_DOUBLE_UNSET,
			TOOL_DOUBLE_UNSET,
			TOOL_DOUBLE_UNSET,
			TOOL_INT_UNSET,
			STACK_SINGLE
		);
	}

	public static JsonObject buildToolItemDefaults(
		String itemId,
		int durability,
		double attackDamage,
		double attackSpeed,
		double miningSpeed,
		int materialLevel,
		String stackValue
	) {
		return JSONFormatManager.object()
			.putAll(buildBaseDefaults(itemId, stackValue, category(CATEGORY_TOOL, 100)))
			.put(FIELD_DURABILITY, durability)
			.put(FIELD_ATTACK_DAMAGE, attackDamage)
			.put(FIELD_ATTACK_SPEED, attackSpeed)
			.put(FIELD_MINING_SPEED, miningSpeed)
			.put(FIELD_MATERIAL_LEVEL, materialLevel)
			.build();
	}

	public static JsonObject buildSpearItemDefaults(String itemId) {
		return buildSpearItemDefaults(
			itemId,
			TOOL_INT_UNSET,
			TOOL_DOUBLE_UNSET,
			TOOL_DOUBLE_UNSET,
			TOOL_INT_UNSET,
			1.0d,
			4.5d,
			STACK_SINGLE
		);
	}

	public static JsonObject buildSpearItemDefaults(
		String itemId,
		int durability,
		double attackDamage,
		double attackSpeed,
		int materialLevel,
		double reachMin,
		double reachMax,
		String stackValue
	) {
		return JSONFormatManager.object()
			.putAll(buildBaseDefaults(itemId, stackValue, category(CATEGORY_TOOL, 100)))
			.put(FIELD_DURABILITY, durability)
			.put(FIELD_ATTACK_DAMAGE, attackDamage)
			.put(FIELD_ATTACK_SPEED, attackSpeed)
			.put(FIELD_MATERIAL_LEVEL, materialLevel)
			.put(FIELD_REACH_MIN, reachMin)
			.put(FIELD_REACH_MAX, reachMax)
			.build();
	}

	public static JsonObject buildArmorItemDefaults(String itemId) {
		return buildArmorItemDefaults(
			itemId,
			TOOL_INT_UNSET,
			TOOL_DOUBLE_UNSET,
			TOOL_DOUBLE_UNSET,
			STACK_SINGLE
		);
	}

	public static JsonObject buildArmorItemDefaults(
		String itemId,
		int durability,
		double armor,
		double armorToughness,
		String stackValue
	) {
		return JSONFormatManager.object()
			.putAll(buildBaseDefaults(itemId, stackValue, category(CATEGORY_ARMOR, 100)))
			.put(FIELD_DURABILITY, durability)
			.put(FIELD_ARMOR, armor)
			.put(FIELD_ARMOR_TOUGHNESS, armorToughness)
			.build();
	}

	public static JsonObject buildBaseDefaults(String itemId, String stackValue, CategoryWeight... categories) {
		return JSONFormatManager.object()
			.put(FIELD_ITEM_ID, itemId == null ? "" : itemId)
			.put(FIELD_CATEGORY, buildCategoryArray(categories))
			.put(FIELD_STACK, normalizeStackValue(stackValue))
			.build();
	}

	public static CategoryWeight category(String category, int weight) {
		return new CategoryWeight(category, weight);
	}

	private static JsonArray buildCategoryArray(CategoryWeight... categoryWeights) {
		JSONFormatManager.ArrayBuilder categoryArray = JSONFormatManager.array();
		if (categoryWeights == null || categoryWeights.length == 0) {
			categoryArray.add(newCategoryEntry(CATEGORY_MISC, 100));
			return categoryArray.build();
		}

		Map<String, Integer> normalizedCategories = new LinkedHashMap<>();
		for (CategoryWeight categoryWeight : categoryWeights) {
			if (categoryWeight == null) {
				continue;
			}
			String normalizedCategory = normalizeCategoryValue(categoryWeight.category());
			int normalizedWeight = Math.max(0, categoryWeight.weight());
			if (normalizedCategory.isEmpty() || normalizedWeight <= 0) {
				continue;
			}
			Integer existingWeight = normalizedCategories.get(normalizedCategory);
			if (existingWeight == null || normalizedWeight > existingWeight) {
				normalizedCategories.put(normalizedCategory, normalizedWeight);
			}
		}

		if (normalizedCategories.isEmpty()) {
			categoryArray.add(newCategoryEntry(CATEGORY_MISC, 100));
			return categoryArray.build();
		}

		for (Map.Entry<String, Integer> entry : normalizedCategories.entrySet()) {
			categoryArray.add(newCategoryEntry(entry.getKey(), entry.getValue()));
		}

		return categoryArray.build();
	}

	private static JsonObject newCategoryEntry(String category, int weight) {
		return JSONFormatManager.object()
			.put(FIELD_CATEGORY_NAME, category)
			.put(FIELD_CATEGORY_WEIGHT, Math.max(1, weight))
			.build();
	}

	private static String normalizeCategoryValue(String rawCategoryValue) {
		return rawCategoryValue == null ? "" : rawCategoryValue.trim().toLowerCase(Locale.ROOT);
	}

	public static final class CategoryWeight {
		private final String category;
		private final int weight;

		private CategoryWeight(String category, int weight) {
			this.category = category;
			this.weight = weight;
		}

		public String category() {
			return category;
		}

		public int weight() {
			return weight;
		}
	}

	private static String defaultStackForItem(String itemId) {
		if ("minecraft:lava_bucket".equals(itemId)
			|| "minecraft:water_bucket".equals(itemId)
			|| "minecraft:milk_bucket".equals(itemId)
			|| "minecraft:powder_snow_bucket".equals(itemId)) {
			return STACK_SINGLE;
		}
		return STACK_MULTI;
	}

	public static String normalizeStackValue(String rawStackValue) {
		String normalized = rawStackValue == null ? "" : rawStackValue.trim().toLowerCase(Locale.ROOT);
		if (STACK_SINGLE.equals(normalized)) {
			return STACK_SINGLE;
		}
		return STACK_MULTI;
	}

	private static String fileKeyFromItemId(String itemId) {
		if (itemId == null) {
			return "";
		}
		String normalized = itemId.trim();
		if (normalized.isEmpty()) {
			return "";
		}
		int separator = normalized.indexOf(':');
		if (separator < 0 || separator >= normalized.length() - 1) {
			return normalized.toLowerCase();
		}
		return normalized.substring(separator + 1).toLowerCase();
	}

	public static Map<String, Double> buildDefaultFuelTicks() {
		Map<String, Double> defaults = new LinkedHashMap<>();
		defaults.put("minecraft:lava_bucket", 86400.0);
		defaults.put("minecraft:coal_block", 19200.0);
		defaults.put("minecraft:magma_block", 12800.0);
		defaults.put("minecraft:dried_kelp_block", 4800.0);
		defaults.put("minecraft:blaze_rod", 3600.0);
		defaults.put("minecraft:coal", 2400.0);
		defaults.put("minecraft:charcoal", 1600.0);
		defaults.put("minecraft:mangrove_roots", 800.0);
		defaults.put("minecraft:muddy_mangrove_roots", 800.0);
		defaults.put("minecraft:crimson_stem", 800.0);
		defaults.put("minecraft:warped_stem", 800.0);
		defaults.put("minecraft:stripped_crimson_stem", 800.0);
		defaults.put("minecraft:stripped_warped_stem", 800.0);
		defaults.put("minecraft:oak_log", 600.0);
		defaults.put("minecraft:spruce_log", 600.0);
		defaults.put("minecraft:birch_log", 600.0);
		defaults.put("minecraft:jungle_log", 600.0);
		defaults.put("minecraft:acacia_log", 600.0);
		defaults.put("minecraft:cherry_log", 600.0);
		defaults.put("minecraft:dark_oak_log", 600.0);
		defaults.put("minecraft:mangrove_log", 600.0);
		defaults.put("minecraft:pale_oak_log", 600.0);
		defaults.put("minecraft:stripped_oak_log", 600.0);
		defaults.put("minecraft:stripped_spruce_log", 600.0);
		defaults.put("minecraft:stripped_birch_log", 600.0);
		defaults.put("minecraft:stripped_jungle_log", 600.0);
		defaults.put("minecraft:stripped_acacia_log", 600.0);
		defaults.put("minecraft:stripped_cherry_log", 600.0);
		defaults.put("minecraft:stripped_dark_oak_log", 600.0);
		defaults.put("minecraft:stripped_mangrove_log", 600.0);
		defaults.put("minecraft:stripped_pale_oak_log", 600.0);
		defaults.put("minecraft:oak_wood", 600.0);
		defaults.put("minecraft:spruce_wood", 600.0);
		defaults.put("minecraft:birch_wood", 600.0);
		defaults.put("minecraft:jungle_wood", 600.0);
		defaults.put("minecraft:acacia_wood", 600.0);
		defaults.put("minecraft:cherry_wood", 600.0);
		defaults.put("minecraft:pale_oak_wood", 600.0);
		defaults.put("minecraft:dark_oak_wood", 600.0);
		defaults.put("minecraft:mangrove_wood", 600.0);
		defaults.put("minecraft:stripped_oak_wood", 600.0);
		defaults.put("minecraft:stripped_spruce_wood", 600.0);
		defaults.put("minecraft:stripped_birch_wood", 600.0);
		defaults.put("minecraft:stripped_jungle_wood", 600.0);
		defaults.put("minecraft:stripped_acacia_wood", 600.0);
		defaults.put("minecraft:stripped_cherry_wood", 600.0);
		defaults.put("minecraft:stripped_pale_oak_wood", 600.0);
		defaults.put("minecraft:stripped_dark_oak_wood", 600.0);
		defaults.put("minecraft:stripped_mangrove_wood", 600.0);
		defaults.put("minecraft:stripped_bamboo_block", 450.0);
		defaults.put("minecraft:bamboo_block", 450.0);
		defaults.put("minecraft:crimson_planks", 400.0);
		defaults.put("minecraft:warped_planks", 400.0);
		defaults.put("minecraft:oak_planks", 300.0);
		defaults.put("minecraft:spruce_planks", 300.0);
		defaults.put("minecraft:birch_planks", 300.0);
		defaults.put("minecraft:jungle_planks", 300.0);
		defaults.put("minecraft:acacia_planks", 300.0);
		defaults.put("minecraft:cherry_planks", 300.0);
		defaults.put("minecraft:dark_oak_planks", 300.0);
		defaults.put("minecraft:mangrove_planks", 300.0);
		defaults.put("minecraft:bamboo_planks", 300.0);
		defaults.put("minecraft:pale_oak_planks", 300.0);
		defaults.put("minecraft:leaf_litter", 200.0);
		defaults.put("minecraft:crimson_fungus", 200.0);
		defaults.put("minecraft:warped_fungus", 200.0);
		defaults.put("minecraft:oak_sapling", 150.0);
		defaults.put("minecraft:spruce_sapling", 150.0);
		defaults.put("minecraft:birch_sapling", 150.0);
		defaults.put("minecraft:jungle_sapling", 150.0);
		defaults.put("minecraft:acacia_sapling", 150.0);
		defaults.put("minecraft:dark_oak_sapling", 150.0);
		defaults.put("minecraft:mangrove_propagule", 150.0);
		defaults.put("minecraft:cherry_sapling", 150.0);
		defaults.put("minecraft:pale_oak_sapling", 150.0);
		defaults.put("minecraft:stick", 100.0);
		defaults.put("minecraft:bamboo", 50.0);
		return defaults;
	}

	public static Map<String, JsonObject> buildDefaultToolItemProfiles() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();

		String[] materials = {"wooden", "stone", "copper", "iron", "golden", "diamond", "netherite"};
		int[] durability = {64, 128, 256, 512, 1024, 2048, 4096};
		int[] materialLevel = {0, 1, 2, 2, 3, 3, 4};
		double[] pickAndShovelDamage = {1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0};

		for (int index = 0; index < materials.length; index++) {
			String prefix = "minecraft:" + materials[index] + "_";
			int itemDurability = durability[index];
			int itemLevel = materialLevel[index];
			double attackStep = index;
			double miningSpeed = 2.0 + (index * 2.0);

			defaults.put(
				prefix + "sword",
				buildToolItemDefaults(prefix + "sword", itemDurability, 6.0 + attackStep, 1.6 + (index * 0.1), TOOL_DOUBLE_UNSET, itemLevel, STACK_SINGLE)
			);
			defaults.put(
				prefix + "axe",
				buildToolItemDefaults(prefix + "axe", itemDurability, 9.0 + attackStep, 0.8 + (index * 0.1), miningSpeed, itemLevel, STACK_SINGLE)
			);
			defaults.put(
				prefix + "pickaxe",
				buildToolItemDefaults(prefix + "pickaxe", itemDurability, pickAndShovelDamage[index], 1.2 + (index * 0.1), miningSpeed, itemLevel, STACK_SINGLE)
			);
			defaults.put(
				prefix + "shovel",
				buildToolItemDefaults(prefix + "shovel", itemDurability, pickAndShovelDamage[index], 1.2 + (index * 0.1), miningSpeed, itemLevel, STACK_SINGLE)
			);
			defaults.put(
				prefix + "hoe",
				buildToolItemDefaults(prefix + "hoe", itemDurability, pickAndShovelDamage[index], 1.2 + (index * 0.1), miningSpeed, itemLevel, STACK_SINGLE)
			);
		}

		defaults.put(
			"minecraft:shears",
			buildToolItemDefaults("minecraft:shears", 512, TOOL_DOUBLE_UNSET, TOOL_DOUBLE_UNSET, TOOL_DOUBLE_UNSET, TOOL_INT_UNSET, STACK_SINGLE)
		);
		defaults.put(
			"minecraft:flint_and_steel",
			buildToolItemDefaults("minecraft:flint_and_steel", 128, TOOL_DOUBLE_UNSET, TOOL_DOUBLE_UNSET, TOOL_DOUBLE_UNSET, TOOL_INT_UNSET, STACK_SINGLE)
		);
		defaults.put(
			"minecraft:fishing_rod",
			buildToolItemDefaults("minecraft:fishing_rod", 64, TOOL_DOUBLE_UNSET, TOOL_DOUBLE_UNSET, TOOL_DOUBLE_UNSET, TOOL_INT_UNSET, STACK_SINGLE)
		);

		return defaults;
	}

	public static Map<String, JsonObject> buildDefaultSpearItemProfiles() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		String[] materials = {"wooden", "stone", "copper", "iron", "golden", "diamond", "netherite"};
		int[] durability = {64, 128, 256, 512, 1024, 2048, 4096};
		int[] materialLevel = {0, 1, 2, 2, 3, 3, 4};

		for (int index = 0; index < materials.length; index++) {
			String itemId = "minecraft:" + materials[index] + "_spear";
			defaults.put(
				itemId,
				buildSpearItemDefaults(
					itemId,
					durability[index],
					3.0 + index,
					1.2 + (index * 0.1),
					materialLevel[index],
					1.0d,
					4.5d,
					STACK_SINGLE
				)
			);
		}

		return defaults;
	}

	public static Map<String, JsonObject> buildDefaultArmorItemProfiles() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		String[] materials = {"leather", "copper", "iron", "golden", "diamond", "netherite"};
		int[] durability = {192, 256, 384, 512, 768, 1024};
		double[] armor = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
		double[] toughness = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0};
		String[] pieces = {"helmet", "chestplate", "leggings", "boots"};

		for (int materialIndex = 0; materialIndex < materials.length; materialIndex++) {
			String material = materials[materialIndex];
			for (String piece : pieces) {
				String itemId = "minecraft:" + material + "_" + piece;
				defaults.put(
					itemId,
					buildArmorItemDefaults(
						itemId,
						durability[materialIndex],
						armor[materialIndex],
						toughness[materialIndex],
						STACK_SINGLE
					)
				);
			}
		}

		return defaults;
	}

	public static Map<String, Boolean> buildDefaultMiscItems() {
		Map<String, Boolean> defaults = new LinkedHashMap<>();
		defaults.put("minecraft:bone_meal", true);
		defaults.put("minecraft:water_bucket", true);
		defaults.put("minecraft:milk_bucket", true);
		defaults.put("minecraft:powder_snow_bucket", true);
		return defaults;
	}

	public static Map<String, Boolean> buildDefaultFarmingItems() {
		Map<String, Boolean> defaults = new LinkedHashMap<>();
		defaults.put("minecraft:carrot", true);
		defaults.put("minecraft:potato", true);
		defaults.put("minecraft:beetroot_seeds", true);
		defaults.put("minecraft:wheat_seeds", true);
		defaults.put("minecraft:melon_seeds", true);
		defaults.put("minecraft:pumpkin_seeds", true);
		return defaults;
	}

	private static int defaultComposterAdjustmentForFarmingItem(String itemId) {
		String normalizedItemId = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
		return switch (normalizedItemId) {
			case "minecraft:carrot", "minecraft:potato" -> 2;
			case "minecraft:beetroot_seeds",
				"minecraft:wheat_seeds",
				"minecraft:melon_seeds",
				"minecraft:pumpkin_seeds" -> 1;
			default -> 1;
		};
	}

}


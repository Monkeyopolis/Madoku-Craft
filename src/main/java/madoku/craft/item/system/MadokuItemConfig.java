package madoku.craft.item.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MadokuItemConfig {
	public static final String FIELD_ITEM_SYSTEM_ENABLED = "itemSystemEnabled";
	public static final String FIELD_ITEM_ID = "item_id";
	public static final String FIELD_PRIMARY_CATEGORY = "primary_category";
	public static final String FIELD_SECONDARY_CATEGORIES = "secondary_categories";
	public static final String FIELD_STACK = "stack";
	public static final String STACK_SINGLE = "single";
	public static final String STACK_MULTI = "multi";

	public static final String PRIMARY_CATEGORY_FUEL = "fuel";
	public static final String PRIMARY_CATEGORY_MISC = "misc";
	public static final String PRIMARY_CATEGORY_TOOL = "tool";
	public static final String PRIMARY_CATEGORY_ARMOR = "armor";
	public static final String SECONDARY_CATEGORY_COMPOSTER = "composter";
	public static final String SECONDARY_CATEGORY_FARMING = "farming";
	public static final String FIELD_COMPOSTER_ADJUSTMENT = "composter_adjustment";

	public static final String FIELD_FUEL_TICKS = "fuel_ticks";
	public static final String FIELD_DURABILITY = "durability";
	public static final String FIELD_ATTACK_DAMAGE = "attack_damage";
	public static final String FIELD_ATTACK_SPEED = "attack_speed";
	public static final String FIELD_MINING_SPEED = "mining_speed";
	public static final String FIELD_MATERIAL_LEVEL = "material_level";
	public static final String FIELD_ARMOR = "armor";
	public static final String FIELD_ARMOR_TOUGHNESS = "armor_toughness";

	public static final int TOOL_INT_UNSET = -1;
	public static final double TOOL_DOUBLE_UNSET = -1.0d;

	private MadokuItemConfig() {
	}

	public static JsonObject buildItemSystemDefaults() {
		JsonObject defaults = new JsonObject();
		defaults.addProperty(FIELD_ITEM_SYSTEM_ENABLED, true);
		return defaults;
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

	public static Map<String, JsonObject> buildDefaultToolsCategoryFileDefaults() {
		return buildDefaultToolFileDefaults();
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
		JsonObject defaults = buildBaseDefaults(itemId, stackValue, PRIMARY_CATEGORY_FUEL);
		defaults.addProperty(FIELD_FUEL_TICKS, fuelTicks);
		return defaults;
	}

	public static JsonObject buildMiscItemDefaults(String itemId, String stackValue) {
		String normalizedItemId = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
		if ("minecraft:bone_meal".equals(normalizedItemId)) {
			return buildBaseDefaults(itemId, stackValue, PRIMARY_CATEGORY_MISC, SECONDARY_CATEGORY_FARMING);
		}
		return buildBaseDefaults(itemId, stackValue, PRIMARY_CATEGORY_MISC);
	}

	public static JsonObject buildFarmingItemDefaults(String itemId, String stackValue) {
		JsonObject defaults = buildBaseDefaults(
			itemId,
			stackValue,
			PRIMARY_CATEGORY_MISC,
			SECONDARY_CATEGORY_FARMING,
			SECONDARY_CATEGORY_COMPOSTER
		);
		defaults.addProperty(FIELD_COMPOSTER_ADJUSTMENT, defaultComposterAdjustmentForFarmingItem(itemId));
		return defaults;
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
		JsonObject defaults = buildBaseDefaults(itemId, stackValue, PRIMARY_CATEGORY_TOOL);
		defaults.addProperty(FIELD_DURABILITY, durability);
		defaults.addProperty(FIELD_ATTACK_DAMAGE, attackDamage);
		defaults.addProperty(FIELD_ATTACK_SPEED, attackSpeed);
		defaults.addProperty(FIELD_MINING_SPEED, miningSpeed);
		defaults.addProperty(FIELD_MATERIAL_LEVEL, materialLevel);
		return defaults;
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
		JsonObject defaults = buildBaseDefaults(itemId, stackValue, PRIMARY_CATEGORY_ARMOR);
		defaults.addProperty(FIELD_DURABILITY, durability);
		defaults.addProperty(FIELD_ARMOR, armor);
		defaults.addProperty(FIELD_ARMOR_TOUGHNESS, armorToughness);
		return defaults;
	}

	public static JsonObject buildBaseDefaults(String itemId, String stackValue, String primaryCategory) {
		return buildBaseDefaults(itemId, stackValue, primaryCategory, new String[0]);
	}

	public static JsonObject buildBaseDefaults(String itemId, String stackValue, String primaryCategory, String... secondaryCategories) {
		JsonObject defaults = new JsonObject();
		defaults.addProperty(FIELD_ITEM_ID, itemId == null ? "" : itemId);
		defaults.addProperty(FIELD_PRIMARY_CATEGORY, normalizeCategoryValue(primaryCategory));
		defaults.add(FIELD_SECONDARY_CATEGORIES, buildSecondaryCategoriesArray(primaryCategory, secondaryCategories));
		defaults.addProperty(FIELD_STACK, normalizeStackValue(stackValue));
		return defaults;
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

		String[] materials = {"wooden", "stone", "iron", "golden", "diamond", "netherite"};
		int[] durability = {32, 64, 256, 512, 2048, 4096};
		int[] materialLevel = {0, 1, 2, 3, 3, 4};
		int[] materialProgress = {0, 1, 3, 4, 5, 6};
		double[] pickAndShovelDamage = {1.0, 1.5, 2.5, 3.0, 3.5, 4.0};

		for (int index = 0; index < materials.length; index++) {
			String prefix = "minecraft:" + materials[index] + "_";
			int itemDurability = durability[index];
			int itemLevel = materialLevel[index];
			double attackStep = materialProgress[index];
			double miningSpeed = 2.0 + (materialProgress[index] * 2.0);

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

	public static Map<String, JsonObject> buildDefaultArmorItemProfiles() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		String[] materials = {"leather", "iron", "golden", "diamond", "netherite"};
		int[] durability = {128, 384, 512, 768, 1024};
		double[] armor = {1.0, 3.0, 4.0, 5.0, 6.0};
		double[] toughness = {0.5, 1.5, 2.0, 2.5, 3.0};
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

	private static JsonArray buildSecondaryCategoriesArray(String primaryCategory, String... secondaryCategories) {
		JsonArray categories = new JsonArray();
		if (secondaryCategories == null || secondaryCategories.length == 0) {
			return categories;
		}
		for (String secondaryCategory : secondaryCategories) {
			String normalized = normalizeCategoryValue(secondaryCategory);
			if (normalized.isBlank()) {
				continue;
			}
			if (normalized.equals(normalizeCategoryValue(primaryCategory))) {
				continue;
			}
			boolean alreadyPresent = false;
			for (int index = 0; index < categories.size(); index++) {
				if (normalized.equals(categories.get(index).getAsString())) {
					alreadyPresent = true;
					break;
				}
			}
			if (!alreadyPresent) {
				categories.add(normalized);
			}
		}
		return categories;
	}

	private static String normalizeCategoryValue(String rawCategoryValue) {
		String normalized = rawCategoryValue == null ? "" : rawCategoryValue.trim().toLowerCase(Locale.ROOT);
		return normalized.isEmpty() ? PRIMARY_CATEGORY_MISC : normalized;
	}

	private static int defaultComposterAdjustmentForFarmingItem(String itemId) {
		String normalizedItemId = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
		return switch (normalizedItemId) {
			case "minecraft:carrot", "minecraft:potato" -> 2;
			case "minecraft:beetroot_seeds", "minecraft:wheat_seeds", "minecraft:melon_seeds", "minecraft:pumpkin_seeds" -> 1;
			default -> 1;
		};
	}
}

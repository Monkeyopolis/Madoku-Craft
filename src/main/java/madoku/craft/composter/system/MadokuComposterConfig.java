package madoku.craft.composter.system;

import com.google.gson.JsonObject;
import madoku.craft.config.JsonFormatBuilder;
import madoku.craft.item.system.MadokuItemConfig;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MadokuComposterConfig {
	public static final String FIELD_COMPOSTER_SYSTEM_ENABLED = "composterSystemEnabled";
	public static final String FIELD_ITEM_ID = "item-id";
	public static final String FIELD_COMPOSTER_ADJUSTMENT = "composter-adjustment";
	public static final String FIELD_STACK = "stack";
	public static final String STACK_SINGLE = "single";
	public static final String STACK_MULTI = "multi";

	private MadokuComposterConfig() {
	}

	public static JsonObject buildComposterSystemDefaults() {
		return JsonFormatBuilder.object()
			.put(FIELD_COMPOSTER_SYSTEM_ENABLED, true)
			.build();
	}

	public static Map<String, JsonObject> buildDefaultComposterFileDefaults(boolean farmingSystemEnabled) {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : buildDefaultComposterItems().entrySet()) {
			String itemId = entry.getKey();
			if (farmingSystemEnabled && MadokuItemConfig.buildDefaultFarmingItems().containsKey(itemId)) {
				continue;
			}
			String fileKey = fileKeyFromItemId(itemId);
			if (fileKey.isBlank()) {
				continue;
			}
			defaults.put(fileKey, buildComposterItemDefaults(itemId, entry.getValue(), STACK_MULTI));
		}
		return defaults;
	}

	public static JsonObject buildComposterItemDefaults(String itemId, int adjustment, String stackValue) {
		JsonObject defaults = MadokuItemConfig.buildBaseDefaults(
			itemId,
			stackValue,
			MadokuItemConfig.category(MadokuItemConfig.CATEGORY_COMPOSTER, 100),
			MadokuItemConfig.category(MadokuItemConfig.CATEGORY_MISC, 90)
		);
		defaults.addProperty(FIELD_COMPOSTER_ADJUSTMENT, Math.max(1, adjustment));
		return defaults;
	}

	public static JsonObject buildDynamicComposterDefaultsForFile(String fileKey) {
		String itemId = fileKeyFromItemId(fileKey);
		if (itemId.isBlank()) {
			itemId = "minecraft:" + normalizeFileKey(fileKey);
		}
		int adjustment = buildDefaultComposterItems().getOrDefault(itemId, 1);
		return buildComposterItemDefaults(itemId, adjustment, STACK_MULTI);
	}

	public static Map<String, Integer> buildDefaultComposterItems() {
		Map<String, Integer> defaults = new LinkedHashMap<>();

		registerComposterItems(defaults, 1,
			"minecraft:beetroot_seeds",
			"minecraft:moss_carpet",
			"minecraft:pink_petals",
			"minecraft:pitcher_pod",
			"minecraft:pumpkin_seeds",
			"minecraft:oak_sapling",
			"minecraft:spruce_sapling",
			"minecraft:birch_sapling",
			"minecraft:jungle_sapling",
			"minecraft:acacia_sapling",
			"minecraft:dark_oak_sapling",
			"minecraft:cherry_sapling",
			"minecraft:mangrove_propagule",
			"minecraft:pale_oak_sapling",
			"minecraft:kelp",
			"minecraft:torchflower_seeds",
			"minecraft:wheat_seeds",
			"minecraft:melon_seeds",
			"minecraft:twisting_vines",
			"minecraft:weeping_vines",
			"minecraft:big_dripleaf",
			"minecraft:lily_pad",
			"minecraft:pale_moss_carpet",
			"minecraft:hanging_roots",
			"minecraft:nether_sprouts",
			"minecraft:warped_roots",
			"minecraft:crimson_roots",
			"minecraft:dandelion",
			"minecraft:poppy",
			"minecraft:blue_orchid",
			"minecraft:allium",
			"minecraft:azure_bluet",
			"minecraft:red_tulip",
			"minecraft:orange_tulip",
			"minecraft:white_tulip",
			"minecraft:pink_tulip",
			"minecraft:oxeye_daisy",
			"minecraft:cornflower",
			"minecraft:lily_of_the_valley",
			"minecraft:sunflower",
			"minecraft:lilac",
			"minecraft:rose_bush",
			"minecraft:peony",
			"minecraft:torchflower",
			"minecraft:pitcher_plant",
			"minecraft:wildflowers"
		);

		registerComposterItems(defaults, 2,
			"minecraft:glow_berries",
			"minecraft:sweet_berries",
			"minecraft:cactus",
			"minecraft:sugar_cane",
			"minecraft:azalea",
			"minecraft:flowering_azalea",
			"minecraft:carrot",
			"minecraft:cocoa_beans",
			"minecraft:crimson_fungus",
			"minecraft:warped_fungus",
			"minecraft:red_mushroom",
			"minecraft:brown_mushroom",
			"minecraft:chorus_fruit",
			"minecraft:nether_wart",
			"minecraft:potato",
			"minecraft:spore_blossom",
			"minecraft:sea_pickle",
			"minecraft:leaf_litter",
			"minecraft:firefly_bush"
		);

		registerComposterItems(defaults, 4,
			"minecraft:mangrove_roots",
			"minecraft:apple",
			"minecraft:beetroot",
			"minecraft:melon_slice",
			"minecraft:moss_block",
			"minecraft:rotten_flesh",
			"minecraft:spider_eye",
			"minecraft:bone",
			"minecraft:rabbit_foot",
			"minecraft:poisonous_potato",
			"minecraft:pufferfish",
			"minecraft:pale_moss_block"
		);

		registerComposterItems(defaults, 8,
			"minecraft:pumpkin",
			"minecraft:hay_block",
			"minecraft:shroomlight"
		);

		return defaults;
	}

	private static void registerComposterItems(Map<String, Integer> defaults, int adjustment, String... itemIds) {
		if (defaults == null || itemIds == null) {
			return;
		}
		for (String itemId : itemIds) {
			if (itemId == null || itemId.isBlank()) {
				continue;
			}
			defaults.put(itemId, Math.max(1, adjustment));
		}
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
			return normalized.toLowerCase(Locale.ROOT);
		}
		return normalized.substring(separator + 1).toLowerCase(Locale.ROOT);
	}

	private static String normalizeFileKey(String fileKey) {
		if (fileKey == null) {
			return "";
		}
		return fileKey.trim().toLowerCase(Locale.ROOT);
	}
}


package madoku.craft.farming.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MadokuCropConfig {
	public static final String FIELD_CROP_ID = "cropId";
	public static final String FIELD_CROP_BLOCK_ID = "cropBlockId";
	public static final String FIELD_PLANTING_ITEM_ID = "plantingItemId";
	public static final String FIELD_HARVEST_ITEM_ID = "harvestItemId";
	public static final String FIELD_GROWTH_MINECRAFT_DAYS = "growthMinecraftDays";
	public static final String FIELD_MIN_HARVEST_COUNT = "minHarvestCount";
	public static final String FIELD_MAX_HARVEST_COUNT = "maxHarvestCount";
	public static final String FIELD_PLANTING_BLOCKED_SEASONS = "plantingBlockedSeasons";

	private MadokuCropConfig() {
	}

	public static Map<String, JsonObject> buildDefaultCropFileDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		defaults.put("potato", buildCropDefaults(
			"potato",
			"minecraft:potatoes",
			"minecraft:potato",
			"minecraft:potato",
			3.0d,
			7,
			9,
			List.of("winter")
		));
		defaults.put("carrot", buildCropDefaults(
			"carrot",
			"minecraft:carrots",
			"minecraft:carrot",
			"minecraft:carrot",
			5.0d,
			5,
			7,
			List.of()
		));
		defaults.put("beetroot", buildCropDefaults(
			"beetroot",
			"minecraft:beetroots",
			"minecraft:beetroot_seeds",
			"minecraft:beetroot",
			3.0d,
			7,
			9,
			List.of("spring", "summer")
		));
		defaults.put("wheat", buildCropDefaults(
			"wheat",
			"minecraft:wheat",
			"minecraft:wheat_seeds",
			"minecraft:wheat",
			7.0d,
			11,
			13,
			List.of("summer")
		));
		return defaults;
	}

	public static JsonObject buildCropDefaults(
		String cropId,
		String cropBlockId,
		String plantingItemId,
		String harvestItemId,
		double growthMinecraftDays,
		int minHarvestCount,
		int maxHarvestCount,
		List<String> blockedSeasons
	) {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_CROP_ID, normalizeRegistryId(cropId));
		root.addProperty(FIELD_CROP_BLOCK_ID, normalizeRegistryId(cropBlockId));
		root.addProperty(FIELD_PLANTING_ITEM_ID, normalizeRegistryId(plantingItemId));
		root.addProperty(FIELD_HARVEST_ITEM_ID, normalizeRegistryId(harvestItemId));
		root.addProperty(FIELD_GROWTH_MINECRAFT_DAYS, growthMinecraftDays);
		root.addProperty(FIELD_MIN_HARVEST_COUNT, Math.max(1, minHarvestCount));
		root.addProperty(FIELD_MAX_HARVEST_COUNT, Math.max(Math.max(1, minHarvestCount), maxHarvestCount));
		JsonArray seasons = new JsonArray();
		if (blockedSeasons != null) {
			for (String season : blockedSeasons) {
				String normalized = normalizeSeasonId(season);
				if (!normalized.isEmpty()) {
					seasons.add(normalized);
				}
			}
		}
		root.add(FIELD_PLANTING_BLOCKED_SEASONS, seasons);
		return root;
	}

	public static String normalizeRegistryId(String value) {
		if (value == null) {
			return "";
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return "";
		}
		return trimmed.contains(":") ? trimmed.toLowerCase(Locale.ROOT) : "minecraft:" + trimmed.toLowerCase(Locale.ROOT);
	}

	public static String normalizeSeasonId(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().toLowerCase(Locale.ROOT);
	}
}

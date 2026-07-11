package madoku.craft.farming.system;

import com.google.gson.JsonObject;

import madoku.craft.api.json.JSONFormatManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MadokuCropConfig {
	public static final String FIELD_CROP_ID = "cropId";
	public static final String FIELD_MATURE_BLOCK_ID = "matureBlockId";
	public static final String FIELD_MIN_HARVEST_SEEDS = "min-harvest-seeds";
	public static final String FIELD_MAX_HARVEST_SEEDS = "max-harvest-seeds";
	public static final String FIELD_GROWTH_TIME = "growth-time";
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
			"minecraft:beetroot_seeds",
			1,
			3,
			3.0d,
			7,
			9,
			List.of("spring", "summer")
		));
		defaults.put("melon", buildCropDefaults(
			"melon",
			"minecraft:melon_stem",
			"minecraft:melon",
			"minecraft:melon_seeds",
			"minecraft:melon_slice",
			"minecraft:melon_seeds",
			1,
			3,
			11.0d,
			15,
			17,
			List.of("fall", "winter")
		));
		defaults.put("pumpkin", buildCropDefaults(
			"pumpkin",
			"minecraft:pumpkin_stem",
			"minecraft:pumpkin",
			"minecraft:pumpkin_seeds",
			"minecraft:pumpkin",
			"minecraft:pumpkin_seeds",
			1,
			3,
			9.0d,
			3,
			5,
			List.of("spring", "winter")
		));
		defaults.put("wheat", buildCropDefaults(
			"wheat",
			"minecraft:wheat",
			"minecraft:wheat_seeds",
			"minecraft:wheat",
			"minecraft:wheat_seeds",
			1,
			3,
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
		return buildCropDefaults(
			cropId,
			cropBlockId,
			plantingItemId,
			harvestItemId,
			"",
			0,
			0,
			growthMinecraftDays,
			minHarvestCount,
			maxHarvestCount,
			blockedSeasons
		);
	}

	public static JsonObject buildCropDefaults(
		String cropId,
		String cropBlockId,
		String matureBlockId,
		String plantingItemId,
		String harvestItemId,
		double growthMinecraftDays,
		int minHarvestCount,
		int maxHarvestCount,
		List<String> blockedSeasons
	) {
		return buildCropDefaults(
			cropId,
			cropBlockId,
			matureBlockId,
			plantingItemId,
			harvestItemId,
			"",
			0,
			0,
			growthMinecraftDays,
			minHarvestCount,
			maxHarvestCount,
			blockedSeasons
		);
	}

	public static JsonObject buildCropDefaults(
		String cropId,
		String cropBlockId,
		String plantingItemId,
		String harvestItemId,
		String secondaryHarvestItemId,
		int secondaryMinHarvestCount,
		int secondaryMaxHarvestCount,
		double growthMinecraftDays,
		int minHarvestCount,
		int maxHarvestCount,
		List<String> blockedSeasons
	) {
		return buildCropDefaults(
			cropId,
			cropBlockId,
			"",
			plantingItemId,
			harvestItemId,
			secondaryHarvestItemId,
			secondaryMinHarvestCount,
			secondaryMaxHarvestCount,
			growthMinecraftDays,
			minHarvestCount,
			maxHarvestCount,
			blockedSeasons
		);
	}

	public static JsonObject buildCropDefaults(
		String cropId,
		String cropBlockId,
		String matureBlockId,
		String plantingItemId,
		String harvestItemId,
		String secondaryHarvestItemId,
		int secondaryMinHarvestCount,
		int secondaryMaxHarvestCount,
		double growthMinecraftDays,
		int minHarvestCount,
		int maxHarvestCount,
		List<String> blockedSeasons
	) {
		JSONFormatManager.ObjectBuilder root = JSONFormatManager.object()
			.put(FIELD_CROP_ID, normalizeRegistryId(cropId))
			.put(FIELD_GROWTH_TIME, growthMinecraftDays)
			.put(FIELD_MIN_HARVEST_COUNT, Math.max(1, minHarvestCount))
			.put(FIELD_MAX_HARVEST_COUNT, Math.max(Math.max(1, minHarvestCount), maxHarvestCount));

		String normalizedMatureBlockId = normalizeRegistryId(matureBlockId);
		if (!normalizedMatureBlockId.isEmpty() && !normalizedMatureBlockId.equals(normalizeRegistryId(cropBlockId))) {
			root.put(FIELD_MATURE_BLOCK_ID, normalizedMatureBlockId);
		}

		String normalizedSecondaryHarvestItemId = normalizeRegistryId(secondaryHarvestItemId);
		if (!normalizedSecondaryHarvestItemId.isEmpty()) {
			root.put(FIELD_MIN_HARVEST_SEEDS, Math.max(0, secondaryMinHarvestCount));
			root.put(FIELD_MAX_HARVEST_SEEDS, Math.max(Math.max(0, secondaryMinHarvestCount), secondaryMaxHarvestCount));
		}

		JSONFormatManager.ArrayBuilder seasons = JSONFormatManager.array();
		if (blockedSeasons != null) {
			for (String season : blockedSeasons) {
				String normalized = normalizeSeasonId(season);
				if (!normalized.isEmpty()) {
					seasons.add(normalized);
				}
			}
		}
		root.put(FIELD_PLANTING_BLOCKED_SEASONS, seasons.build());
		return root.build();
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


package madoku.craft.farming.crops;

import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.MadokuJSONManager;

import java.util.LinkedHashMap;
import java.util.Map;

/** Static defaults and field names for individual farming crop files. */
public final class CropsConfigManager {
	public static final String FIELD_CROP_ID = "crop-id";
	public static final String FIELD_YIELD_ID = "yield-id";
	public static final String FIELD_YIELD_MINIMUM_AMOUNT = "yield-minimum-amount";
	public static final String FIELD_YIELD_MAXIMUM_AMOUNT = "yield-maximum-amount";
	public static final String FIELD_GROWTH_TIME = "growth-time";
	public static final String FIELD_GROWING_CONDITIONS = "growing-conditions";
	public static final String FIELD_IDEAL_TEMPERATURE = "ideal-temperature";
	public static final String FIELD_IDEAL_HUMIDITY = "ideal-humidity";
	public static final String FIELD_MINIMUM_TEMPERATURE = "minimum-temperature";
	public static final String FIELD_MAXIMUM_TEMPERATURE = "maximum-temperature";
	public static final String FIELD_MINIMUM_HUMIDITY = "minimum-humidity";
	public static final String FIELD_MAXIMUM_HUMIDITY = "maximum-humidity";

	private CropsConfigManager() { }

	public static Map<String, JsonObject> buildDefaultCropFileDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		defaults.put("potato", buildCropDefaults("minecraft:potato", 3.0d, new ConditionDefault(35, 55, 50, 70),
			new YieldDefault("minecraft:potato", 7, 9)));
		defaults.put("carrot", buildCropDefaults("minecraft:carrot", 5.0d, new ConditionDefault(40, 60, 45, 65),
			new YieldDefault("minecraft:carrot", 5, 7)));
		defaults.put("beetroot", buildCropDefaults("minecraft:beetroot", 3.0d, new ConditionDefault(45, 65, 50, 70),
			new YieldDefault("minecraft:beetroot", 7, 9),
			new YieldDefault("minecraft:beetroot-seeds", 1, 3)));
		defaults.put("melon", buildCropDefaults("minecraft:melon", 11.0d, new ConditionDefault(50, 80, 35, 55),
			new YieldDefault("minecraft:melon-slice", 15, 17),
			new YieldDefault("minecraft:melon-seeds", 1, 3)));
		defaults.put("pumpkin", buildCropDefaults("minecraft:pumpkin", 9.0d, new ConditionDefault(45, 70, 40, 60),
			new YieldDefault("minecraft:pumpkin", 3, 5),
			new YieldDefault("minecraft:pumpkin-seeds", 1, 3)));
		defaults.put("wheat", buildCropDefaults("minecraft:wheat", 7.0d, new ConditionDefault(40, 60, 45, 55),
			new YieldDefault("minecraft:wheat", 9, 13),
			new YieldDefault("minecraft:wheat-seeds", 1, 3)));
		return defaults;
	}

	private static JsonObject buildCropDefaults(
		String cropId,
		double growthMinecraftDays,
		ConditionDefault conditions,
		YieldDefault... yields
	) {
		String normalizedCropId = normalizeRegistryId(cropId);
		JSONFormatManager.ObjectBuilder root = JSONFormatManager.object()
			.put(FIELD_CROP_ID, MadokuJSONManager.normalizeRegistryIdentifierForJson(normalizedCropId))
			.put(FIELD_GROWTH_TIME, Math.max(0.25d, growthMinecraftDays));

		root.object(FIELD_YIELD_ID, yieldGroup -> {
			if (yields == null) return;
			for (YieldDefault yield : yields) {
				if (yield == null) continue;
				String yieldId = normalizeRegistryId(yield.id());
				if (yieldId.isBlank()) continue;
				yieldGroup.object(yieldId, values -> values
					.put(FIELD_YIELD_MINIMUM_AMOUNT, Math.max(1, yield.minimumAmount()))
					.put(FIELD_YIELD_MAXIMUM_AMOUNT, Math.max(Math.max(1, yield.minimumAmount()), yield.maximumAmount())));
			}
		});

		ConditionDefault safe = conditions == null ? new ConditionDefault(40, 60, 40, 60) : conditions;
		root.object(FIELD_GROWING_CONDITIONS, growing -> {
			growing.object(FIELD_IDEAL_TEMPERATURE, temperature -> temperature
				.put(FIELD_MINIMUM_TEMPERATURE, safe.minimumTemperature())
				.put(FIELD_MAXIMUM_TEMPERATURE, safe.maximumTemperature()));
			growing.object(FIELD_IDEAL_HUMIDITY, humidity -> humidity
				.put(FIELD_MINIMUM_HUMIDITY, safe.minimumHumidity())
				.put(FIELD_MAXIMUM_HUMIDITY, safe.maximumHumidity()));
		});
		return root.build();
	}

	private static String normalizeRegistryId(String value) {
		return MadokuJSONManager.normalizeRegistryIdentifierForLookup(value);
	}

	private record YieldDefault(String id, int minimumAmount, int maximumAmount) { }
	private record ConditionDefault(double minimumTemperature, double maximumTemperature, double minimumHumidity, double maximumHumidity) { }
}

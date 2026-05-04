package madoku.craft.season;

import com.google.gson.JsonObject;

import java.util.Locale;

public final class MadokuSeasonConfig {
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_BIOME_OVERRIDES_ENABLED = "biome-overrides-enabled";
	public static final String FIELD_COLD_TEMPERATURE_THRESHOLD = "cold-temperature-threshold";
	public static final String FIELD_HOT_TEMPERATURE_THRESHOLD = "hot-temperature-threshold";

	public static final String FIELD_BIOME_ID = "biome-id";
	public static final String FIELD_DEFAULT_CLASSIFICATION = "default-classification";
	public static final String FIELD_CLASSIFICATION = "classification";
	public static final String FIELD_BIOME_TEMPERATURE = "biome-temperature";
	public static final String FIELD_BIOME_PRECIPITATION = "biome-precipitation";

	public static final int DEFAULT_SEASON_LENGTH_DAYS = 28;
	public static final int DEFAULT_DAYS_PER_WEEK = 7;
	public static final long DEFAULT_SEASON_PROCESS_INTERVAL_TICKS = 5L;
	public static final double DEFAULT_COLD_TEMPERATURE_THRESHOLD = 0.2d;
	public static final double DEFAULT_HOT_TEMPERATURE_THRESHOLD = 1.0d;

	private MadokuSeasonConfig() {
	}

	public static JsonObject buildBiomeDefaults(
		String biomeId,
		String defaultClassification,
		String classification,
		double temperature,
		String precipitation
	) {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_BIOME_ID, biomeId == null ? "" : biomeId);
		root.addProperty(FIELD_DEFAULT_CLASSIFICATION, normalizeClassification(defaultClassification));
		root.addProperty(FIELD_CLASSIFICATION, normalizeClassification(classification));
		root.addProperty(FIELD_BIOME_TEMPERATURE, temperature);
		root.addProperty(FIELD_BIOME_PRECIPITATION, normalizeKey(precipitation));
		return root;
	}

	public static String normalizeClassification(String value) {
		String normalized = normalizeKey(value);
		return normalized.isEmpty() ? "temperate" : normalized;
	}

	public static String normalizeKey(String value) {
		if (value == null) {
			return "";
		}

		String trimmed = value.trim().toLowerCase(Locale.ROOT);
		StringBuilder builder = new StringBuilder(trimmed.length());
		for (int index = 0; index < trimmed.length(); index++) {
			char character = trimmed.charAt(index);
			if ((character >= 'a' && character <= 'z')
				|| (character >= '0' && character <= '9')
				|| character == '-'
				|| character == '.') {
				builder.append(character);
			} else {
				builder.append('-');
			}
		}
		return builder.toString();
	}
}

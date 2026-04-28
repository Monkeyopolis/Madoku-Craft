package madoku.craft.rarity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class MadokuRarityConfig {
	private static final String FIELD_ENABLED = "enabled";
	private static final String FIELD_COMMON_CHANCE_WEIGHT = "common-chance-weight";
	private static final String FIELD_RARE_CHANCE_WEIGHT = "rare-chance-weight";
	private static final String FIELD_EPIC_CHANCE_WEIGHT = "epic-chance-weight";
	private static final String FIELD_MYTHIC_CHANCE_WEIGHT = "mythic-chance-weight";
	private static final String FIELD_RARE_STAT_BUFF_PERCENT = "rare-stat-buff-percent";
	private static final String FIELD_EPIC_STAT_BUFF_PERCENT = "epic-stat-buff-percent";
	private static final String FIELD_MYTHIC_STAT_BUFF_PERCENT = "mythic-stat-buff-percent";

	private static final double DEFAULT_COMMON_CHANCE_WEIGHT = 67.0;
	private static final double DEFAULT_RARE_CHANCE_WEIGHT = 24.0;
	private static final double DEFAULT_EPIC_CHANCE_WEIGHT = 8.0;
	private static final double DEFAULT_MYTHIC_CHANCE_WEIGHT = 1.0;
	private static final double DEFAULT_RARE_STAT_BUFF_PERCENT = 25.0;
	private static final double DEFAULT_EPIC_STAT_BUFF_PERCENT = 50.0;
	private static final double DEFAULT_MYTHIC_STAT_BUFF_PERCENT = 100.0;

	public boolean enabled = true;
	public double commonChanceWeight = DEFAULT_COMMON_CHANCE_WEIGHT;
	public double rareChanceWeight = DEFAULT_RARE_CHANCE_WEIGHT;
	public double epicChanceWeight = DEFAULT_EPIC_CHANCE_WEIGHT;
	public double mythicChanceWeight = DEFAULT_MYTHIC_CHANCE_WEIGHT;
	public double rareStatBuffPercent = DEFAULT_RARE_STAT_BUFF_PERCENT;
	public double epicStatBuffPercent = DEFAULT_EPIC_STAT_BUFF_PERCENT;
	public double mythicStatBuffPercent = DEFAULT_MYTHIC_STAT_BUFF_PERCENT;

	public boolean update(JsonObject root) {
		boolean changed = false;

		enabled = readBoolean(root, FIELD_ENABLED, true);
		changed |= setBoolean(root, FIELD_ENABLED, enabled);

		commonChanceWeight = sanitizeNonNegative(
			readDouble(root, FIELD_COMMON_CHANCE_WEIGHT, DEFAULT_COMMON_CHANCE_WEIGHT),
			DEFAULT_COMMON_CHANCE_WEIGHT
		);
		rareChanceWeight = sanitizeNonNegative(
			readDouble(root, FIELD_RARE_CHANCE_WEIGHT, DEFAULT_RARE_CHANCE_WEIGHT),
			DEFAULT_RARE_CHANCE_WEIGHT
		);
		epicChanceWeight = sanitizeNonNegative(
			readDouble(root, FIELD_EPIC_CHANCE_WEIGHT, DEFAULT_EPIC_CHANCE_WEIGHT),
			DEFAULT_EPIC_CHANCE_WEIGHT
		);
		mythicChanceWeight = sanitizeNonNegative(
			readDouble(root, FIELD_MYTHIC_CHANCE_WEIGHT, DEFAULT_MYTHIC_CHANCE_WEIGHT),
			DEFAULT_MYTHIC_CHANCE_WEIGHT
		);
		rareStatBuffPercent = sanitizeNonNegative(
			readDouble(root, FIELD_RARE_STAT_BUFF_PERCENT, DEFAULT_RARE_STAT_BUFF_PERCENT),
			DEFAULT_RARE_STAT_BUFF_PERCENT
		);
		epicStatBuffPercent = sanitizeNonNegative(
			readDouble(root, FIELD_EPIC_STAT_BUFF_PERCENT, DEFAULT_EPIC_STAT_BUFF_PERCENT),
			DEFAULT_EPIC_STAT_BUFF_PERCENT
		);
		mythicStatBuffPercent = sanitizeNonNegative(
			readDouble(root, FIELD_MYTHIC_STAT_BUFF_PERCENT, DEFAULT_MYTHIC_STAT_BUFF_PERCENT),
			DEFAULT_MYTHIC_STAT_BUFF_PERCENT
		);

		changed |= setDouble(root, FIELD_COMMON_CHANCE_WEIGHT, commonChanceWeight);
		changed |= setDouble(root, FIELD_RARE_CHANCE_WEIGHT, rareChanceWeight);
		changed |= setDouble(root, FIELD_EPIC_CHANCE_WEIGHT, epicChanceWeight);
		changed |= setDouble(root, FIELD_MYTHIC_CHANCE_WEIGHT, mythicChanceWeight);
		changed |= setDouble(root, FIELD_RARE_STAT_BUFF_PERCENT, rareStatBuffPercent);
		changed |= setDouble(root, FIELD_EPIC_STAT_BUFF_PERCENT, epicStatBuffPercent);
		changed |= setDouble(root, FIELD_MYTHIC_STAT_BUFF_PERCENT, mythicStatBuffPercent);

		return changed;
	}

	public static JsonObject buildDefaults() {
		JsonObject defaults = new JsonObject();
		defaults.addProperty(FIELD_ENABLED, true);
		defaults.addProperty(FIELD_COMMON_CHANCE_WEIGHT, DEFAULT_COMMON_CHANCE_WEIGHT);
		defaults.addProperty(FIELD_RARE_CHANCE_WEIGHT, DEFAULT_RARE_CHANCE_WEIGHT);
		defaults.addProperty(FIELD_EPIC_CHANCE_WEIGHT, DEFAULT_EPIC_CHANCE_WEIGHT);
		defaults.addProperty(FIELD_MYTHIC_CHANCE_WEIGHT, DEFAULT_MYTHIC_CHANCE_WEIGHT);
		defaults.addProperty(FIELD_RARE_STAT_BUFF_PERCENT, DEFAULT_RARE_STAT_BUFF_PERCENT);
		defaults.addProperty(FIELD_EPIC_STAT_BUFF_PERCENT, DEFAULT_EPIC_STAT_BUFF_PERCENT);
		defaults.addProperty(FIELD_MYTHIC_STAT_BUFF_PERCENT, DEFAULT_MYTHIC_STAT_BUFF_PERCENT);
		return defaults;
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return element.getAsBoolean();
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			double value = element.getAsDouble();
			return Double.isFinite(value) ? value : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static boolean setBoolean(JsonObject root, String key, boolean value) {
		JsonElement element = root.get(key);
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
			if (element.getAsBoolean() == value) {
				return false;
			}
		}
		root.addProperty(key, value);
		return true;
	}

	private static boolean setDouble(JsonObject root, String key, double value) {
		JsonElement element = root.get(key);
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
			if (Double.compare(element.getAsDouble(), value) == 0) {
				return false;
			}
		}
		root.addProperty(key, value);
		return true;
	}

	private static double sanitizeNonNegative(double value, double fallback) {
		if (!Double.isFinite(value) || value < 0.0D) {
			return fallback;
		}
		return value;
	}
}

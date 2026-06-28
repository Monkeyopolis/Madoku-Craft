package madoku.craft.armor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.attributes.AttributesConfigManager;
import madoku.craft.config.JsonFormatBuilder;
import madoku.craft.config.JsonStaticSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class ArmorConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(ArmorConfigManager.class);

	private static final boolean DEFAULT_ENABLED = true;
	private static final int DEFAULT_ARMOR_POINTS_CLAMP_LIMIT = 100;
	private static final int DEFAULT_ARMOR_TOUGHNESS_POINTS_CLAMP_LIMIT = 100;
	private static final double DEFAULT_FALL_DAMAGE_ARMOR_EFFECTIVENESS = 0.5d;

	private static final String ARMOR_CONFIG_DIRECTORY_NAME = "madoku-armor";
	private static final String ARMOR_CONFIG_FILE_NAME = "madoku-armor";

	private ArmorConfigManager() {
	}

	static Settings loadSettings(boolean systemEnabled) {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path configFile = AttributesConfigManager.prepareSystemConfigFile(
				ARMOR_CONFIG_DIRECTORY_NAME,
				ARMOR_CONFIG_FILE_NAME
			);
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(configFile, defaults);
			Settings configured = Settings.fromJson(normalized);
			JsonStaticSystem.writeManagedFile(configFile, configured.toConfigJson(), defaults);
			return configured.withEnabled(systemEnabled);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load MadokuArmorManager config; using defaults.", exception);
			return fallback.withEnabled(systemEnabled);
		}
	}

	static final class Settings {
		final boolean enabled;
		final int armorPointsClampLimit;
		final int armorToughnessPointsClampLimit;
		final double fallDamageArmorEffectiveness;

		private Settings(
			boolean enabled,
			int armorPointsClampLimit,
			int armorToughnessPointsClampLimit,
			double fallDamageArmorEffectiveness
		) {
			this.enabled = enabled;
			this.armorPointsClampLimit = armorPointsClampLimit;
			this.armorToughnessPointsClampLimit = armorToughnessPointsClampLimit;
			this.fallDamageArmorEffectiveness = fallDamageArmorEffectiveness;
		}

		static Settings defaults() {
			return new Settings(
				DEFAULT_ENABLED,
				DEFAULT_ARMOR_POINTS_CLAMP_LIMIT,
				DEFAULT_ARMOR_TOUGHNESS_POINTS_CLAMP_LIMIT,
				DEFAULT_FALL_DAMAGE_ARMOR_EFFECTIVENESS
			);
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();

			boolean enabled = getBoolean(source, "enabled", defaults.enabled);
			long maxArmorPointsRaw = getLong(source, "max-armor-points", Long.MIN_VALUE);
			if (maxArmorPointsRaw == Long.MIN_VALUE) {
				maxArmorPointsRaw = getLong(source, "armor-points-clamp-limit", defaults.armorPointsClampLimit);
			}
			int armorPointsClampLimit = (int) clampLong(maxArmorPointsRaw, 0L, 100000L);
			long maxArmorToughnessPointsRaw = getLong(source, "max-armor-toughness-points", Long.MIN_VALUE);
			if (maxArmorToughnessPointsRaw == Long.MIN_VALUE) {
				maxArmorToughnessPointsRaw = getLong(
					source,
					"armor-toughness-points-clamp-limit",
					defaults.armorToughnessPointsClampLimit
				);
			}
			int armorToughnessPointsClampLimit = (int) clampLong(maxArmorToughnessPointsRaw, 0L, 100000L);
			double fallDamageArmorReductionRaw = getDouble(source, "fall-damage-armor-reduction", Double.NaN);
			if (Double.isNaN(fallDamageArmorReductionRaw)) {
				fallDamageArmorReductionRaw = getDouble(
					source,
					"fall-damage-armor-effectiveness",
					defaults.fallDamageArmorEffectiveness
				);
			}
			double fallDamageArmorEffectiveness = clampDouble(fallDamageArmorReductionRaw, 0.0d, 1.0d);

			return new Settings(
				enabled,
				armorPointsClampLimit,
				armorToughnessPointsClampLimit,
				fallDamageArmorEffectiveness
			);
		}

		JsonObject toConfigJson() {
			return JsonFormatBuilder.object()
				.put("enabled", enabled)
				.put("max-armor-points", armorPointsClampLimit)
				.put("max-armor-toughness-points", armorToughnessPointsClampLimit)
				.put("fall-damage-armor-reduction", fallDamageArmorEffectiveness)
				.build();
		}

		Settings withEnabled(boolean attributesEnabled) {
			return new Settings(
				attributesEnabled && enabled,
				armorPointsClampLimit,
				armorToughnessPointsClampLimit,
				fallDamageArmorEffectiveness
			);
		}

		private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
			if (object == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = object.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
				return fallback;
			}
			try {
				return element.getAsBoolean();
			} catch (RuntimeException exception) {
				return fallback;
			}
		}

		private static long getLong(JsonObject object, String key, long fallback) {
			if (object == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = object.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
				return fallback;
			}
			try {
				return element.getAsLong();
			} catch (RuntimeException exception) {
				return fallback;
			}
		}

		private static double getDouble(JsonObject object, String key, double fallback) {
			if (object == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = object.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
				return fallback;
			}
			try {
				return element.getAsDouble();
			} catch (RuntimeException exception) {
				return fallback;
			}
		}

		private static long clampLong(long value, long min, long max) {
			return Math.max(min, Math.min(max, value));
		}

		private static double clampDouble(double value, double min, double max) {
			return Math.max(min, Math.min(max, value));
		}
	}
}


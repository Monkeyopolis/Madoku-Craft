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
import java.util.Locale;

public final class ArmorConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(ArmorConfigManager.class);

	private static final boolean DEFAULT_ENABLED = true;
	private static final double DEFAULT_STARTING_ARMOR_POINTS = 0.0d;
	private static final double DEFAULT_MAX_ARMOR_POINTS = 100.0d;
	private static final DamageReductionType DEFAULT_ARMOR_POINTS_REDUCTION_TYPE = DamageReductionType.FLAT;
	private static final double DEFAULT_ARMOR_POINTS_REDUCTION_VALUE = 0.05d;

	private static final double DEFAULT_STARTING_ARMOR_TOUGHNESS_POINTS = 0.0d;
	private static final double DEFAULT_MAX_ARMOR_TOUGHNESS_POINTS = 100.0d;
	private static final DamageReductionType DEFAULT_ARMOR_TOUGHNESS_REDUCTION_TYPE = DamageReductionType.PERCENTAGE;
	private static final double DEFAULT_ARMOR_TOUGHNESS_REDUCTION_VALUE = 0.01d;

	private static final double DEFAULT_FALL_DAMAGE_REDUCTION = 0.5d;

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
		final MainSettings main;
		final ArmorPointsSettings armorPoints;
		final ArmorToughnessPointsSettings armorToughnessPoints;

		private Settings(
			boolean enabled,
			MainSettings main,
			ArmorPointsSettings armorPoints,
			ArmorToughnessPointsSettings armorToughnessPoints
		) {
			this.enabled = enabled;
			this.main = main;
			this.armorPoints = armorPoints;
			this.armorToughnessPoints = armorToughnessPoints;
		}

		static Settings defaults() {
			return new Settings(
				DEFAULT_ENABLED,
				MainSettings.defaults(),
				ArmorPointsSettings.defaults(),
				ArmorToughnessPointsSettings.defaults()
			);
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();

			boolean enabled = getBoolean(source, "enabled", defaults.enabled);
			MainSettings main = MainSettings.fromJson(readObject(source, "main"));
			ArmorPointsSettings armorPoints = ArmorPointsSettings.fromJson(readObject(source, "armor-points"));
			ArmorToughnessPointsSettings armorToughnessPoints = ArmorToughnessPointsSettings.fromJson(
				readObject(source, "armor-toughness-points")
			);

			return new Settings(enabled, main, armorPoints, armorToughnessPoints);
		}

		JsonObject toConfigJson() {
			return JsonFormatBuilder.object()
				.put("enabled", enabled)
				.object("main", group -> group.put("fall-damage-reduction", main.fallDamageReduction))
				.object("armor-points", group -> {
					group.put("starting-points", armorPoints.startingArmor);
					group.put("max-points", armorPoints.maxPoints);
					group.object("damage-reduction", damageReduction -> {
						damageReduction.put("type", armorPoints.damageReduction.type.configValue);
						damageReduction.put("value", armorPoints.damageReduction.value);
					});
				})
				.object("armor-toughness-points", group -> {
					group.put("starting-points", armorToughnessPoints.startingArmorToughness);
					group.put("max-points", armorToughnessPoints.maxPoints);
					group.object("damage-reduction", damageReduction -> {
						damageReduction.put("type", armorToughnessPoints.damageReduction.type.configValue);
						damageReduction.put("value", armorToughnessPoints.damageReduction.value);
					});
				})
				.build();
		}

		Settings withEnabled(boolean attributesEnabled) {
			return new Settings(attributesEnabled && enabled, main, armorPoints, armorToughnessPoints);
		}

		private static JsonObject readObject(JsonObject object, String key) {
			if (object == null || key == null || key.isBlank()) {
				return new JsonObject();
			}
			JsonElement element = object.get(key);
			if (element == null || !element.isJsonObject()) {
				return new JsonObject();
			}
			return element.getAsJsonObject();
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

		private static String getString(JsonObject object, String key, String fallback) {
			if (object == null || key == null || key.isBlank()) {
				return fallback;
			}
			JsonElement element = object.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				return fallback;
			}
			try {
				return element.getAsString();
			} catch (RuntimeException exception) {
				return fallback;
			}
		}

		private static double clampDouble(double value, double min, double max) {
			return Math.max(min, Math.min(max, value));
		}

		private static DamageReductionType readDamageReductionType(JsonObject source, DamageReductionType fallback) {
			String normalized = getString(source, "type", fallback.configValue).trim().toLowerCase(Locale.ROOT);
			return DamageReductionType.fromConfigValue(normalized, fallback);
		}

		private static double readDamageReductionValue(JsonObject source, DamageReductionType type, double fallback) {
			double value = getDouble(source, "value", fallback);
			if (type == DamageReductionType.PERCENTAGE) {
				return clampDouble(value, 0.0d, 1.0d);
			}
			return Math.max(0.0d, value);
		}
	}

	static final class MainSettings {
		final double fallDamageReduction;

		private MainSettings(double fallDamageReduction) {
			this.fallDamageReduction = fallDamageReduction;
		}

		static MainSettings defaults() {
			return new MainSettings(DEFAULT_FALL_DAMAGE_REDUCTION);
		}

		static MainSettings fromJson(JsonObject source) {
			MainSettings defaults = defaults();
			double fallDamageReduction = Settings.clampDouble(
				Settings.getDouble(source, "fall-damage-reduction", defaults.fallDamageReduction),
				0.0d,
				1.0d
			);
			return new MainSettings(fallDamageReduction);
		}
	}

	static final class ArmorPointsSettings {
		final double startingArmor;
		final double maxPoints;
		final DamageReduction damageReduction;

		private ArmorPointsSettings(double startingArmor, double maxPoints, DamageReduction damageReduction) {
			this.startingArmor = startingArmor;
			this.maxPoints = maxPoints;
			this.damageReduction = damageReduction;
		}

		static ArmorPointsSettings defaults() {
			return new ArmorPointsSettings(
				DEFAULT_STARTING_ARMOR_POINTS,
				DEFAULT_MAX_ARMOR_POINTS,
				DamageReduction.defaults(DEFAULT_ARMOR_POINTS_REDUCTION_TYPE, DEFAULT_ARMOR_POINTS_REDUCTION_VALUE)
			);
		}

		static ArmorPointsSettings fromJson(JsonObject source) {
			ArmorPointsSettings defaults = defaults();
			double startingArmor = Settings.clampDouble(
				Settings.getDouble(source, "starting-points", defaults.startingArmor),
				0.0d,
				100000.0d
			);
			double maxPoints = Settings.clampDouble(
				Settings.getDouble(source, "max-points", defaults.maxPoints),
				startingArmor,
				100000.0d
			);
			JsonObject damageReductionRoot = Settings.readObject(source, "damage-reduction");
			DamageReduction damageReduction = DamageReduction.fromJson(
				damageReductionRoot,
				DEFAULT_ARMOR_POINTS_REDUCTION_TYPE,
				DEFAULT_ARMOR_POINTS_REDUCTION_VALUE
			);
			return new ArmorPointsSettings(startingArmor, maxPoints, damageReduction);
		}
	}

	static final class ArmorToughnessPointsSettings {
		final double startingArmorToughness;
		final double maxPoints;
		final DamageReduction damageReduction;

		private ArmorToughnessPointsSettings(
			double startingArmorToughness,
			double maxPoints,
			DamageReduction damageReduction
		) {
			this.startingArmorToughness = startingArmorToughness;
			this.maxPoints = maxPoints;
			this.damageReduction = damageReduction;
		}

		static ArmorToughnessPointsSettings defaults() {
			return new ArmorToughnessPointsSettings(
				DEFAULT_STARTING_ARMOR_TOUGHNESS_POINTS,
				DEFAULT_MAX_ARMOR_TOUGHNESS_POINTS,
				DamageReduction.defaults(DEFAULT_ARMOR_TOUGHNESS_REDUCTION_TYPE, DEFAULT_ARMOR_TOUGHNESS_REDUCTION_VALUE)
			);
		}

		static ArmorToughnessPointsSettings fromJson(JsonObject source) {
			ArmorToughnessPointsSettings defaults = defaults();
			double startingArmorToughness = Settings.clampDouble(
				Settings.getDouble(source, "starting-points", defaults.startingArmorToughness),
				0.0d,
				100000.0d
			);
			double maxPoints = Settings.clampDouble(
				Settings.getDouble(source, "max-points", defaults.maxPoints),
				startingArmorToughness,
				100000.0d
			);
			JsonObject damageReductionRoot = Settings.readObject(source, "damage-reduction");
			DamageReduction damageReduction = DamageReduction.fromJson(
				damageReductionRoot,
				DEFAULT_ARMOR_TOUGHNESS_REDUCTION_TYPE,
				DEFAULT_ARMOR_TOUGHNESS_REDUCTION_VALUE
			);
			return new ArmorToughnessPointsSettings(startingArmorToughness, maxPoints, damageReduction);
		}
	}

	static final class DamageReduction {
		final DamageReductionType type;
		final double value;

		private DamageReduction(DamageReductionType type, double value) {
			this.type = type;
			this.value = value;
		}

		static DamageReduction defaults(DamageReductionType type, double value) {
			return new DamageReduction(type, value);
		}

		static DamageReduction fromJson(JsonObject source, DamageReductionType fallbackType, double fallbackValue) {
			DamageReductionType type = Settings.readDamageReductionType(source, fallbackType);
			double value = Settings.readDamageReductionValue(source, type, fallbackValue);
			return new DamageReduction(type, value);
		}
	}

	enum DamageReductionType {
		PERCENTAGE("percentage"),
		FLAT("flat");

		final String configValue;

		DamageReductionType(String configValue) {
			this.configValue = configValue;
		}

		static DamageReductionType fromConfigValue(String value, DamageReductionType fallback) {
			if (value == null) {
				return fallback;
			}
			return switch (value) {
				case "percentage" -> PERCENTAGE;
				case "flat" -> FLAT;
				default -> fallback;
			};
		}
	}
}

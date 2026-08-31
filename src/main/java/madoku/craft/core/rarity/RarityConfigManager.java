package madoku.craft.core.rarity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.core.MadokuCoreManager;
import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.json.MadokuJSONManager;
import madoku.craft.core.rarity.RarityTierManager.Tier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public final class RarityConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(RarityConfigManager.class);
	private static final String RARITY_CONFIG_FOLDER_NAME = MadokuCoreManager.CORE_FOLDER_NAME + "/madoku-rarity";
	private static final String RARITY_CONFIG_FILE_NAME = "madoku-rarity";
	private static final String FIELD_ENABLED = "enabled";
	private static final String FIELD_RARITY = "rarity";
	private static final String FIELD_WEIGHT = "weight";
	private static final String FIELD_WEIGHT_ADJUSTMENT = "weight-adjustment";
	private static final String FIELD_USE_MADOKU_LUCK = "use-madoku-luck";

	private static final Map<Tier, RarityDefaults> DEFAULTS = Map.of(
		Tier.COMMON, new RarityDefaults(true, 669, 0.25D),
		Tier.RARE, new RarityDefaults(true, 240, 0.5D),
		Tier.EPIC, new RarityDefaults(true, 80, 1.25D),
		Tier.LEGENDARY, new RarityDefaults(true, 10, 1.0D),
		Tier.MYTHIC, new RarityDefaults(true, 1, 0.75D)
	);

	private static volatile Settings settings = Settings.defaults();

	private RarityConfigManager() {
	}

	public static void initialize() {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();
		try {
			Path directory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(RARITY_CONFIG_FOLDER_NAME);
			Path configFile = directory.resolve(RARITY_CONFIG_FILE_NAME + ".json");
			JsonObject normalized = JSONFormatManager.ensureManagedFile(configFile, defaults);
			Settings loaded = Settings.fromJson(normalized);
			JSONFormatManager.writeManagedFile(configFile, loaded.toConfigJson(), defaults);
			settings = loaded;
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load Madoku rarity config; using defaults.", exception);
		}
	}

	public static void reset() {
		settings = Settings.defaults();
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static boolean useMadokuLuck() {
		return settings.useMadokuLuck;
	}

	static RaritySettings settings(Tier tier) {
		if (tier == null) {
			return null;
		}
		return settings.rarities.get(tier);
	}

	static JsonObject buildDefaults() {
		return Settings.defaults().toConfigJson();
	}

	private record RarityDefaults(boolean enabled, int weight, double weightAdjustment) {
	}

	static final class Settings {
		final boolean enabled;
		final boolean useMadokuLuck;
		final EnumMap<Tier, RaritySettings> rarities;

		private Settings(boolean enabled, boolean useMadokuLuck, EnumMap<Tier, RaritySettings> rarities) {
			this.enabled = enabled;
			this.useMadokuLuck = useMadokuLuck;
			this.rarities = rarities;
		}

		static Settings defaults() {
			EnumMap<Tier, RaritySettings> rarities = new EnumMap<>(Tier.class);
			for (Tier tier : Tier.values()) {
				RarityDefaults defaults = DEFAULTS.get(tier);
				rarities.put(tier, new RaritySettings(defaults.enabled(), defaults.weight(), defaults.weightAdjustment()));
			}
			return new Settings(true, true, rarities);
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			JsonObject rarityRoot = readObject(source, FIELD_RARITY);
			EnumMap<Tier, RaritySettings> rarities = new EnumMap<>(Tier.class);
			for (Tier tier : Tier.values()) {
				RaritySettings base = defaults.rarities.get(tier);
				rarities.put(tier, RaritySettings.fromJson(readObject(rarityRoot, tier.id()), base));
			}
			return new Settings(
				getBoolean(source, FIELD_ENABLED, defaults.enabled),
				getBoolean(source, FIELD_USE_MADOKU_LUCK, defaults.useMadokuLuck),
				rarities
			);
		}

		JsonObject toConfigJson() {
			return JSONFormatManager.object()
				.put(FIELD_ENABLED, enabled)
				.object(FIELD_RARITY, rarity -> {
					for (Tier tier : Tier.values()) {
						RaritySettings entry = rarities.get(tier);
						rarity.object(tier.id(), child -> entry.toConfigJson(child));
					}
				})
				.put(FIELD_USE_MADOKU_LUCK, useMadokuLuck)
				.build();
		}
	}

	static final class RaritySettings {
		final boolean enabled;
		final int weight;
		final double weightAdjustment;

		private RaritySettings(boolean enabled, int weight, double weightAdjustment) {
			this.enabled = enabled;
			this.weight = weight;
			this.weightAdjustment = weightAdjustment;
		}

		static RaritySettings fromJson(JsonObject source, RaritySettings fallback) {
			RaritySettings base = fallback == null ? new RaritySettings(true, 1, 1.0D) : fallback;
			return new RaritySettings(
				getBoolean(source, FIELD_ENABLED, base.enabled),
				(int) Math.max(0L, Math.min(Integer.MAX_VALUE, getLong(source, FIELD_WEIGHT, base.weight))),
				clamp(getDouble(source, FIELD_WEIGHT_ADJUSTMENT, base.weightAdjustment), 0.0D, 1024.0D)
			);
		}

		void toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			builder.put(FIELD_ENABLED, enabled)
				.put(FIELD_WEIGHT, weight)
				.put(FIELD_WEIGHT_ADJUSTMENT, weightAdjustment);
		}
	}

	private static JsonObject readObject(JsonObject source, String key) {
		if (source == null || key == null || key.isBlank()) {
			return new JsonObject();
		}
		JsonElement element = source.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static boolean getBoolean(JsonObject source, String key, boolean fallback) {
		try {
			JsonElement element = source == null ? null : source.get(key);
			return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
				? element.getAsBoolean() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static long getLong(JsonObject source, String key, long fallback) {
		try {
			JsonElement element = source == null ? null : source.get(key);
			return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
				? element.getAsLong() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static double getDouble(JsonObject source, String key, double fallback) {
		try {
			JsonElement element = source == null ? null : source.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
				return fallback;
			}
			double value = element.getAsDouble();
			return Double.isFinite(value) ? value : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}

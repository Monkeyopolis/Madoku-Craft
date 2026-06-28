package madoku.craft.oxygen;

import com.google.gson.JsonObject;
import madoku.craft.attributes.AttributesConfigManager;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.JsonFormatBuilder;
import madoku.craft.config.JsonStaticSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class OxygenConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(OxygenConfigManager.class);

	private static final String OXYGEN_CONFIG_DIRECTORY_NAME = "madoku-oxygen";
	private static final String OXYGEN_CONFIG_FILE_NAME = "madoku-oxygen";
	private static final double DEFAULT_MAXIMUM_OXYGEN_GAIN_PER_EFFECT_LEVEL_FRACTION = 2.0d;
	private static final double DEFAULT_OXYGEN_GAIN_PER_EFFECT_LEVEL_FRACTION = 1.0d;
	private static final long DEFAULT_DROWNING_DAMAGE_INTERVAL_TICKS = 20L;
	private static final double DEFAULT_DROWNING_DAMAGE_AMOUNT = 1.0d;
	private static final long TICKS_PER_SECOND = Math.max(1L, MadokuTicks.TICKS_PER_SECOND);

	private OxygenConfigManager() {
	}

	static Settings loadSettings(boolean systemEnabled) {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path configFile = AttributesConfigManager.prepareSystemConfigFile(
				OXYGEN_CONFIG_DIRECTORY_NAME,
				OXYGEN_CONFIG_FILE_NAME
			);
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(configFile, defaults);
			Settings configured = Settings.fromJson(normalized);
			JsonStaticSystem.writeManagedFile(configFile, configured.toConfigJson(), defaults);
			return configured.withEnabled(systemEnabled);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load MadokuOxygenManager config; using defaults.", exception);
			return fallback.withEnabled(systemEnabled);
		}
	}

	static final class Settings {
		final boolean enabled;
		final long schedulerTickInterval;
		final int maximumOxygenTicks;
		final int oxygenDrainPerTick;
		final int oxygenRecoveryPerTick;
		final double maximumOxygenGainPerEffectLevelFraction;
		final double oxygenGainPerEffectLevelFraction;
		final long drowningDamageIntervalTicks;
		final double drowningDamageAmount;

		private Settings(
			boolean enabled,
			long schedulerTickInterval,
			int maximumOxygenTicks,
			int oxygenDrainPerTick,
			int oxygenRecoveryPerTick,
			double maximumOxygenGainPerEffectLevelFraction,
			double oxygenGainPerEffectLevelFraction,
			long drowningDamageIntervalTicks,
			double drowningDamageAmount
		) {
			this.enabled = enabled;
			this.schedulerTickInterval = schedulerTickInterval;
			this.maximumOxygenTicks = maximumOxygenTicks;
			this.oxygenDrainPerTick = oxygenDrainPerTick;
			this.oxygenRecoveryPerTick = oxygenRecoveryPerTick;
			this.maximumOxygenGainPerEffectLevelFraction = maximumOxygenGainPerEffectLevelFraction;
			this.oxygenGainPerEffectLevelFraction = oxygenGainPerEffectLevelFraction;
			this.drowningDamageIntervalTicks = drowningDamageIntervalTicks;
			this.drowningDamageAmount = drowningDamageAmount;
		}

		static Settings defaults() {
			return new Settings(
				true,
				1L,
				(int) (20L * TICKS_PER_SECOND),
				1,
				5,
				DEFAULT_MAXIMUM_OXYGEN_GAIN_PER_EFFECT_LEVEL_FRACTION,
				DEFAULT_OXYGEN_GAIN_PER_EFFECT_LEVEL_FRACTION,
				DEFAULT_DROWNING_DAMAGE_INTERVAL_TICKS,
				DEFAULT_DROWNING_DAMAGE_AMOUNT
			);
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();

			boolean enabled = getBoolean(source, "enabled", defaults.enabled);
			long schedulerTickInterval = defaults.schedulerTickInterval;
			int maximumOxygenTicks = (int) clampLong(
				getLong(source, "maximum-oxygen", defaults.maximumOxygenTicks),
				1L,
				20L * 60L * 60L
			);
			int oxygenDrainPerTick = defaults.oxygenDrainPerTick;
			int oxygenRecoveryPerTick = defaults.oxygenRecoveryPerTick;
			double oxygenGainPerEffectLevelFraction = defaults.oxygenGainPerEffectLevelFraction;
			double maximumOxygenGainPerEffectLevelFraction = defaults.maximumOxygenGainPerEffectLevelFraction;
			long drowningDamageIntervalTicks = defaults.drowningDamageIntervalTicks;
			double drowningDamageAmount = defaults.drowningDamageAmount;

			return new Settings(
				enabled,
				schedulerTickInterval,
				maximumOxygenTicks,
				oxygenDrainPerTick,
				oxygenRecoveryPerTick,
				maximumOxygenGainPerEffectLevelFraction,
				oxygenGainPerEffectLevelFraction,
				drowningDamageIntervalTicks,
				drowningDamageAmount
			);
		}

		JsonObject toConfigJson() {
			return JsonFormatBuilder.object()
				.put("enabled", enabled)
				.put("maximum-oxygen", maximumOxygenTicks)
				.build();
		}

		Settings withEnabled(boolean attributesEnabled) {
			return new Settings(
				attributesEnabled && enabled,
				schedulerTickInterval,
				maximumOxygenTicks,
				oxygenDrainPerTick,
				oxygenRecoveryPerTick,
				maximumOxygenGainPerEffectLevelFraction,
				oxygenGainPerEffectLevelFraction,
				drowningDamageIntervalTicks,
				drowningDamageAmount
			);
		}

		private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
			if (object == null || key == null || key.isBlank()) {
				return fallback;
			}
			var element = object.get(key);
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
			var element = object.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
				return fallback;
			}
			try {
				return element.getAsLong();
			} catch (RuntimeException exception) {
				return fallback;
			}
		}

		private static long clampLong(long value, long min, long max) {
			return Math.max(min, Math.min(max, value));
		}
	}
}


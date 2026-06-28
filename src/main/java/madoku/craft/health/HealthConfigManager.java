package madoku.craft.health;

import com.google.gson.JsonObject;
import madoku.craft.attributes.AttributesConfigManager;
import madoku.craft.config.JsonFormatBuilder;
import madoku.craft.config.JsonStaticSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class HealthConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(HealthConfigManager.class);

	private static final String HEALTH_CONFIG_DIRECTORY_NAME = "madoku-health";
	private static final String HEALTH_CONFIG_FILE_NAME = "madoku-health";

	private HealthConfigManager() {
	}

	static Settings loadSettings(boolean systemEnabled) {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path configFile = AttributesConfigManager.prepareSystemConfigFile(
				HEALTH_CONFIG_DIRECTORY_NAME,
				HEALTH_CONFIG_FILE_NAME
			);
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(configFile, defaults);
			Settings configured = Settings.fromJson(normalized);
			JsonStaticSystem.writeManagedFile(configFile, configured.toConfigJson(), defaults);
			return configured.withEnabled(systemEnabled);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load MadokuHealthManager config; using defaults.", exception);
			return fallback.withEnabled(systemEnabled);
		}
	}

	static final class Settings {
		final boolean enabled;
		final long schedulerTickInterval;
		final int actionIntervalTicks;
		final double maximumHealth;
		final float hungerDrainRatio;
		final float hungerPenaltyRatio;
		final double healthPenaltyRatio;
		final long pendingIdleTimeoutTicks;

		private Settings(
			boolean enabled,
			long schedulerTickInterval,
			int actionIntervalTicks,
			double maximumHealth,
			float hungerDrainRatio,
			float hungerPenaltyRatio,
			double healthPenaltyRatio,
			long pendingIdleTimeoutTicks
		) {
			this.enabled = enabled;
			this.schedulerTickInterval = schedulerTickInterval;
			this.actionIntervalTicks = actionIntervalTicks;
			this.maximumHealth = maximumHealth;
			this.hungerDrainRatio = hungerDrainRatio;
			this.hungerPenaltyRatio = hungerPenaltyRatio;
			this.healthPenaltyRatio = healthPenaltyRatio;
			this.pendingIdleTimeoutTicks = pendingIdleTimeoutTicks;
		}

		static Settings defaults() {
			return new Settings(
				true,
				1L,
				10,
				20.0d,
				0.75f,
				0.25f,
				0.50d,
				1500L
			);
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();

			boolean enabled = getBoolean(source, "enabled", defaults.enabled);
			long schedulerTickInterval = defaults.schedulerTickInterval;
			int actionIntervalTicks = defaults.actionIntervalTicks;
			double maximumHealth = clampDouble(getDouble(source, "maximum-health", defaults.maximumHealth), 1.0d, 1024.0d);
			double hungerDrainRatioRaw = getDouble(source, "hunger-drain-ratio", Double.NaN);
			if (Double.isNaN(hungerDrainRatioRaw)) {
				hungerDrainRatioRaw = getDouble(source, "high-hunger-start-ratio", defaults.hungerDrainRatio);
			}
			float hungerDrainRatio = (float) clampDouble(hungerDrainRatioRaw, 0.0d, 1.0d);
			double hungerPenaltyRatioRaw = getDouble(source, "hunger-penalty-ratio", Double.NaN);
			if (Double.isNaN(hungerPenaltyRatioRaw)) {
				hungerPenaltyRatioRaw = getDouble(source, "low-hunger-threshold-ratio", defaults.hungerPenaltyRatio);
			}
			float hungerPenaltyRatio = (float) clampDouble(hungerPenaltyRatioRaw, 0.0d, 1.0d);
			double healthPenaltyRatioRaw = getDouble(source, "health-penalty-ratio", Double.NaN);
			if (Double.isNaN(healthPenaltyRatioRaw)) {
				healthPenaltyRatioRaw = getDouble(source, "minimum-max-health-multiplier", defaults.healthPenaltyRatio);
			}
			double healthPenaltyRatio = clampDouble(healthPenaltyRatioRaw, 0.10d, 1.0d);
			long pendingIdleTimeoutTicks = defaults.pendingIdleTimeoutTicks;

			return new Settings(
				enabled,
				schedulerTickInterval,
				actionIntervalTicks,
				maximumHealth,
				hungerDrainRatio,
				hungerPenaltyRatio,
				healthPenaltyRatio,
				pendingIdleTimeoutTicks
			);
		}

		JsonObject toConfigJson() {
			return JsonFormatBuilder.object()
				.put("enabled", enabled)
				.put("maximum-health", maximumHealth)
				.put("hunger-drain-ratio", hungerDrainRatio)
				.put("hunger-penalty-ratio", hungerPenaltyRatio)
				.put("health-penalty-ratio", healthPenaltyRatio)
				.build();
		}

		Settings withEnabled(boolean attributesEnabled) {
			return new Settings(
				attributesEnabled && enabled,
				schedulerTickInterval,
				actionIntervalTicks,
				maximumHealth,
				hungerDrainRatio,
				hungerPenaltyRatio,
				healthPenaltyRatio,
				pendingIdleTimeoutTicks
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

		private static double getDouble(JsonObject object, String key, double fallback) {
			if (object == null || key == null || key.isBlank()) {
				return fallback;
			}
			var element = object.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
				return fallback;
			}
			try {
				return element.getAsDouble();
			} catch (RuntimeException exception) {
				return fallback;
			}
		}

		private static double clampDouble(double value, double min, double max) {
			return Math.max(min, Math.min(max, value));
		}
	}
}


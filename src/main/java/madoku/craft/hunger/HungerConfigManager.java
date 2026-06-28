package madoku.craft.hunger;

import com.google.gson.JsonObject;
import madoku.craft.attributes.AttributesConfigManager;
import madoku.craft.config.JsonFormatBuilder;
import madoku.craft.config.JsonStaticSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class HungerConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(HungerConfigManager.class);

	private static final int MAX_CONFIG_HUNGER_POINTS = 8192;
	private static final int DEFAULT_TIME_GOAL_CLOCK_TICKS = 7200;
	private static final String HUNGER_CONFIG_DIRECTORY_NAME = "madoku-hunger";
	private static final String HUNGER_CONFIG_FILE_NAME = "madoku-hunger";

	private HungerConfigManager() {
	}

	static Settings loadSettings(boolean systemEnabled) {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path configFile = AttributesConfigManager.prepareSystemConfigFile(
				HUNGER_CONFIG_DIRECTORY_NAME,
				HUNGER_CONFIG_FILE_NAME
			);
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(configFile, defaults);
			Settings configured = Settings.fromJson(normalized);
			JsonStaticSystem.writeManagedFile(configFile, configured.toConfigJson(), defaults);
			return configured.withEnabled(systemEnabled);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load MadokuHungerManager config; using defaults.", exception);
			return fallback.withEnabled(systemEnabled);
		}
	}

	static final class Settings {
		final boolean enabled;
		final int maximumHungerPoints;
		final int pendingAllocationIntervalTicks;
		final long pendingIdleTimeoutTicks;
		final int blockBreakGoal;
		final double travelGoalDistance;
		final int timeGoalTicks;
		final double teleportDistanceThreshold;

		private Settings(
			boolean enabled,
			int maximumHungerPoints,
			int pendingAllocationIntervalTicks,
			long pendingIdleTimeoutTicks,
			int blockBreakGoal,
			double travelGoalDistance,
			int timeGoalTicks,
			double teleportDistanceThreshold
		) {
			this.enabled = enabled;
			this.maximumHungerPoints = maximumHungerPoints;
			this.pendingAllocationIntervalTicks = pendingAllocationIntervalTicks;
			this.pendingIdleTimeoutTicks = pendingIdleTimeoutTicks;
			this.blockBreakGoal = blockBreakGoal;
			this.travelGoalDistance = travelGoalDistance;
			this.timeGoalTicks = timeGoalTicks;
			this.teleportDistanceThreshold = teleportDistanceThreshold;
		}

		static Settings defaults() {
			return new Settings(
				true,
				30,
				10,
				1500L,
				128,
				150.0d,
				DEFAULT_TIME_GOAL_CLOCK_TICKS,
				16.0d
			);
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();

			boolean enabled = getBoolean(source, "enabled", defaults.enabled);
			int maximumHungerPoints = (int) clampLong(
				getLong(source, "maximum-hunger-points", defaults.maximumHungerPoints),
				1L,
				MAX_CONFIG_HUNGER_POINTS
			);
			int pendingAllocationIntervalTicks = defaults.pendingAllocationIntervalTicks;
			long pendingIdleTimeoutTicks = defaults.pendingIdleTimeoutTicks;
			int blockBreakGoal = (int) clampLong(getLong(source, "block-break-goal", defaults.blockBreakGoal), 1L, 100000L);
			double travelGoalDistance = clampDouble(getDouble(source, "travel-goal-distance", defaults.travelGoalDistance), 1.0d, 1000000.0d);
			int timeGoalTicks = (int) clampLong(getLong(source, "time-goal-ticks", defaults.timeGoalTicks), 1L, 20L * 60L * 60L * 24L);
			double teleportDistanceThreshold = clampDouble(
				getDouble(source, "teleport-distance-threshold", defaults.teleportDistanceThreshold),
				1.0d,
				1024.0d
			);

			return new Settings(
				enabled,
				maximumHungerPoints,
				pendingAllocationIntervalTicks,
				pendingIdleTimeoutTicks,
				blockBreakGoal,
				travelGoalDistance,
				timeGoalTicks,
				teleportDistanceThreshold
			);
		}

		JsonObject toConfigJson() {
			return JsonFormatBuilder.object()
				.put("enabled", enabled)
				.put("maximum-hunger-points", maximumHungerPoints)
				.put("block-break-goal", blockBreakGoal)
				.put("travel-goal-distance", travelGoalDistance)
				.put("time-goal-ticks", timeGoalTicks)
				.put("teleport-distance-threshold", teleportDistanceThreshold)
				.build();
		}

		Settings withEnabled(boolean attributesEnabled) {
			return new Settings(
				attributesEnabled && enabled,
				maximumHungerPoints,
				pendingAllocationIntervalTicks,
				pendingIdleTimeoutTicks,
				blockBreakGoal,
				travelGoalDistance,
				timeGoalTicks,
				teleportDistanceThreshold
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

		private static long clampLong(long value, long min, long max) {
			return Math.max(min, Math.min(max, value));
		}

		private static double clampDouble(double value, double min, double max) {
			return Math.max(min, Math.min(max, value));
		}
	}
}


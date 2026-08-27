package madoku.craft.core.season;

import com.google.gson.JsonObject;

import madoku.craft.core.data.DataSystemsManager;
import madoku.craft.core.data.DataWorldManager;
import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.scheduler.SchedulerAdaptiveIntervalManager;
import madoku.craft.core.time.MadokuTimeManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.WeatherData;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/** Owns the global Madoku seasonal weather condition and Vanilla weather state. */
public final class SeasonWeatherManager {
	private static final long TICKS_PER_MINUTE = MadokuTimeManager.TICKS_PER_SECOND * MadokuTimeManager.SECONDS_PER_MINUTE;
	private static final double NEUTRAL_HUMIDITY = 50.0D;
	private static final String DATA_SYSTEM_ID = "season-weather";
	private static final String ADAPTIVE_INTERVAL_SYSTEM_ID = "season-weather-manager";
	private static final String FIELD_CONDITION = "condition";
	private static final String FIELD_CONDITION_END = "condition-end-absolute-time";
	private static final String FIELD_NEXT_EVALUATION = "next-evaluation-absolute-time";
	private static final long MAX_ADAPTIVE_POLL_TICKS = 200L;
	private static final double[] HUMIDITY_ADJUSTMENT_LEVELS = {
		0.0D, 10.0D, 19.0D, 30.0D, 40.0D, 60.0D, 70.0D, 81.0D, 90.0D, 100.0D
	};
	private static final double[] HUMIDITY_ADJUSTMENT_VALUES = {
		0.75D, 0.75D, 0.50D, 0.25D, 0.0D, 0.0D, 0.25D, 0.50D, 0.75D, 0.75D
	};

	private static volatile MinecraftServer currentServer;
	private static volatile WeatherCondition currentCondition;
	private static volatile long nextEvaluationAbsoluteTime = -1L;
	private static volatile long conditionEndAbsoluteTime = -1L;
	private static volatile long lastObservedAbsoluteTime = -1L;
	private static volatile WeatherCondition lastAppliedCondition;
	private static volatile long nextAdaptivePollGameplayTick = -1L;
	private static final Map<ServerLevel, WeatherCondition> lastAppliedLevelConditions = new IdentityHashMap<>();

	private SeasonWeatherManager() { }

	public static void initialize() {
		DataSystemsManager.registerSystem(DATA_SYSTEM_ID);
		WeatherConfigManager.initialize();
	}

	public static void reset() {
		currentServer = null;
		currentCondition = null;
		nextEvaluationAbsoluteTime = -1L;
		conditionEndAbsoluteTime = -1L;
		lastObservedAbsoluteTime = -1L;
		lastAppliedCondition = null;
		nextAdaptivePollGameplayTick = -1L;
		lastAppliedLevelConditions.clear();
		SchedulerAdaptiveIntervalManager.clearSystem(ADAPTIVE_INTERVAL_SYSTEM_ID);
	}

	public static boolean isEnabled() {
		return SeasonConfigManager.getSettings().enabled() && WeatherConfigManager.isEnabled();
	}

	public static WeatherCondition getCurrentCondition() {
		return currentCondition;
	}

	public static boolean isPrecipitating(ServerLevel level) {
		return isEnabled() && currentCondition != null && currentCondition.precipitating();
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server == null || currentServer == server) return;

		currentServer = server;
		DataSystemsManager.registerSystem(DATA_SYSTEM_ID);
		currentCondition = null;
		conditionEndAbsoluteTime = -1L;
		lastAppliedCondition = null;
		nextAdaptivePollGameplayTick = -1L;
		lastAppliedLevelConditions.clear();
		long now = currentAbsoluteTime(server);
		lastObservedAbsoluteTime = now;
		nextEvaluationAbsoluteTime = safeAdd(now, resolveMinutesToTicks(WeatherConfigManager.getSettings().timeRateMinutes()));
		if (isEnabled()) restorePersistedState(server, now);

		if (isEnabled()) {
			applyCondition(server, currentCondition == null ? WeatherCondition.CLEAR : currentCondition, true);
		}
	}

	public static void onServerTick(MinecraftServer server) {
		if (server == null) return;
		if (!isEnabled()) {
			SchedulerAdaptiveIntervalManager.clearSystem(ADAPTIVE_INTERVAL_SYSTEM_ID);
			return;
		}
		if (currentServer != server) onServerStarted(server);

		long now = currentAbsoluteTime(server);
		long gameplayTick = Math.max(0L, MadokuTimeManager.getGameplayTicks());
		long adaptiveInterval = resolveAdaptivePollInterval(server);
		boolean deadlineReached = currentCondition != null
			? now >= conditionEndAbsoluteTime
			: now >= nextEvaluationAbsoluteTime;
		if (!deadlineReached
			&& nextAdaptivePollGameplayTick >= 0L
			&& gameplayTick < nextAdaptivePollGameplayTick) {
			return;
		}
		nextAdaptivePollGameplayTick = safeAdd(gameplayTick, adaptiveInterval);

		if (lastObservedAbsoluteTime >= 0L && now < lastObservedAbsoluteTime) {
			// A backwards world-time change should not leave the state waiting on a
			// deadline that belongs to the old time coordinate.
			if (currentCondition == null) {
				nextEvaluationAbsoluteTime = safeAdd(now, resolveMinutesToTicks(WeatherConfigManager.getSettings().timeRateMinutes()));
			} else {
				conditionEndAbsoluteTime = safeAdd(now, resolveMinutesToTicks(WeatherConfigManager.getSettings().timeRateMinutes()));
			}
		}
		lastObservedAbsoluteTime = now;

		if (currentCondition != null && now >= conditionEndAbsoluteTime) {
			currentCondition = null;
			conditionEndAbsoluteTime = -1L;
			nextEvaluationAbsoluteTime = safeAdd(now, resolveMinutesToTicks(WeatherConfigManager.getSettings().timeRateMinutes()));
			applyCondition(server, WeatherCondition.CLEAR, false);
			persistState();
		}

		if (currentCondition == null && now >= nextEvaluationAbsoluteTime) {
			Selection selection = selectCondition(server);
			WeatherCondition selected = selection.condition();
			long duration = resolveMinutesToTicks(selectDurationMinutes(server));
			currentCondition = selected;
			conditionEndAbsoluteTime = safeAdd(now, duration);
			nextEvaluationAbsoluteTime = -1L;
			applyCondition(server, selected, true);
			persistState();
		}

		applyCondition(server, currentCondition == null ? WeatherCondition.CLEAR : currentCondition, false);
	}

	private static void restorePersistedState(MinecraftServer server, long now) {
		JsonObject source = DataWorldManager.getSystemData(DATA_SYSTEM_ID);
		WeatherCondition persistedCondition = resolveCondition(readString(source, FIELD_CONDITION, ""));
		long persistedEnd = readLong(source, FIELD_CONDITION_END, -1L);
		long persistedNextEvaluation = readLong(source, FIELD_NEXT_EVALUATION, -1L);
		if (persistedCondition != null && persistedEnd > now) {
			currentCondition = persistedCondition;
			conditionEndAbsoluteTime = persistedEnd;
			nextEvaluationAbsoluteTime = -1L;
			return;
		}
		currentCondition = null;
		conditionEndAbsoluteTime = -1L;
		nextEvaluationAbsoluteTime = persistedNextEvaluation > now
			? persistedNextEvaluation
			: safeAdd(now, resolveMinutesToTicks(WeatherConfigManager.getSettings().timeRateMinutes()));
	}

	private static void persistState() {
		String condition = currentCondition == null ? "" : currentCondition.id();
		JsonObject state = JSONFormatManager.object()
			.put(FIELD_CONDITION, condition)
			.put(FIELD_CONDITION_END, conditionEndAbsoluteTime)
			.put(FIELD_NEXT_EVALUATION, nextEvaluationAbsoluteTime)
			.build();
		DataWorldManager.setSystemData(DATA_SYSTEM_ID, state);
	}

	private static long resolveAdaptivePollInterval(MinecraftServer server) {
		long configuredInterval = resolveMinutesToTicks(WeatherConfigManager.getSettings().timeRateMinutes());
		long maximum = Math.max(1L, Math.min(MAX_ADAPTIVE_POLL_TICKS, configuredInterval));
		return SchedulerAdaptiveIntervalManager.resolve(
			ADAPTIVE_INTERVAL_SYSTEM_ID,
			server,
			1L,
			maximum);
	}

	private static Selection selectCondition(MinecraftServer server) {
		WeatherConfigManager.Settings settings = WeatherConfigManager.getSettings();
		double averageHumidity = resolveAverageHumidity(server);
		double clearWeight = settings.clearWeight();
		double rainWeight = settings.rainWeight();
		double thunderstormWeight = settings.thunderstormWeight();
		double adjustment = resolveHumidityAdjustment(averageHumidity);
		if (averageHumidity < 50.0D) {
			rainWeight *= 1.0D - adjustment;
			thunderstormWeight *= 1.0D - adjustment;
			clearWeight *= 1.0D + adjustment;
		} else if (averageHumidity > 50.0D) {
			rainWeight *= 1.0D + adjustment;
			thunderstormWeight *= 1.0D + adjustment;
			clearWeight *= 1.0D - adjustment;
		}

		double totalWeight = clearWeight + rainWeight + thunderstormWeight;
		if (!Double.isFinite(totalWeight) || totalWeight <= 0.0D) {
			return new Selection(WeatherCondition.CLEAR, averageHumidity);
		}

		double roll = server.overworld().getRandom().nextDouble() * totalWeight;
		if (roll < clearWeight) return new Selection(WeatherCondition.CLEAR, averageHumidity);
		if (roll < clearWeight + rainWeight) return new Selection(WeatherCondition.RAIN, averageHumidity);
		return new Selection(WeatherCondition.THUNDERSTORM, averageHumidity);
	}

	private static double resolveHumidityAdjustment(double averageHumidity) {
		double humidity = Math.max(0.0D, Math.min(100.0D, averageHumidity));
		for (int index = 0; index < HUMIDITY_ADJUSTMENT_LEVELS.length - 1; index++) {
			double startHumidity = HUMIDITY_ADJUSTMENT_LEVELS[index];
			double endHumidity = HUMIDITY_ADJUSTMENT_LEVELS[index + 1];
			if (humidity <= endHumidity) {
				return interpolateHumidityAdjustment(
					HUMIDITY_ADJUSTMENT_VALUES[index],
					HUMIDITY_ADJUSTMENT_VALUES[index + 1],
					(humidity - startHumidity) / (endHumidity - startHumidity));
			}
		}
		return HUMIDITY_ADJUSTMENT_VALUES[HUMIDITY_ADJUSTMENT_VALUES.length - 1];
	}

	private static double interpolateHumidityAdjustment(double start, double end, double progress) {
		double clampedProgress = Math.max(0.0D, Math.min(1.0D, progress));
		double smoothProgress = clampedProgress * clampedProgress * (3.0D - 2.0D * clampedProgress);
		return start + (end - start) * smoothProgress;
	}

	private static double resolveAverageHumidity(MinecraftServer server) {
		if (server == null) return NEUTRAL_HUMIDITY;
		double totalHumidity = 0.0D;
		int playerCount = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player == null || player.level() == null) continue;
			SeasonBiomeClimateManager.Climate climate = MadokuSeasonManager.resolveBiomeClimate(player.level(), player.blockPosition());
			if (!Double.isFinite(climate.humidity())) continue;
			totalHumidity += climate.humidity();
			playerCount++;
		}
		return playerCount == 0 ? NEUTRAL_HUMIDITY : totalHumidity / playerCount;
	}

	private static int selectDurationMinutes(MinecraftServer server) {
		var durations = WeatherConfigManager.getSettings().durationMinutes();
		return durations.get(server.overworld().getRandom().nextInt(durations.size()));
	}

	private static void applyCondition(MinecraftServer server, WeatherCondition condition, boolean forceSync) {
		if (server == null || condition == null) return;
		int timer = Integer.MAX_VALUE;
		for (ServerLevel level : server.getAllLevels()) {
			if (!forceSync && lastAppliedLevelConditions.get(level) == condition) continue;
			WeatherData weatherData = level.getWeatherData();
			if (weatherData == null) continue;
			weatherData.setClearWeatherTime(condition == WeatherCondition.CLEAR ? timer : 0);
			weatherData.setRainTime(condition == WeatherCondition.CLEAR ? 0 : timer);
			weatherData.setThunderTime(condition == WeatherCondition.THUNDERSTORM ? timer : 0);
			weatherData.setRaining(condition.precipitating());
			weatherData.setThundering(condition == WeatherCondition.THUNDERSTORM);
			lastAppliedLevelConditions.put(level, condition);
		}

		if (forceSync || lastAppliedCondition != condition) {
			invokeWeatherSetter(server, condition, timer);
			lastAppliedCondition = condition;
		}
	}

	private static void invokeWeatherSetter(MinecraftServer server, WeatherCondition condition, int timer) {
		try {
			Method method = server.getClass().getMethod(
				"setWeatherParameters", int.class, int.class, boolean.class, boolean.class);
			method.invoke(server,
				condition == WeatherCondition.CLEAR ? timer : 0,
				condition == WeatherCondition.CLEAR ? 0 : timer,
				condition.precipitating(),
				condition == WeatherCondition.THUNDERSTORM);
		} catch (ReflectiveOperationException | RuntimeException exception) {
		}
	}

	private static long currentAbsoluteTime(MinecraftServer server) {
		return Math.max(0L, MadokuTimeManager.getCurrentAbsoluteDayTime(server == null ? null : server.overworld()));
	}

	private static long resolveMinutesToTicks(int minutes) {
		try {
			return Math.max(1L, Math.multiplyExact((long) Math.max(1, minutes), TICKS_PER_MINUTE));
		} catch (ArithmeticException exception) {
			return Long.MAX_VALUE;
		}
	}

	private static long safeAdd(long base, long delta) {
		try {
			return Math.addExact(base, delta);
		} catch (ArithmeticException exception) {
			return Long.MAX_VALUE;
		}
	}

	private static String readString(JsonObject source, String key, String fallback) {
		try {
			return source != null && source.has(key) ? source.get(key).getAsString() : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static long readLong(JsonObject source, String key, long fallback) {
		try {
			return source != null && source.has(key) ? source.get(key).getAsLong() : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static WeatherCondition resolveCondition(String value) {
		if (value == null || value.isBlank()) return null;
		for (WeatherCondition condition : WeatherCondition.values()) {
			if (condition.id().equalsIgnoreCase(value.trim())) return condition;
		}
		return null;
	}


	private record Selection(WeatherCondition condition, double averageHumidity) { }

	public enum WeatherCondition {
		CLEAR("clear-weather", false),
		RAIN("rain-weather", true),
		THUNDERSTORM("thunderstorm-weather", true);

		private final String id;
		private final boolean precipitating;

		WeatherCondition(String id, boolean precipitating) {
			this.id = id;
			this.precipitating = precipitating;
		}

		public String id() { return id; }
		public boolean precipitating() { return precipitating; }
	}
}

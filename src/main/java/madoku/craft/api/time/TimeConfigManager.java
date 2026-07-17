package madoku.craft.api.time;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.MadokuAPIManager;
import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.MadokuJSONManager;
import madoku.craft.api.season.MadokuSeasonManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class TimeConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(TimeConfigManager.class);
	private static final String DEBUG_SUB_SYSTEM = "time-config-manager";
	private static final String TIME_CONFIG_FOLDER_NAME = MadokuAPIManager.API_FOLDER_NAME + "/madoku-time";
	private static final String TIME_CONFIG_FILE_NAME = "madoku-time";
	private static final long TICKS_PER_MINUTE = MadokuTimeManager.TICKS_PER_SECOND * MadokuTimeManager.SECONDS_PER_MINUTE;

	private static final String FIELD_DAY_CYCLE = "day-cycle";
	private static final String FIELD_SEASONAL_CHANGES = "seasonal-changes";
	private static final String FIELD_TIME = "time";
	private static final String FIELD_DAY = "day";
	private static final String FIELD_NIGHT_DURATION = "night";
	private static final String FIELD_SLEEP = "sleep";
	private static final String FIELD_TIME_TRANSITIONS = "time-transitions";
	private static final String FIELD_FORWARD_TIME = "forward-time";
	private static final String FIELD_THUNDERSTORM_BYPASS = "thunderstorm-bypass";
	private static final String FIELD_SPRING = "spring";
	private static final String FIELD_SUMMER = "summer";
	private static final String FIELD_FALL = "fall";
	private static final String FIELD_WINTER = "winter";
	private static final String FIELD_TIME_ADJUSTMENT = "time-adjustment";

	private static final String FIELD_ENABLED = "enabled";
	private static final String FIELD_VALUE = "value";
	private static final String FIELD_MORNING = "morning";
	private static final String FIELD_NOON = "noon";
	private static final String FIELD_NIGHT_TRANSITION = "night";
	private static final String FIELD_MIDNIGHT = "midnight";
	private static final String FIELD_CLEAR_WEATHER = "clear-weather";

	private static volatile Settings settings = Settings.defaults();

	private TimeConfigManager() {
	}

	public static void initialize() {
		loadConfig();
		emitTimeDebug("initialize", builder -> builder
			.subject("initialize")
			.field("config-folder", TIME_CONFIG_FOLDER_NAME)
			.field("config-file", TIME_CONFIG_FILE_NAME)
			.field("day-cycle-enabled", isDayCycleEnabled())
			.field("day-cycle-time-enabled", isDayCycleTimeEnabled())
			.field("day-minutes", getDayMinutes())
			.field("night-minutes", getNightMinutes())
			.field("seasonal-changes-enabled", isSeasonalChangesEnabled())
			.field("sleep-enabled", isSleepEnabled())
			.field("sleep-transitions-enabled", isSleepTimeTransitionsEnabled())
			.field("sleep-forward-time-enabled", isForwardTimeEnabled()));
	}

	public static boolean isDayCycleEnabled() {
		return settings.dayCycle.enabled();
	}

	public static boolean isDayCycleTimeEnabled() {
		return settings.dayCycle.time().enabled();
	}

	public static long getDayMinutes() {
		return settings.dayCycle.time().day().valueMinutes();
	}

	public static long getNightMinutes() {
		return settings.dayCycle.time().night().valueMinutes();
	}

	public static boolean isSeasonalChangesEnabled() {
		return settings.dayCycle.seasonalChanges().enabled();
	}

	public static double getSeasonalDayMultiplier(String seasonId) {
		if (!isSeasonalChangesActive()) {
			return 1.0D;
		}
		return resolveSeasonAdjustment(seasonId).day();
	}

	public static double getSeasonalNightMultiplier(String seasonId) {
		if (!isSeasonalChangesActive()) {
			return 1.0D;
		}
		return resolveSeasonAdjustment(seasonId).night();
	}

	public static long getSeasonalCycleTicks(String seasonId) {
		double minutes = getSeasonalCycleMinutes(seasonId);
		if (!Double.isFinite(minutes) || minutes <= 0.0D) {
			return 24L * TICKS_PER_MINUTE;
		}
		return Math.max(1L, Math.round(minutes * (double) TICKS_PER_MINUTE));
	}

	public static long getDayCycleTicks() {
		return getSeasonalCycleTicks(isSeasonalChangesActive() ? MadokuSeasonManager.getCurrentSeasonId() : null);
	}

	public static double getSeasonalCycleMinutes(String seasonId) {
		double dayMinutes = Math.max(1L, getDayMinutes()) * getSeasonalDayMultiplier(seasonId);
		double nightMinutes = Math.max(1L, getNightMinutes()) * getSeasonalNightMultiplier(seasonId);
		return Math.max(1.0D, dayMinutes + nightMinutes);
	}

	public static boolean isSleepEnabled() {
		return settings.sleep.enabled();
	}

	public static boolean isSleepTimeTransitionsEnabled() {
		return settings.sleep.timeTransitions().enabled();
	}

	public static int getMorningMinutes() {
		return settings.sleep.timeTransitions().morningMinutes();
	}

	public static int getNoonMinutes() {
		return settings.sleep.timeTransitions().noonMinutes();
	}

	public static int getNightMinutesTransition() {
		return settings.sleep.timeTransitions().nightMinutes();
	}

	public static int getMidnightMinutes() {
		return settings.sleep.timeTransitions().midnightMinutes();
	}

	public static boolean isForwardTimeEnabled() {
		return settings.sleep.forwardTime().enabled();
	}

	public static boolean shouldClearWeather() {
		return settings.sleep.forwardTime().clearWeather();
	}

	public static boolean isThunderstormBypassEnabled() {
		return settings.sleep.forwardTime().thunderstormBypass().enabled();
	}

	private static void loadConfig() {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path rootDirectory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(TIME_CONFIG_FOLDER_NAME);
			Path configFile = resolveJsonFile(rootDirectory, TIME_CONFIG_FILE_NAME);
			JsonObject normalized = JSONFormatManager.ensureManagedFile(configFile, defaults);
			Settings loaded = Settings.fromJson(normalized);
			JSONFormatManager.writeManagedFile(configFile, loaded.toConfigJson(), defaults);
			settings = loaded;
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load Madoku time config; using defaults.", exception);
		}
	}

	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Config file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private static JsonObject readObject(JsonObject source, String key) {
		if (source == null || key == null || key.isBlank()) {
			return new JsonObject();
		}
		JsonElement element = source.get(key);
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

	private static long sanitizePositive(long value, long fallback) {
		return value > 0L ? value : fallback;
	}

	private static double sanitizePositive(double value, double fallback) {
		return Double.isFinite(value) && value > 0.0D ? value : fallback;
	}

	private static int sanitizeCycleMinute(int value, int fallback) {
		int normalized = value % 24;
		if (normalized < 0) {
			normalized += 24;
		}
		return normalized;
	}

	private static boolean isStrictlyOrdered(int midnight, int morning, int noon, int night) {
		return midnight >= 0
			&& midnight < 24
			&& morning >= 0
			&& morning < 24
			&& noon >= 0
			&& noon < 24
			&& night >= 0
			&& night < 24
			&& midnight < morning
			&& morning < noon
			&& noon < night;
	}

	private static boolean isSeasonalChangesActive() {
		return isSeasonalChangesEnabled() && MadokuSeasonManager.isEnabled();
	}

	private static TimeAdjustmentSettings resolveSeasonAdjustment(String seasonId) {
		SeasonalChangesSettings seasonalChanges = settings.dayCycle.seasonalChanges();
		if (!isSeasonalChangesActive()) {
			return TimeAdjustmentSettings.defaults(1.0D, 1.0D);
		}
		return switch (normalizeSeasonId(seasonId)) {
			case FIELD_SUMMER -> seasonalChanges.summer().timeAdjustment();
			case FIELD_FALL -> seasonalChanges.fall().timeAdjustment();
			case FIELD_WINTER -> seasonalChanges.winter().timeAdjustment();
			case FIELD_SPRING -> seasonalChanges.spring().timeAdjustment();
			default -> seasonalChanges.spring().timeAdjustment();
		};
	}

	private static String normalizeSeasonId(String seasonId) {
		if (seasonId == null) {
			return FIELD_SPRING;
		}
		String normalized = seasonId.trim().toLowerCase();
		return switch (normalized) {
			case FIELD_SPRING, FIELD_SUMMER, FIELD_FALL, FIELD_WINTER -> normalized;
			default -> FIELD_SPRING;
		};
	}

	private static void emitTimeDebug(String metricId, Consumer<MadokuDebugManager.EventBuilder> customizer) {
		String entry = MadokuDebugManager.resolveCallerMethodName(1);
		if (!MadokuDebugManager.shouldEmit("api", "time-manager", DEBUG_SUB_SYSTEM, entry)) {
			return;
		}
		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(metricId, "api", "time-manager", DEBUG_SUB_SYSTEM, entry)
			.side(MadokuDebugManager.Side.SERVER);
		if (customizer != null) {
			customizer.accept(builder);
		}
		builder.log();
	}

	private record Settings(DayCycleSettings dayCycle, SleepSettings sleep) {
		private static Settings defaults() {
			return new Settings(DayCycleSettings.defaults(), SleepSettings.defaults());
		}

		private static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			return new Settings(
				DayCycleSettings.fromJson(readObject(source, FIELD_DAY_CYCLE), defaults.dayCycle),
				SleepSettings.fromJson(readObject(source, FIELD_SLEEP), defaults.sleep)
			);
		}

		private JsonObject toConfigJson() {
			return JSONFormatManager.object()
				.object(FIELD_DAY_CYCLE, builder -> dayCycle.toConfigJson(builder))
				.object(FIELD_SLEEP, builder -> sleep.toConfigJson(builder))
				.build();
		}
	}

	private record DayCycleSettings(boolean enabled, TimeSettings time, SeasonalChangesSettings seasonalChanges) {
		private static DayCycleSettings defaults() {
			return new DayCycleSettings(true, TimeSettings.defaults(), SeasonalChangesSettings.defaults());
		}

		private static DayCycleSettings fromJson(JsonObject source, DayCycleSettings defaults) {
			DayCycleSettings base = defaults == null ? defaults() : defaults;
			return new DayCycleSettings(
				getBoolean(source, FIELD_ENABLED, base.enabled),
				TimeSettings.fromJson(readObject(source, FIELD_TIME), base.time),
				SeasonalChangesSettings.fromJson(readObject(source, FIELD_SEASONAL_CHANGES), base.seasonalChanges)
			);
		}

		private void toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			if (builder == null) {
				return;
			}
			builder.put(FIELD_ENABLED, enabled);
			builder.object(FIELD_TIME, timeBuilder -> time.toConfigJson(timeBuilder));
			builder.object(FIELD_SEASONAL_CHANGES, seasonalBuilder -> seasonalChanges.toConfigJson(seasonalBuilder));
		}
	}

	private record TimeSettings(boolean enabled, TimeDurationSettings day, TimeDurationSettings night) {
		private static TimeSettings defaults() {
			return new TimeSettings(true, TimeDurationSettings.defaults(12L), TimeDurationSettings.defaults(12L));
		}

		private static TimeSettings fromJson(JsonObject source, TimeSettings defaults) {
			TimeSettings base = defaults == null ? defaults() : defaults;
			return new TimeSettings(
				getBoolean(source, FIELD_ENABLED, base.enabled),
				TimeDurationSettings.fromJson(readObject(source, FIELD_DAY), base.day),
				TimeDurationSettings.fromJson(readObject(source, FIELD_NIGHT_DURATION), base.night)
			);
		}

		private void toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			if (builder == null) {
				return;
			}
			builder.put(FIELD_ENABLED, enabled);
			builder.object(FIELD_DAY, dayBuilder -> day.toConfigJson(dayBuilder));
			builder.object(FIELD_NIGHT_DURATION, nightBuilder -> night.toConfigJson(nightBuilder));
		}
	}

	private record TimeDurationSettings(long valueMinutes) {
		private static TimeDurationSettings defaults(long minutes) {
			return new TimeDurationSettings(Math.max(1L, minutes));
		}

		private static TimeDurationSettings fromJson(JsonObject source, TimeDurationSettings defaults) {
			TimeDurationSettings base = defaults == null ? defaults(12L) : defaults;
			return new TimeDurationSettings(sanitizePositive(getLong(source, FIELD_VALUE, base.valueMinutes), base.valueMinutes));
		}

		private void toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			if (builder == null) {
				return;
			}
			builder.put(FIELD_VALUE, valueMinutes);
		}
	}

	private record SeasonalChangesSettings(
		boolean enabled,
		SeasonSettings spring,
		SeasonSettings summer,
		SeasonSettings fall,
		SeasonSettings winter
	) {
		private static SeasonalChangesSettings defaults() {
			return new SeasonalChangesSettings(
				true,
				SeasonSettings.defaults(1.0D, 1.0D),
				SeasonSettings.defaults(1.25D, 0.75D),
				SeasonSettings.defaults(1.0D, 1.0D),
				SeasonSettings.defaults(0.75D, 1.25D)
			);
		}

		private static SeasonalChangesSettings fromJson(JsonObject source, SeasonalChangesSettings defaults) {
			SeasonalChangesSettings base = defaults == null ? defaults() : defaults;
			return new SeasonalChangesSettings(
				getBoolean(source, FIELD_ENABLED, base.enabled),
				SeasonSettings.fromJson(readObject(source, FIELD_SPRING), base.spring),
				SeasonSettings.fromJson(readObject(source, FIELD_SUMMER), base.summer),
				SeasonSettings.fromJson(readObject(source, FIELD_FALL), base.fall),
				SeasonSettings.fromJson(readObject(source, FIELD_WINTER), base.winter)
			);
		}

		private void toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			if (builder == null) {
				return;
			}
			builder.put(FIELD_ENABLED, enabled);
			builder.object(FIELD_SPRING, springBuilder -> spring.toConfigJson(springBuilder));
			builder.object(FIELD_SUMMER, summerBuilder -> summer.toConfigJson(summerBuilder));
			builder.object(FIELD_FALL, fallBuilder -> fall.toConfigJson(fallBuilder));
			builder.object(FIELD_WINTER, winterBuilder -> winter.toConfigJson(winterBuilder));
		}
	}

	private record SeasonSettings(TimeAdjustmentSettings timeAdjustment) {
		private static SeasonSettings defaults(double dayMultiplier, double nightMultiplier) {
			return new SeasonSettings(TimeAdjustmentSettings.defaults(dayMultiplier, nightMultiplier));
		}

		private static SeasonSettings fromJson(JsonObject source, SeasonSettings defaults) {
			SeasonSettings base = defaults == null ? defaults(1.0D, 1.0D) : defaults;
			return new SeasonSettings(TimeAdjustmentSettings.fromJson(readObject(source, FIELD_TIME_ADJUSTMENT), base.timeAdjustment));
		}

		private void toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			if (builder == null) {
				return;
			}
			builder.object(FIELD_TIME_ADJUSTMENT, adjustmentBuilder -> timeAdjustment.toConfigJson(adjustmentBuilder));
		}
	}

	private record TimeAdjustmentSettings(double day, double night) {
		private static TimeAdjustmentSettings defaults(double day, double night) {
			return new TimeAdjustmentSettings(sanitizePositive(day, 1.0D), sanitizePositive(night, 1.0D));
		}

		private static TimeAdjustmentSettings fromJson(JsonObject source, TimeAdjustmentSettings defaults) {
			TimeAdjustmentSettings base = defaults == null ? defaults(1.0D, 1.0D) : defaults;
			double day = sanitizePositive(getDouble(source, FIELD_DAY, base.day), base.day);
			double night = sanitizePositive(getDouble(source, FIELD_NIGHT_DURATION, base.night), base.night);
			return new TimeAdjustmentSettings(day, night);
		}

		private void toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			if (builder == null) {
				return;
			}
			builder.put(FIELD_DAY, day);
			builder.put(FIELD_NIGHT_DURATION, night);
		}
	}

	private record SleepSettings(boolean enabled, TimeTransitionsSettings timeTransitions, ForwardTimeSettings forwardTime) {
		private static SleepSettings defaults() {
			return new SleepSettings(true, TimeTransitionsSettings.defaults(), ForwardTimeSettings.defaults());
		}

		private static SleepSettings fromJson(JsonObject source, SleepSettings defaults) {
			SleepSettings base = defaults == null ? defaults() : defaults;
			return new SleepSettings(
				getBoolean(source, FIELD_ENABLED, base.enabled),
				TimeTransitionsSettings.fromJson(readObject(source, FIELD_TIME_TRANSITIONS), base.timeTransitions),
				ForwardTimeSettings.fromJson(readObject(source, FIELD_FORWARD_TIME), base.forwardTime)
			);
		}

		private void toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			if (builder == null) {
				return;
			}
			builder.put(FIELD_ENABLED, enabled);
			builder.object(FIELD_TIME_TRANSITIONS, transitionBuilder -> timeTransitions.toConfigJson(transitionBuilder));
			builder.object(FIELD_FORWARD_TIME, forwardBuilder -> forwardTime.toConfigJson(forwardBuilder));
		}
	}

	private record TimeTransitionsSettings(boolean enabled, int morningMinutes, int noonMinutes, int nightMinutes, int midnightMinutes) {
		private static TimeTransitionsSettings defaults() {
			return new TimeTransitionsSettings(true, 6, 12, 18, 0);
		}

		private static TimeTransitionsSettings fromJson(JsonObject source, TimeTransitionsSettings defaults) {
			TimeTransitionsSettings base = defaults == null ? defaults() : defaults;
			int morning = sanitizeCycleMinute((int) getLong(source, FIELD_MORNING, base.morningMinutes), base.morningMinutes);
			int noon = sanitizeCycleMinute((int) getLong(source, FIELD_NOON, base.noonMinutes), base.noonMinutes);
			int night = sanitizeCycleMinute((int) getLong(source, FIELD_NIGHT_TRANSITION, base.nightMinutes), base.nightMinutes);
			int midnight = sanitizeCycleMinute((int) getLong(source, FIELD_MIDNIGHT, base.midnightMinutes), base.midnightMinutes);
			if (!isStrictlyOrdered(midnight, morning, noon, night)) {
				return base;
			}
			return new TimeTransitionsSettings(
				getBoolean(source, FIELD_ENABLED, base.enabled),
				morning,
				noon,
				night,
				midnight
			);
		}

		private void toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			if (builder == null) {
				return;
			}
			builder.put(FIELD_ENABLED, enabled);
			builder.put(FIELD_MORNING, morningMinutes);
			builder.put(FIELD_NOON, noonMinutes);
			builder.put(FIELD_NIGHT_TRANSITION, nightMinutes);
			builder.put(FIELD_MIDNIGHT, midnightMinutes);
		}
	}

	private record ForwardTimeSettings(boolean enabled, boolean clearWeather, ThunderstormBypassSettings thunderstormBypass) {
		private static ForwardTimeSettings defaults() {
			return new ForwardTimeSettings(true, false, ThunderstormBypassSettings.defaults());
		}

		private static ForwardTimeSettings fromJson(JsonObject source, ForwardTimeSettings defaults) {
			ForwardTimeSettings base = defaults == null ? defaults() : defaults;
			return new ForwardTimeSettings(
				getBoolean(source, FIELD_ENABLED, base.enabled),
				getBoolean(source, FIELD_CLEAR_WEATHER, base.clearWeather),
				ThunderstormBypassSettings.fromJson(readObject(source, FIELD_THUNDERSTORM_BYPASS), base.thunderstormBypass)
			);
		}

		private void toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			if (builder == null) {
				return;
			}
			builder.put(FIELD_ENABLED, enabled);
			builder.put(FIELD_CLEAR_WEATHER, clearWeather);
			builder.object(FIELD_THUNDERSTORM_BYPASS, bypassBuilder -> thunderstormBypass.toConfigJson(bypassBuilder));
		}
	}

	private record ThunderstormBypassSettings(boolean enabled) {
		private static ThunderstormBypassSettings defaults() {
			return new ThunderstormBypassSettings(false);
		}

		private static ThunderstormBypassSettings fromJson(JsonObject source, ThunderstormBypassSettings defaults) {
			ThunderstormBypassSettings base = defaults == null ? defaults() : defaults;
			return new ThunderstormBypassSettings(getBoolean(source, FIELD_ENABLED, base.enabled));
		}

		private void toConfigJson(JSONFormatManager.ObjectBuilder builder) {
			if (builder == null) {
				return;
			}
			builder.put(FIELD_ENABLED, enabled);
		}
	}
}

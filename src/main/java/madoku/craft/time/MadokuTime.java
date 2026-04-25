package madoku.craft.time;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.clock.MadokuClock;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.data.DataManagerSystem;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class MadokuTime {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuTime.class);

	private static final long DEFAULT_REAL_MINUTES_PER_DAY = 12L;
	private static final long DEFAULT_REAL_MINUTES_PER_NIGHT = 12L;
	private static final int DEFAULT_CLOCK_DAY_START_MINUTES = 6 * 60;
	private static final int DEFAULT_CLOCK_NIGHT_START_MINUTES = 18 * 60;
	private static final int DEFAULT_CLOCK_MIDNIGHT_MINUTES = 0;

	public static final long MINECRAFT_TICKS_PER_CYCLE = 24000L;
	private static final long VANILLA_TIME_SET_DAY_TICK = 1000L;
	private static final long VANILLA_TIME_SET_NIGHT_TICK = 13000L;
	private static final int MINUTES_PER_DAY = 24 * 60;
	private static final int MINECRAFT_CLOCK_ZERO_OFFSET_MINUTES = 6 * 60;

	private static final String TIME_CONFIG_FOLDER_NAME = "madoku-craft-time";
	private static final String TIME_CONFIG_FILE_NAME = "madoku-time";
	private static final String DATA_FOLDER_NAME = "madoku-craft-time";
	private static final String DATA_FILE_NAME = "madoku-time";

	private static volatile TimeSettings settings = TimeSettings.defaults();
	private static boolean hasAppliedDayTime = false;
	private static boolean hasAppliedManagedGameRules = false;
	private static boolean hasRestoredVanillaGameRules = false;
	private static long lastAppliedDayTime = 0L;
	private static long lastAutosaveBucket = Long.MIN_VALUE;
	private static long sessionTickOffset = 0L;

	private MadokuTime() {
	}

	public static void initialize() {
		loadStaticConfig();
	}

	public static void reset() {
		hasAppliedDayTime = false;
		hasAppliedManagedGameRules = false;
		hasRestoredVanillaGameRules = false;
		lastAppliedDayTime = 0L;
		lastAutosaveBucket = Long.MIN_VALUE;
		sessionTickOffset = 0L;
	}

	public static void loadPersistedData(MinecraftServer server) {
		loadStaticConfig();
		TimeSettings currentSettings = settings;

		JsonObject data = DataManagerSystem.loadWorldData(
			server,
			DATA_FOLDER_NAME,
			DATA_FILE_NAME,
			createData(
				0L,
				currentSettings.clockDayStartMinutes / 60,
				currentSettings.clockDayStartMinutes % 60
			)
		);

		long day = getLong(data, "day", 0L);
		int hour = (int) getLong(data, "hour", currentSettings.clockDayStartMinutes / 60);
		int minute = (int) getLong(data, "minute", currentSettings.clockDayStartMinutes % 60);
		if (day >= 0L && hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
			setClockFromAbsoluteDayTime(toAbsoluteDayTime(day, hour, minute));
		}

		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		long currentBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
		if (currentBucket != lastAutosaveBucket) {
			lastAutosaveBucket = currentBucket;
			savePersistedData(server);
		}
	}

	public static void savePersistedData(MinecraftServer server) {
		long absoluteDayTime = getCurrentAbsoluteDayTime();
		long day = getDay(absoluteDayTime);
		int totalMinutes = getTotalMinutes(absoluteDayTime);
		int hour = totalMinutes / 60;
		int minute = totalMinutes % 60;
		DataManagerSystem.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createData(day, hour, minute));
	}

	public static void update(MinecraftServer server) {
		TimeSettings currentSettings = settings;
		if (!currentSettings.enabled) {
			if (!hasRestoredVanillaGameRules || hasAppliedManagedGameRules || hasAppliedDayTime) {
				for (ServerLevel world : server.getAllLevels()) {
					world.getGameRules().set(GameRules.ADVANCE_TIME, true, server);
				}
				hasRestoredVanillaGameRules = true;
				hasAppliedManagedGameRules = false;
			}

			ServerLevel overworld = server.overworld();
			if (overworld != null) {
				MadokuClock.observeWorldTime(overworld);
			}

			hasAppliedDayTime = false;
			lastAppliedDayTime = 0L;
			return;
		}
		if (!hasAppliedManagedGameRules) {
			for (ServerLevel world : server.getAllLevels()) {
				world.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
				world.getGameRules().set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 100, server);
			}
			hasAppliedManagedGameRules = true;
			hasRestoredVanillaGameRules = false;
		}

		long sessionTicks = getSessionTicks();
		long absoluteDayTime = getAbsoluteDayTime(sessionTicks, currentSettings);

			ServerLevel overworld = server.overworld();
			if (overworld != null) {
				long observedDayTime = overworld.getOverworldClockTime();
				MadokuClock.observeWorldTime(observedDayTime);
				if (hasAppliedDayTime && observedDayTime != lastAppliedDayTime) {
					long remappedObservedDayTime =
					remapVanillaTimeSetAnchors(observedDayTime, lastAppliedDayTime, currentSettings);
				sessionTickOffset = getNearestSessionTicks(remappedObservedDayTime, currentSettings) - MadokuTicks.getGameplayTicks();
				// Preserve the exact command-driven value this tick; clock mapping takes over next tick.
				absoluteDayTime = remappedObservedDayTime;
			}
			}

			for (ServerLevel world : server.getAllLevels()) {
				var overworldClock = world.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
				world.clockManager().setTotalTicks(overworldClock, absoluteDayTime);
			}

		lastAppliedDayTime = absoluteDayTime;
		hasAppliedDayTime = true;
		syncClientTime(server);
	}

	public static void syncClientTime(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return;
		}

		ClientboundSetTimePacket packet = overworld.clockManager().createFullSyncPacket();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.connection.send(packet);
		}
	}

	public static long getTimeOfDay(long cycleTick) {
		TimeSettings currentSettings = settings;
		long normalizedTick = Math.floorMod(cycleTick, currentSettings.serverTicksPerCycle);

		if (normalizedTick < currentSettings.serverTicksPerDay) {
			long dayTick = mapRangeInclusive(
				normalizedTick,
				currentSettings.serverTicksPerDay,
				currentSettings.minecraftTicksPerDay
			);
			return Math.floorMod(
				currentSettings.minecraftDayStartTick + dayTick,
				MINECRAFT_TICKS_PER_CYCLE
			);
		}

		long nightTick = normalizedTick - currentSettings.serverTicksPerDay;
		long mappedNightTick = mapRangeInclusive(
			nightTick,
			currentSettings.serverTicksPerNight,
			currentSettings.minecraftTicksPerNight
		);
		return Math.floorMod(
			currentSettings.minecraftNightStartTick + mappedNightTick,
			MINECRAFT_TICKS_PER_CYCLE
		);
	}

	public static long getDay(long absoluteDayTime) {
		TimeSettings currentSettings = settings;
		return Math.floorDiv(
			absoluteDayTime + currentSettings.dayRolloverOffsetTicks,
			MINECRAFT_TICKS_PER_CYCLE
		);
	}

	public static int getTotalMinutes(long absoluteDayTime) {
		long timeOfDay = Math.floorMod(absoluteDayTime, MINECRAFT_TICKS_PER_CYCLE);
		long minutesFromTick = (timeOfDay * MINUTES_PER_DAY) / MINECRAFT_TICKS_PER_CYCLE;
		return (int) ((minutesFromTick + MINECRAFT_CLOCK_ZERO_OFFSET_MINUTES) % MINUTES_PER_DAY);
	}

	public static long getCurrentAbsoluteDayTime() {
		if (!settings.enabled) {
			return MadokuTicks.getGameplayTicks();
		}
		return getAbsoluteDayTime(getSessionTicks(), settings);
	}

	public static long getCurrentAbsoluteDayTime(ServerLevel world) {
		if (settings.enabled) {
			return getCurrentAbsoluteDayTime();
		}
			return world == null ? MadokuTicks.getGameplayTicks() : world.getOverworldClockTime();
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static long getGameplayTicksPerDay() {
		TimeSettings currentSettings = settings;
		if (!currentSettings.enabled) {
			return MINECRAFT_TICKS_PER_CYCLE;
		}
		return Math.max(1L, currentSettings.serverTicksPerCycle);
	}

	public static long toAbsoluteDayTime(long day, int hour, int minute) {
		TimeSettings currentSettings = settings;
		long normalizedDay = Math.max(0L, day);
		int normalizedHour = Math.floorMod(hour, 24);
		int normalizedMinute = Math.floorMod(minute, 60);
		int totalMinutes = normalizedHour * 60 + normalizedMinute;
		int minecraftMinutes = Math.floorMod(totalMinutes - MINECRAFT_CLOCK_ZERO_OFFSET_MINUTES, MINUTES_PER_DAY);
		long timeOfDay = (minecraftMinutes * MINECRAFT_TICKS_PER_CYCLE) / MINUTES_PER_DAY;
		long dayCarry = Math.floorDiv(
			timeOfDay + currentSettings.dayRolloverOffsetTicks,
			MINECRAFT_TICKS_PER_CYCLE
		);
		long completedCycles = normalizedDay - dayCarry;
		return completedCycles * MINECRAFT_TICKS_PER_CYCLE + timeOfDay;
	}

	public static void setClockFromAbsoluteDayTime(long absoluteDayTime) {
		sessionTickOffset = getSessionTicks(absoluteDayTime, settings) - MadokuTicks.getGameplayTicks();
	}

	public static void advanceSkippedTimeTicks(long amount) {
		if (!settings.enabled || amount <= 1L) {
			return;
		}

		long extraTicks = amount - 1L;
		try {
			sessionTickOffset = Math.addExact(sessionTickOffset, extraTicks);
		} catch (ArithmeticException exception) {
			sessionTickOffset = Long.MAX_VALUE;
		}
	}

	public static boolean isDaytime(long absoluteDayTime) {
		return isDaytime(getTotalMinutes(absoluteDayTime));
	}

	public static boolean isSleepTime(long absoluteDayTime) {
		return isSleepTime(getTotalMinutes(absoluteDayTime));
	}

	private static long getSessionTicks() {
		return MadokuTicks.getGameplayTicks() + sessionTickOffset;
	}

	private static boolean isDaytime(int totalMinutes) {
		TimeSettings currentSettings = settings;
		return isWithinWrappedRange(
			totalMinutes,
			currentSettings.clockDayStartMinutes,
			currentSettings.clockNightStartMinutes
		);
	}

	private static boolean isSleepTime(int totalMinutes) {
		return !isDaytime(totalMinutes);
	}

	private static long getAbsoluteDayTime(long sessionTicks, TimeSettings timeSettings) {
		long cycleTick = Math.floorMod(sessionTicks, timeSettings.serverTicksPerCycle);
		long completedCycles = Math.floorDiv(sessionTicks, timeSettings.serverTicksPerCycle);
		long timeOfDay = getTimeOfDay(cycleTick);
		long timeSinceDayStart = Math.floorMod(
			timeOfDay - timeSettings.minecraftDayStartTick,
			MINECRAFT_TICKS_PER_CYCLE
		);
		return completedCycles * MINECRAFT_TICKS_PER_CYCLE
			+ timeSettings.minecraftDayStartTick
			+ timeSinceDayStart;
	}

	private static JsonObject createData(long day, int hour, int minute) {
		JsonObject root = new JsonObject();
		root.addProperty("day", Math.max(0L, day));
		root.addProperty("hour", Math.max(0, Math.min(23, hour)));
		root.addProperty("minute", Math.max(0, Math.min(59, minute)));
		return root;
	}

	private static long getLong(JsonObject object, String key, long fallback) {
		if (object == null) {
			return fallback;
		}

		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}

		try {
			return element.getAsLong();
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static String getString(JsonObject object, String key, String fallback) {
		if (object == null) {
			return fallback;
		}

		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}

		String value = element.getAsString();
		return value == null ? fallback : value;
	}

	private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
		if (object == null) {
			return fallback;
		}

		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}

		return element.getAsBoolean();
	}

	private static long getSessionTicks(long absoluteDayTime, TimeSettings timeSettings) {
		long shiftedDayTime = absoluteDayTime - timeSettings.minecraftDayStartTick;
		long completedCycles = Math.floorDiv(shiftedDayTime, MINECRAFT_TICKS_PER_CYCLE);
		long timeSinceDayStart = Math.floorMod(shiftedDayTime, MINECRAFT_TICKS_PER_CYCLE);
		long timeOfDay = Math.floorMod(
			timeSettings.minecraftDayStartTick + timeSinceDayStart,
			MINECRAFT_TICKS_PER_CYCLE
		);
		long cycleTick = getCycleTick(timeOfDay, timeSettings);
		return completedCycles * timeSettings.serverTicksPerCycle + cycleTick;
	}

	private static long getNearestSessionTicks(long observedDayTime, TimeSettings timeSettings) {
		long candidate = getSessionTicks(observedDayTime, timeSettings);
		long bestTicks = candidate;
		long bestDistance = Math.abs(getAbsoluteDayTime(candidate, timeSettings) - observedDayTime);

		for (long delta = -2L; delta <= 2L; delta++) {
			long testTicks = candidate + delta;
			long distance = Math.abs(getAbsoluteDayTime(testTicks, timeSettings) - observedDayTime);
			if (distance < bestDistance) {
				bestDistance = distance;
				bestTicks = testTicks;
			}
		}

		return bestTicks;
	}

	private static long remapVanillaTimeSetAnchors(
		long observedDayTime,
		long previousAppliedDayTime,
		TimeSettings timeSettings
	) {
		long timeOfDay = Math.floorMod(observedDayTime, MINECRAFT_TICKS_PER_CYCLE);
		if (timeOfDay == VANILLA_TIME_SET_DAY_TICK) {
			long commandDay = getDay(observedDayTime);
			return toAbsoluteDayTime(
				commandDay,
				timeSettings.clockDayStartMinutes / 60,
				timeSettings.clockDayStartMinutes % 60
			);
		}
		if (timeOfDay == VANILLA_TIME_SET_NIGHT_TICK) {
			long commandDay = getDay(observedDayTime);
			return toAbsoluteDayTime(
				commandDay,
				timeSettings.clockNightStartMinutes / 60,
				timeSettings.clockNightStartMinutes % 60
			);
		}
		// Keep `/time set <Nd>` on vanilla day boundaries.
		// This preserves the expected day number semantics for command users.
		if (timeOfDay == 0L && observedDayTime >= MINECRAFT_TICKS_PER_CYCLE) {
			long plainAddOneDayTarget = previousAppliedDayTime + MINECRAFT_TICKS_PER_CYCLE;
			if (observedDayTime != plainAddOneDayTarget) {
				return observedDayTime;
			}
		}
		if (timeOfDay == 0L && observedDayTime == 0L) {
			return toAbsoluteDayTime(
				0L,
				timeSettings.clockDayStartMinutes / 60,
				timeSettings.clockDayStartMinutes % 60
			);
		}
		return observedDayTime;
	}

	private static long getCycleTick(long timeOfDay, TimeSettings timeSettings) {
		long dayOffset = Math.floorMod(
			timeOfDay - timeSettings.minecraftDayStartTick,
			MINECRAFT_TICKS_PER_CYCLE
		);
		if (dayOffset < timeSettings.minecraftTicksPerDay) {
			return inverseMapRangeInclusive(
				dayOffset,
				timeSettings.serverTicksPerDay,
				timeSettings.minecraftTicksPerDay
			);
		}

		long nightTick = Math.floorMod(
			timeOfDay - timeSettings.minecraftNightStartTick,
			MINECRAFT_TICKS_PER_CYCLE
		);
		return timeSettings.serverTicksPerDay
			+ inverseMapRangeInclusive(
				nightTick,
				timeSettings.serverTicksPerNight,
				timeSettings.minecraftTicksPerNight
			);
	}

	private static long clockMinutesToMinecraftTicks(int clockMinutes) {
		int minecraftMinutes = Math.floorMod(clockMinutes - MINECRAFT_CLOCK_ZERO_OFFSET_MINUTES, MINUTES_PER_DAY);
		return (minecraftMinutes * MINECRAFT_TICKS_PER_CYCLE) / MINUTES_PER_DAY;
	}

	private static int wrappedClockMinutes(int startMinutes, int endMinutes) {
		return Math.floorMod(endMinutes - startMinutes, MINUTES_PER_DAY);
	}

	private static int wrappedMidpoint(int startMinutes, int spanMinutes) {
		return Math.floorMod(startMinutes + (spanMinutes / 2), MINUTES_PER_DAY);
	}

	private static boolean isWithinWrappedRange(int value, int startInclusive, int endExclusive) {
		int span = wrappedClockMinutes(startInclusive, endExclusive);
		if (span <= 0 || span >= MINUTES_PER_DAY) {
			return false;
		}
		int offset = Math.floorMod(value - startInclusive, MINUTES_PER_DAY);
		return offset < span;
	}

	private static long wrappedDuration(long startTick, long endTick) {
		return Math.floorMod(endTick - startTick, MINECRAFT_TICKS_PER_CYCLE);
	}

	private static long mapRangeInclusive(long value, long sourceLength, long targetLength) {
		if (sourceLength <= 1L || targetLength <= 1L) {
			return 0L;
		}

		return (value * (targetLength - 1L)) / (sourceLength - 1L);
	}

	private static long inverseMapRangeInclusive(long value, long sourceLength, long targetLength) {
		if (sourceLength <= 1L || targetLength <= 1L) {
			return 0L;
		}

		long sourceRange = sourceLength - 1L;
		long targetRange = targetLength - 1L;
		return (value * sourceRange + targetRange / 2L) / targetRange;
	}

	private static void loadStaticConfig() {
		JsonObject defaults = TimeSettings.defaults().toConfigJson();
		TimeSettings fallback = TimeSettings.defaults();

		try {
			Path configDirectory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(TIME_CONFIG_FOLDER_NAME);
			Path configFile = resolveJsonFile(configDirectory, TIME_CONFIG_FILE_NAME);
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(configFile, defaults);
			TimeSettings loaded = TimeSettings.fromJson(normalized);
			JsonObject cleaned = loaded.toConfigJson();
			JsonStaticSystem.writeManagedFile(configFile, cleaned, defaults);
			settings = loaded;
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load MadokuTime static config; using defaults.", exception);
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

	private static long safeServerTicksFromMinutes(long minutes, long fallbackMinutes) {
		long safeMinutes = Math.max(1L, minutes);
		try {
				return Math.multiplyExact(
					safeMinutes,
					MadokuTicks.SECONDS_PER_MINUTE * MadokuTicks.TICKS_PER_SECOND
				);
			} catch (ArithmeticException exception) {
				return fallbackMinutes * MadokuTicks.SECONDS_PER_MINUTE * MadokuTicks.TICKS_PER_SECOND;
			}
		}

	private static long sanitizePositive(long value, long fallback) {
		return value > 0L ? value : fallback;
	}

	private static int sanitizeClockMinutes(int value, int fallback) {
		if (value < 0 || value >= MINUTES_PER_DAY) {
			return fallback;
		}
		return value;
	}

	private static int parseClockMinutes(String value, int fallback) {
		if (value == null) {
			return fallback;
		}

		String trimmed = value.trim();
		String[] parts = trimmed.split(":");
		if (parts.length != 2) {
			return fallback;
		}

		try {
			int hour = Integer.parseInt(parts[0].trim());
			int minute = Integer.parseInt(parts[1].trim());
			if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
				return fallback;
			}
			return hour * 60 + minute;
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static String formatClockMinutes(int totalMinutes) {
		int normalized = Math.floorMod(totalMinutes, MINUTES_PER_DAY);
		int hour = normalized / 60;
		int minute = normalized % 60;
		return String.format("%02d:%02d", hour, minute);
	}

	private static final class TimeSettings {
		private final boolean enabled;
		private final long realMinutesPerDay;
		private final long realMinutesPerNight;
		private final long serverTicksPerCycle;
		private final long serverTicksPerDay;
		private final long serverTicksPerNight;
		private final int clockDayStartMinutes;
		private final int clockNightStartMinutes;
		private final int clockMidnightMinutes;
		private final long minecraftDayStartTick;
		private final long minecraftNightStartTick;
		private final long minecraftMidnightTick;
		private final long dayRolloverOffsetTicks;
		private final long minecraftTicksPerDay;
		private final long minecraftTicksPerNight;

		private TimeSettings(
			boolean enabled,
			long realMinutesPerDay,
			long realMinutesPerNight,
			int clockDayStartMinutes,
			int clockNightStartMinutes,
			int clockMidnightMinutes
		) {
			this.enabled = enabled;
			this.realMinutesPerDay = realMinutesPerDay;
			this.realMinutesPerNight = realMinutesPerNight;
			this.clockDayStartMinutes = clockDayStartMinutes;
			this.clockNightStartMinutes = clockNightStartMinutes;
			this.clockMidnightMinutes = clockMidnightMinutes;
			this.serverTicksPerDay = safeServerTicksFromMinutes(realMinutesPerDay, DEFAULT_REAL_MINUTES_PER_DAY);
			this.serverTicksPerNight = safeServerTicksFromMinutes(realMinutesPerNight, DEFAULT_REAL_MINUTES_PER_NIGHT);
			this.serverTicksPerCycle = this.serverTicksPerDay + this.serverTicksPerNight;
			this.minecraftDayStartTick = clockMinutesToMinecraftTicks(clockDayStartMinutes);
			this.minecraftNightStartTick = clockMinutesToMinecraftTicks(clockNightStartMinutes);
			this.minecraftMidnightTick = clockMinutesToMinecraftTicks(clockMidnightMinutes);
			this.dayRolloverOffsetTicks = MINECRAFT_TICKS_PER_CYCLE - this.minecraftMidnightTick;
			this.minecraftTicksPerDay = wrappedDuration(this.minecraftDayStartTick, this.minecraftNightStartTick);
			this.minecraftTicksPerNight = MINECRAFT_TICKS_PER_CYCLE - this.minecraftTicksPerDay;
		}

		private static TimeSettings defaults() {
			return fromValues(
				true,
				DEFAULT_REAL_MINUTES_PER_DAY,
				DEFAULT_REAL_MINUTES_PER_NIGHT,
				DEFAULT_CLOCK_DAY_START_MINUTES,
				DEFAULT_CLOCK_NIGHT_START_MINUTES,
				DEFAULT_CLOCK_MIDNIGHT_MINUTES
			);
		}

		private static TimeSettings fromJson(JsonObject source) {
			boolean enabled = getBoolean(source, "enabled", true);
			long dayMinutes = sanitizePositive(
				getLong(source, "real-minutes-per-day", DEFAULT_REAL_MINUTES_PER_DAY),
				DEFAULT_REAL_MINUTES_PER_DAY
			);
			long nightMinutes = sanitizePositive(
				getLong(source, "real-minutes-per-night", DEFAULT_REAL_MINUTES_PER_NIGHT),
				DEFAULT_REAL_MINUTES_PER_NIGHT
			);

			int dayStart = parseClockMinutes(
				getString(source, "clock-day-start", formatClockMinutes(DEFAULT_CLOCK_DAY_START_MINUTES)),
				DEFAULT_CLOCK_DAY_START_MINUTES
			);
			int nightStart = parseClockMinutes(
				getString(source, "clock-night-start", formatClockMinutes(DEFAULT_CLOCK_NIGHT_START_MINUTES)),
				DEFAULT_CLOCK_NIGHT_START_MINUTES
			);
			int midnight = parseClockMinutes(
				getString(source, "clock-midnight", formatClockMinutes(DEFAULT_CLOCK_MIDNIGHT_MINUTES)),
				DEFAULT_CLOCK_MIDNIGHT_MINUTES
			);

			return fromValues(enabled, dayMinutes, nightMinutes, dayStart, nightStart, midnight);
		}

		private static TimeSettings fromValues(
			boolean enabled,
			long dayMinutesValue,
			long nightMinutesValue,
			int dayStartValue,
			int nightStartValue,
			int midnightValue
		) {
			long dayMinutes = sanitizePositive(dayMinutesValue, DEFAULT_REAL_MINUTES_PER_DAY);
			long nightMinutes = sanitizePositive(nightMinutesValue, DEFAULT_REAL_MINUTES_PER_NIGHT);
			int dayStart = sanitizeClockMinutes(dayStartValue, DEFAULT_CLOCK_DAY_START_MINUTES);
			int nightStart = sanitizeClockMinutes(nightStartValue, DEFAULT_CLOCK_NIGHT_START_MINUTES);
			int midnight = sanitizeClockMinutes(midnightValue, DEFAULT_CLOCK_MIDNIGHT_MINUTES);

			if (dayStart == nightStart) {
				dayStart = DEFAULT_CLOCK_DAY_START_MINUTES;
				nightStart = DEFAULT_CLOCK_NIGHT_START_MINUTES;
			}

			int daySpan = wrappedClockMinutes(dayStart, nightStart);
			if (daySpan <= 0 || daySpan >= MINUTES_PER_DAY) {
				dayStart = DEFAULT_CLOCK_DAY_START_MINUTES;
				nightStart = DEFAULT_CLOCK_NIGHT_START_MINUTES;
				daySpan = wrappedClockMinutes(dayStart, nightStart);
			}
			int nightSpan = MINUTES_PER_DAY - daySpan;

			if (!isWithinWrappedRange(midnight, nightStart, dayStart)) {
				midnight = wrappedMidpoint(nightStart, nightSpan);
			}

			return new TimeSettings(enabled, dayMinutes, nightMinutes, dayStart, nightStart, midnight);
		}

		private JsonObject toConfigJson() {
			JsonObject root = new JsonObject();
			root.addProperty("enabled", enabled);
			root.addProperty("real-minutes-per-day", realMinutesPerDay);
			root.addProperty("real-minutes-per-night", realMinutesPerNight);
			root.addProperty("clock-day-start", formatClockMinutes(clockDayStartMinutes));
			root.addProperty("clock-night-start", formatClockMinutes(clockNightStartMinutes));
			root.addProperty("clock-midnight", formatClockMinutes(clockMidnightMinutes));
			return root;
		}
	}
}

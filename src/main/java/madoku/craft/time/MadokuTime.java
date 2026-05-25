package madoku.craft.time;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.clock.MadokuClock;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
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
	private static final int MINUTES_PER_DAY = 24 * 60;
	private static final int MINECRAFT_CLOCK_ZERO_OFFSET_MINUTES = 6 * 60;

	private static final String TIME_CONFIG_FOLDER_NAME = "madoku-craft-time";
	private static final String TIME_CONFIG_FILE_NAME = "madoku-time";

	private static volatile TimeSettings settings = TimeSettings.defaults();
	private static boolean managedGameRulesApplied = false;
	private static long pendingSkippedTicks = 0L;
	private static double timeAdjustmentCarry = 0.0D;
	private static boolean hasObservedOverworldTime = false;
	private static long lastObservedOverworldDayTime = 0L;

	private MadokuTime() {
	}

	public static void initialize() {
		loadStaticConfig();
	}

	public static void reset() {
		managedGameRulesApplied = false;
		pendingSkippedTicks = 0L;
		timeAdjustmentCarry = 0.0D;
		hasObservedOverworldTime = false;
		lastObservedOverworldDayTime = 0L;
	}

	public static void loadPersistedData(MinecraftServer server) {
		loadStaticConfig();
	}

	public static void autosavePersistedData(MinecraftServer server) {
	}

	public static void savePersistedData(MinecraftServer server) {
	}

	public static void update(MinecraftServer server) {
		if (server == null) {
			return;
		}

		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return;
		}

		TimeSettings currentSettings = settings;
		long observedDayTime = overworld.getOverworldClockTime();
		observeOverworldTime(observedDayTime);
		MadokuClock.observeWorldTime(observedDayTime);

		if (!currentSettings.enabled) {
			restoreVanillaGameRules(server);
			clearRuntimeAdjustments();
			return;
		}

		applyManagedGameRules(server);
		timeAdjustmentCarry += getPerTickAdjustment(observedDayTime, currentSettings);

		long correctionTicks = takeWholeTicksFromCarry();
		long skippedTicks = consumePendingSkippedTicks();
		long totalAdjustment = safeAdd(correctionTicks, skippedTicks);
		if (totalAdjustment == 0L) {
			return;
		}

		long adjustedDayTime = applyWorldTimeDelta(server, observedDayTime, totalAdjustment);
		observeOverworldTime(adjustedDayTime);
		MadokuClock.observeWorldTime(adjustedDayTime);
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
		return Math.floorMod(cycleTick, MINECRAFT_TICKS_PER_CYCLE);
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
		if (hasObservedOverworldTime) {
			return lastObservedOverworldDayTime;
		}
		return MadokuTicks.getGameplayTicks();
	}

	public static long getCurrentAbsoluteDayTime(ServerLevel world) {
		if (world != null) {
			return world.getOverworldClockTime();
		}
		return getCurrentAbsoluteDayTime();
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
		observeOverworldTime(Math.max(0L, absoluteDayTime));
	}

	public static void advanceSkippedTimeTicks(long amount) {
		if (!settings.enabled || amount <= 1L) {
			return;
		}

		long extraTicks = amount - 1L;
		pendingSkippedTicks = safeAdd(pendingSkippedTicks, extraTicks);
	}

	public static boolean isDaytime(long absoluteDayTime) {
		return isDaytime(getTotalMinutes(absoluteDayTime));
	}

	public static boolean isSleepTime(long absoluteDayTime) {
		return isSleepTime(getTotalMinutes(absoluteDayTime));
	}

	private static void clearRuntimeAdjustments() {
		pendingSkippedTicks = 0L;
		timeAdjustmentCarry = 0.0D;
	}

	private static void observeOverworldTime(long absoluteDayTime) {
		hasObservedOverworldTime = true;
		lastObservedOverworldDayTime = absoluteDayTime;
	}

	private static void applyManagedGameRules(MinecraftServer server) {
		if (managedGameRulesApplied) {
			return;
		}
		for (ServerLevel world : server.getAllLevels()) {
			world.getGameRules().set(GameRules.ADVANCE_TIME, true, server);
		}
		managedGameRulesApplied = true;
	}

	private static void restoreVanillaGameRules(MinecraftServer server) {
		for (ServerLevel world : server.getAllLevels()) {
			world.getGameRules().set(GameRules.ADVANCE_TIME, true, server);
		}
		managedGameRulesApplied = false;
	}

	private static long applyWorldTimeDelta(MinecraftServer server, long observedDayTime, long delta) {
		long targetDayTime = safeAdd(observedDayTime, delta);
		for (ServerLevel world : server.getAllLevels()) {
			var overworldClock = world.registryAccess()
				.lookupOrThrow(Registries.WORLD_CLOCK)
				.getOrThrow(WorldClocks.OVERWORLD);
			world.clockManager().setTotalTicks(overworldClock, targetDayTime);
		}
		return targetDayTime;
	}

	private static double getPerTickAdjustment(long absoluteDayTime, TimeSettings timeSettings) {
		long timeOfDay = Math.floorMod(absoluteDayTime, MINECRAFT_TICKS_PER_CYCLE);
		double desiredTicksPerTick = isDaytime(getTotalMinutes(timeOfDay))
			? timeSettings.dayTicksPerServerTick
			: timeSettings.nightTicksPerServerTick;
		return desiredTicksPerTick - 1.0D;
	}

	private static long takeWholeTicksFromCarry() {
		if (timeAdjustmentCarry >= 1.0D) {
			long ticks = (long) Math.floor(timeAdjustmentCarry);
			timeAdjustmentCarry -= ticks;
			return ticks;
		}
		if (timeAdjustmentCarry <= -1.0D) {
			long ticks = (long) Math.ceil(timeAdjustmentCarry);
			timeAdjustmentCarry -= ticks;
			return ticks;
		}
		return 0L;
	}

	private static long consumePendingSkippedTicks() {
		long value = pendingSkippedTicks;
		pendingSkippedTicks = 0L;
		return value;
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

	private static long safeAdd(long base, long delta) {
		try {
			return Math.addExact(base, delta);
		} catch (ArithmeticException exception) {
			return delta >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
		}
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

	private static void loadStaticConfig() {
		JsonObject defaults = TimeSettings.defaults().toConfigJson();
		TimeSettings fallback = TimeSettings.defaults();

		try {
			Path configDirectory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(TIME_CONFIG_FOLDER_NAME);
			Path configFile = resolveJsonFile(configDirectory, TIME_CONFIG_FILE_NAME);
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(configFile, defaults);
			TimeSettings loaded = TimeSettings.fromJson(normalized);
			JsonStaticSystem.writeManagedFile(configFile, loaded.toConfigJson(), defaults);
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
		private final long serverTicksPerDay;
		private final long serverTicksPerNight;
		private final long serverTicksPerCycle;
		private final int clockDayStartMinutes;
		private final int clockNightStartMinutes;
		private final int clockMidnightMinutes;
		private final long minecraftMidnightTick;
		private final long dayRolloverOffsetTicks;
		private final double dayTicksPerServerTick;
		private final double nightTicksPerServerTick;

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

			long minecraftDayStartTick = clockMinutesToMinecraftTicks(clockDayStartMinutes);
			long minecraftNightStartTick = clockMinutesToMinecraftTicks(clockNightStartMinutes);
			this.minecraftMidnightTick = clockMinutesToMinecraftTicks(clockMidnightMinutes);
			this.dayRolloverOffsetTicks = MINECRAFT_TICKS_PER_CYCLE - this.minecraftMidnightTick;

			long minecraftTicksPerDay = wrappedDuration(minecraftDayStartTick, minecraftNightStartTick);
			long minecraftTicksPerNight = MINECRAFT_TICKS_PER_CYCLE - minecraftTicksPerDay;
			this.dayTicksPerServerTick = minecraftTicksPerDay / (double) Math.max(1L, this.serverTicksPerDay);
			this.nightTicksPerServerTick = minecraftTicksPerNight / (double) Math.max(1L, this.serverTicksPerNight);
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

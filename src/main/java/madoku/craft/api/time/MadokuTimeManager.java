package madoku.craft.api.time;

import madoku.craft.api.MadokuAPIManager;
import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.season.MadokuSeason;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public final class MadokuTimeManager {
	public static final long TICKS_PER_SECOND = 20L;
	public static final long SECONDS_PER_MINUTE = 60L;
	public static final long MINECRAFT_TICKS_PER_CYCLE = 24000L;
	private static final long CYCLE_MINUTES_PER_DAY = 24L;
	private static final long MINUTES_PER_DAY = 24L * 60L;
	private static final int MINECRAFT_CLOCK_ZERO_OFFSET_MINUTES = 6 * 60;
	private static final String DEBUG_SUB_SYSTEM = "time-manager";
	private static final String TIME_WORLD_FOLDER_NAME = MadokuAPIManager.API_FOLDER_NAME + "/madoku-time";
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuTimeManager.class);

	private static volatile boolean managedGameRulesApplied = false;
	private static volatile long gameplayTicks = 0L;
	private static volatile long pendingSkippedTicks = 0L;
	private static volatile double timeAdjustmentCarry = 0.0D;
	private static volatile boolean hasObservedWorldTime = false;
	private static volatile long lastObservedWorldDayTime = 0L;
	private static volatile long lastObservedWorldTimeDelta = 0L;
	private static volatile boolean hasObservedGameplayTicks = false;
	private static volatile long lastObservedGameplayTicks = 0L;
	private static volatile long lastGameplayTickDelta = 0L;
	private MadokuTimeManager() {
	}

	public static void initialize() {
		TimeConfigManager.initialize();
		resetRuntimeState();
		emitTimeDebug("initialize", builder -> builder
			.subject("initialize")
			.field("enabled", isEnabled())
			.field("day-minutes", TimeConfigManager.getDayMinutes())
			.field("night-minutes", TimeConfigManager.getNightMinutes())
			.field("seasonal-changes-enabled", TimeConfigManager.isSeasonalChangesEnabled())
			.field("sleep-enabled", TimeConfigManager.isSleepEnabled()));
	}

	public static void reset() {
		long previousWorldTime = lastObservedWorldDayTime;
		long previousGameplayTicks = lastObservedGameplayTicks;
		resetRuntimeState();
		SleepManager.reset();
		emitTimeDebug("reset", builder -> builder
			.subject("reset")
			.field("world-time", previousWorldTime)
			.field("gameplay-ticks", previousGameplayTicks));
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server != null) {
			try {
				JsonManagerSystem.getOrCreateWorldSystemDirectory(server, TIME_WORLD_FOLDER_NAME);
			} catch (RuntimeException exception) {
				LOGGER.warn("Failed to prepare Madoku time world root directory.", exception);
			}
		}
		TimeConfigManager.initialize();
		observeRuntimeState(server);
		emitTimeDebug("loadPersistedData", builder -> builder
			.subject("load-persisted-data")
			.field("enabled", isEnabled())
			.field("world-time", getCurrentAbsoluteDayTime())
			.field("gameplay-ticks", getElapsedGameplayTicks()));
	}

	public static void autosavePersistedData(MinecraftServer server) {
		emitTimeDebug("autosavePersistedData", builder -> builder
			.subject("autosave")
			.field("enabled", isEnabled())
			.field("world-time", getCurrentAbsoluteDayTime())
			.field("gameplay-ticks", getElapsedGameplayTicks()));
	}

	public static void savePersistedData(MinecraftServer server) {
		emitTimeDebug("savePersistedData", builder -> builder
			.subject("save-persisted-data")
			.field("enabled", isEnabled())
			.field("world-time", getCurrentAbsoluteDayTime())
			.field("gameplay-ticks", getElapsedGameplayTicks()));
	}

	public static void onServerStarted(MinecraftServer server) {
		observeRuntimeState(server);
		emitTimeDebug("onServerStarted", builder -> builder
			.subject("server-started")
			.field("world-time", getCurrentAbsoluteDayTime())
			.field("gameplay-ticks", getElapsedGameplayTicks()));
	}

	public static void onServerStopping(MinecraftServer server) {
		restoreVanillaGameRules(server);
		emitTimeDebug("onServerStopping", builder -> builder
			.subject("server-stopping")
			.field("world-time", getCurrentAbsoluteDayTime())
			.field("gameplay-ticks", getElapsedGameplayTicks()));
	}

	public static void update(MinecraftServer server) {
		if (server == null) {
			return;
		}

		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return;
		}

		long gameplayTicks = getGameplayTicks();
		observeGameplayTicks(gameplayTicks);

		long observedDayTime = overworld.getOverworldClockTime();
		observeWorldTime(observedDayTime);

		if (!isEnabled()) {
			restoreVanillaGameRules(server);
			clearRuntimeAdjustments();
			return;
		}

		applyManagedGameRules(server);
		String seasonId = MadokuSeason.getCurrentSeasonId();
		int cycleMinute = getCycleMinutes(observedDayTime);
		boolean daytime = isDaytime(cycleMinute);
		double segmentMinutes = daytime
			? TimeConfigManager.getDayMinutes() * TimeConfigManager.getSeasonalDayMultiplier(seasonId)
			: TimeConfigManager.getNightMinutes() * TimeConfigManager.getSeasonalNightMultiplier(seasonId);
		long segmentWorldTicks = daytime ? resolveDayWorldTickSpan() : resolveNightWorldTickSpan();
		double desiredTicksPerServerTick = resolveDesiredTicksPerServerTick(daytime, segmentMinutes, segmentWorldTicks);
		double carryBefore = timeAdjustmentCarry;
		timeAdjustmentCarry += desiredTicksPerServerTick - 1.0D;

		long correctionTicks = takeWholeTicksFromCarry();
		long skippedTicks = consumePendingSkippedTicks();
		long totalAdjustment = safeAdd(correctionTicks, skippedTicks);
		double carryAfter = timeAdjustmentCarry;
		long adjustedDayTime = observedDayTime;
		if (totalAdjustment != 0L) {
			adjustedDayTime = applyWorldTimeDelta(server, observedDayTime, totalAdjustment);
			observeWorldTime(adjustedDayTime);
		}
		long finalDayTime = adjustedDayTime;
		SleepManager.onWorldTimeAdvanced(server, finalDayTime);
		emitTimeDebug("update", builder -> builder
			.subject("advance")
			.field("gameplay-delta", lastGameplayTickDelta)
			.field("world-delta", MadokuTimeManager.getWorldTimeDelta())
			.field("world-time", finalDayTime)
			.field("adjustment", totalAdjustment)
			.field("season", seasonId)
			.field("cycle-minute", cycleMinute)
			.field("daytime", daytime)
			.field("segment-minutes", segmentMinutes)
			.field("segment-world-ticks", segmentWorldTicks)
			.field("desired-ticks-per-server-tick", desiredTicksPerServerTick)
			.field("carry-before", carryBefore)
			.field("carry-after", carryAfter)
			.field("correction-ticks", correctionTicks)
			.field("skipped-ticks", skippedTicks));
	}

	public static long getElapsedGameplayTicks() {
		return Math.max(0L, getGameplayTicks());
	}

	public static long getGameplayTickDelta() {
		return lastGameplayTickDelta;
	}

	public static long getElapsedWorldTimeTicks() {
		return getCurrentAbsoluteDayTime();
	}

	public static long getWorldTimeDelta() {
		return lastObservedWorldTimeDelta;
	}

	public static long getCurrentAbsoluteDayTime() {
		if (hasObservedWorldTime) {
			return lastObservedWorldDayTime;
		}
		return getGameplayTicks();
	}

	public static long getCurrentAbsoluteDayTime(ServerLevel world) {
		if (world != null) {
			return world.getOverworldClockTime();
		}
		return getCurrentAbsoluteDayTime();
	}

	public static boolean isEnabled() {
		return TimeConfigManager.isDayCycleEnabled() && TimeConfigManager.isDayCycleTimeEnabled();
	}

	public static long getGameplayTicksPerDay() {
		return Math.max(1L, TimeConfigManager.getDayCycleTicks());
	}

	public static long getDay(long absoluteDayTime) {
		return Math.floorDiv(absoluteDayTime + dayRolloverOffsetTicks(), MINECRAFT_TICKS_PER_CYCLE);
	}

	public static long toAbsoluteDayTime(long day, int hour, int minute) {
		return toAbsoluteDayTime(day, Math.max(0, hour) * 60 + Math.max(0, minute));
	}

	public static long toAbsoluteDayTime(long day, int totalMinutes) {
		long normalizedDay = Math.max(0L, day);
		int minecraftMinutes = Math.floorMod(totalMinutes - MINECRAFT_CLOCK_ZERO_OFFSET_MINUTES, (int) MINUTES_PER_DAY);
		long timeOfDay = (minecraftMinutes * MINECRAFT_TICKS_PER_CYCLE) / MINUTES_PER_DAY;
		long dayCarry = Math.floorDiv(timeOfDay + dayRolloverOffsetTicks(), MINECRAFT_TICKS_PER_CYCLE);
		long completedCycles = normalizedDay - dayCarry;
		return completedCycles * MINECRAFT_TICKS_PER_CYCLE + timeOfDay;
	}

	public static void setClockFromAbsoluteDayTime(long absoluteDayTime) {
		observeWorldTime(Math.max(0L, absoluteDayTime));
	}

	public static void setGameplayTicks(long value) {
		gameplayTicks = Math.max(0L, value);
		observeGameplayTicks(Math.max(0L, value));
	}

	public static void setWorldTimeTicks(long value) {
		setClockFromAbsoluteDayTime(value);
	}

	public static void advanceSkippedTimeTicks(long amount) {
		if (!isEnabled() || amount <= 1L) {
			return;
		}

		long extraTicks = amount - 1L;
		pendingSkippedTicks = safeAdd(pendingSkippedTicks, extraTicks);
	}

	public static void tickGameplay() {
		gameplayTicks = safeAdd(gameplayTicks, 1L);
	}

	public static void advance(MinecraftServer server, long ignoredAmount) {
		if (server == null) {
			return;
		}

		tickGameplay();
		advanceSkippedTimeTicks(Math.max(1L, ignoredAmount));
	}

	public static long getGameplayTicks() {
		return Math.max(0L, gameplayTicks);
	}

	public static boolean isDaytime(long absoluteDayTime) {
		return isDaytime(getCycleMinutes(absoluteDayTime));
	}

	public static boolean isSleepTime(long absoluteDayTime) {
		return isSleepTime(getCycleMinutes(absoluteDayTime));
	}

	public static int getTotalMinutes(long absoluteDayTime) {
		long timeOfDay = Math.floorMod(absoluteDayTime, MINECRAFT_TICKS_PER_CYCLE);
		long minutesFromTick = (timeOfDay * MINUTES_PER_DAY) / MINECRAFT_TICKS_PER_CYCLE;
		return (int) ((minutesFromTick + MINECRAFT_CLOCK_ZERO_OFFSET_MINUTES) % MINUTES_PER_DAY);
	}

	public static int getCycleMinutes(long absoluteDayTime) {
		long timeOfDay = Math.floorMod(absoluteDayTime, MINECRAFT_TICKS_PER_CYCLE);
		long minutesFromTick = (timeOfDay * CYCLE_MINUTES_PER_DAY) / MINECRAFT_TICKS_PER_CYCLE;
		return (int) Math.floorMod(minutesFromTick, CYCLE_MINUTES_PER_DAY);
	}

	private static void observeRuntimeState(MinecraftServer server) {
		if (server == null) {
			return;
		}
		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return;
		}
		observeGameplayTicks(getGameplayTicks());
		observeWorldTime(overworld.getOverworldClockTime());
	}

	private static void observeGameplayTicks(long gameplayTicks) {
		if (hasObservedGameplayTicks) {
			lastGameplayTickDelta = gameplayTicks - lastObservedGameplayTicks;
		} else {
			lastGameplayTickDelta = 0L;
			hasObservedGameplayTicks = true;
		}
		lastObservedGameplayTicks = gameplayTicks;
	}

	private static void observeWorldTime(long absoluteDayTime) {
		if (hasObservedWorldTime) {
			lastObservedWorldTimeDelta = absoluteDayTime - lastObservedWorldDayTime;
		} else {
			lastObservedWorldTimeDelta = 0L;
			hasObservedWorldTime = true;
		}
		lastObservedWorldDayTime = absoluteDayTime;
	}

	private static void resetRuntimeState() {
		gameplayTicks = 0L;
		managedGameRulesApplied = false;
		pendingSkippedTicks = 0L;
		timeAdjustmentCarry = 0.0D;
		hasObservedWorldTime = false;
		lastObservedWorldDayTime = 0L;
		lastObservedWorldTimeDelta = 0L;
		hasObservedGameplayTicks = false;
		lastObservedGameplayTicks = 0L;
		lastGameplayTickDelta = 0L;
	}

	private static void clearRuntimeAdjustments() {
		pendingSkippedTicks = 0L;
		timeAdjustmentCarry = 0.0D;
	}

	private static void applyManagedGameRules(MinecraftServer server) {
		if (managedGameRulesApplied || server == null) {
			return;
		}
		for (ServerLevel world : server.getAllLevels()) {
			world.getGameRules().set(GameRules.ADVANCE_TIME, true, server);
		}
		managedGameRulesApplied = true;
	}

	private static void restoreVanillaGameRules(MinecraftServer server) {
		if (server != null) {
			for (ServerLevel world : server.getAllLevels()) {
				world.getGameRules().set(GameRules.ADVANCE_TIME, true, server);
			}
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
		int startInclusive = TimeConfigManager.getMorningMinutes();
		int endExclusive = TimeConfigManager.getNightMinutesTransition();
		return isWithinWrappedRange(totalMinutes, startInclusive, endExclusive);
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

	private static int wrappedClockMinutes(int startMinutes, int endMinutes) {
		return Math.floorMod(endMinutes - startMinutes, (int) CYCLE_MINUTES_PER_DAY);
	}

	private static boolean isWithinWrappedRange(int value, int startInclusive, int endExclusive) {
		int span = wrappedClockMinutes(startInclusive, endExclusive);
		if (span <= 0 || span >= CYCLE_MINUTES_PER_DAY) {
			return false;
		}
		int offset = Math.floorMod(value - startInclusive, (int) CYCLE_MINUTES_PER_DAY);
		return offset < span;
	}

	private static long dayRolloverOffsetTicks() {
		long midnightMinutes = Math.max(0L, Math.min(23L, TimeConfigManager.getMidnightMinutes()));
		long minecraftMidnightTick = cycleMinutesToMinecraftTicks((int) midnightMinutes);
		return MINECRAFT_TICKS_PER_CYCLE - minecraftMidnightTick;
	}

	private static double resolveDesiredTicksPerServerTick(boolean daytime, double segmentMinutes, long segmentWorldTicks) {
		if (!Double.isFinite(segmentMinutes) || segmentMinutes <= 0.0D) {
			return 1.0D;
		}
		if (segmentWorldTicks <= 0L) {
			return 1.0D;
		}
		return segmentWorldTicks / (segmentMinutes * TICKS_PER_SECOND * SECONDS_PER_MINUTE);
	}

	private static long resolveDayWorldTickSpan() {
		long morningTicks = cycleMinutesToMinecraftTicks(TimeConfigManager.getMorningMinutes());
		long nightTicks = cycleMinutesToMinecraftTicks(TimeConfigManager.getNightMinutesTransition());
		long span = Math.floorMod(nightTicks - morningTicks, MINECRAFT_TICKS_PER_CYCLE);
		if (span <= 0L || span >= MINECRAFT_TICKS_PER_CYCLE) {
			return MINECRAFT_TICKS_PER_CYCLE / 2L;
		}
		return span;
	}

	private static long resolveNightWorldTickSpan() {
		return MINECRAFT_TICKS_PER_CYCLE - resolveDayWorldTickSpan();
	}

	private static long cycleMinutesToMinecraftTicks(int cycleMinutes) {
		int minecraftMinutes = Math.floorMod(cycleMinutes, (int) CYCLE_MINUTES_PER_DAY);
		return (minecraftMinutes * MINECRAFT_TICKS_PER_CYCLE) / CYCLE_MINUTES_PER_DAY;
	}

	private static void emitTimeDebug(String metricId, Consumer<MadokuDebugManager.EventBuilder> customizer) {
		String entry = MadokuDebugManager.resolveCallerMethodName(1);
		if (!MadokuDebugManager.shouldEmit("api", DEBUG_SUB_SYSTEM, entry)) {
			return;
		}
		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(metricId, "api", DEBUG_SUB_SYSTEM, entry)
			.side(MadokuDebugManager.Side.SERVER);
		if (customizer != null) {
			customizer.accept(builder);
		}
		builder.log();
	}
}

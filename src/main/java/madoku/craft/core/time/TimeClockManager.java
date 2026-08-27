package madoku.craft.core.time;

import madoku.craft.core.season.MadokuSeasonManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ClockTimeMarkers;


public final class TimeClockManager {
	public static final long TICKS_PER_SECOND = 20L;
	public static final long SECONDS_PER_MINUTE = 60L;
	public static final long MINECRAFT_TICKS_PER_CYCLE = 24000L;
	private static final long CYCLE_MINUTES_PER_DAY = 24L;
	private static final long MINUTES_PER_DAY = 24L * 60L;
	private static final int MINECRAFT_CLOCK_ZERO_OFFSET_MINUTES = 6 * 60;
	private static volatile long gameplayTicks = 0L;
	private static volatile boolean hasObservedWorldTime = false;
	private static volatile long lastObservedWorldDayTime = 0L;
	private static volatile long lastObservedWorldTimeDelta = 0L;
	private static volatile boolean hasObservedGameplayTicks = false;
	private static volatile long lastObservedGameplayTicks = 0L;
	private static volatile long lastGameplayTickDelta = 0L;

	private TimeClockManager() {
	}

	public static void initialize() {
		resetRuntimeState();
	}

	public static void reset() {
		resetRuntimeState();
	}

	public static void onServerStarted(MinecraftServer server) {
		observeRuntimeState(server);
	}

	public static void onServerStopping(MinecraftServer server) {
	}

	public static void update(MinecraftServer server) {
		if (server == null) {
			return;
		}

		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return;
		}

		observeGameplayTicks(getGameplayTicks());
		long observedDayTime = overworld.getOverworldClockTime();
		observeWorldTime(observedDayTime);
		TimeSleepManager.onWorldTimeAdvanced(server, observedDayTime);

		if (!isEnabled()) {
			return;
		}

	}

	public static float resolveWorldClockRate(MinecraftServer server) {
		if (server == null || !isEnabled()) {
			return 1.0F;
		}

		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return 1.0F;
		}

		long currentDayTime = overworld.getOverworldClockTime();
		String seasonId = MadokuSeasonManager.getCurrentSeasonId();
		int clockHour = getClockHour(currentDayTime);
		boolean daytime = isDaytime(clockHour);
		double segmentMinutes = daytime
			? TimeConfigManager.getDayMinutes() * TimeConfigManager.getSeasonalDayMultiplier(seasonId)
			: TimeConfigManager.getNightMinutes() * TimeConfigManager.getSeasonalNightMultiplier(seasonId);
		long segmentWorldTicks = daytime ? resolveDayWorldTickSpan() : resolveNightWorldTickSpan();
		double baseRate = resolveDesiredTicksPerServerTick(segmentMinutes, segmentWorldTicks);
		double sleepMultiplier = Math.max(1L, TimeSleepManager.getCachedTickIncrement());
		double resolvedRate = baseRate * sleepMultiplier;
		if (!Double.isFinite(resolvedRate) || resolvedRate <= 0.0D) {
			return 1.0F;
		}
		return (float) resolvedRate;
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
		long oneBasedDay = Math.floorDiv(absoluteDayTime + dayRolloverOffsetTicks(), MINECRAFT_TICKS_PER_CYCLE);
		return Math.max(0L, oneBasedDay - 1L);
	}

	public static long toAbsoluteDayTime(long day, int hour, int minute) {
		return toAbsoluteDayTime(day, Math.max(0, hour) * 60 + Math.max(0, minute));
	}

	public static long toAbsoluteDayTime(long day, int totalMinutes) {
		long normalizedDay = Math.max(0L, day);
		int minecraftMinutes = Math.floorMod(totalMinutes - MINECRAFT_CLOCK_ZERO_OFFSET_MINUTES, (int) MINUTES_PER_DAY);
		long timeOfDay = (minecraftMinutes * MINECRAFT_TICKS_PER_CYCLE) / MINUTES_PER_DAY;
		long oneBasedDayCarry = Math.floorDiv(timeOfDay + dayRolloverOffsetTicks(), MINECRAFT_TICKS_PER_CYCLE);
		long completedCycles = normalizedDay + 1L - oneBasedDayCarry;
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

	public static void tickGameplay() {
		gameplayTicks = safeAdd(gameplayTicks, 1L);
	}

	public static void advance(MinecraftServer server, long ignoredAmount) {
		if (server == null) {
			return;
		}

		tickGameplay();
	}

	public static long getGameplayTicks() {
		return Math.max(0L, gameplayTicks);
	}

	public static boolean isDaytime(long absoluteDayTime) {
		return isDaytime(getClockHour(absoluteDayTime));
	}

	public static boolean isSleepTime(long absoluteDayTime) {
		return isSleepTime(getClockHour(absoluteDayTime));
	}

	public static int getTotalMinutes(long absoluteDayTime) {
		long timeOfDay = Math.floorMod(absoluteDayTime, MINECRAFT_TICKS_PER_CYCLE);
		long minutesFromTick = (timeOfDay * MINUTES_PER_DAY) / MINECRAFT_TICKS_PER_CYCLE;
		return (int) ((minutesFromTick + MINECRAFT_CLOCK_ZERO_OFFSET_MINUTES) % MINUTES_PER_DAY);
	}

	public static int getClockHour(long absoluteDayTime) {
		return Math.floorDiv(getTotalMinutes(absoluteDayTime), 60);
	}

	public static long resolveClockHourToMinecraftTimeTicks(int clockHour) {
		int clockMinutes = Math.floorMod(clockHour, (int) CYCLE_MINUTES_PER_DAY) * 60;
		int minecraftMinutes = Math.floorMod(clockMinutes - MINECRAFT_CLOCK_ZERO_OFFSET_MINUTES, (int) MINUTES_PER_DAY);
		return (minecraftMinutes * MINECRAFT_TICKS_PER_CYCLE) / MINUTES_PER_DAY;
	}

	public static long resolveConfiguredTimeMarkerTicks(ResourceKey<ClockTimeMarker> markerKey) {
		if (!TimeConfigManager.isSleepTimeTransitionsEnabled() || markerKey == null) {
			return -1L;
		}
		if (ClockTimeMarkers.DAY.equals(markerKey)) {
			return resolveClockHourToMinecraftTimeTicks(TimeConfigManager.getMorningMinutes());
		}
		if (ClockTimeMarkers.NOON.equals(markerKey)) {
			return resolveClockHourToMinecraftTimeTicks(TimeConfigManager.getNoonMinutes());
		}
		if (ClockTimeMarkers.NIGHT.equals(markerKey)) {
			return resolveClockHourToMinecraftTimeTicks(TimeConfigManager.getNightMinutesTransition());
		}
		if (ClockTimeMarkers.MIDNIGHT.equals(markerKey)) {
			return resolveClockHourToMinecraftTimeTicks(TimeConfigManager.getMidnightMinutes());
		}
		return -1L;
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

	private static void observeGameplayTicks(long value) {
		if (hasObservedGameplayTicks) {
			lastGameplayTickDelta = value - lastObservedGameplayTicks;
		} else {
			lastGameplayTickDelta = 0L;
			hasObservedGameplayTicks = true;
		}
		lastObservedGameplayTicks = value;
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
		hasObservedWorldTime = false;
		lastObservedWorldDayTime = 0L;
		lastObservedWorldTimeDelta = 0L;
		hasObservedGameplayTicks = false;
		lastObservedGameplayTicks = 0L;
		lastGameplayTickDelta = 0L;
	}

	private static boolean isDaytime(int totalMinutes) {
		int startInclusive = TimeConfigManager.getMorningMinutes();
		int endExclusive = TimeConfigManager.getNightMinutesTransition();
		return isWithinWrappedRange(totalMinutes, startInclusive, endExclusive);
	}

	private static boolean isSleepTime(int totalMinutes) {
		return !isDaytime(totalMinutes);
	}

	private static long dayRolloverOffsetTicks() {
		long midnightMinutes = Math.max(0L, Math.min(23L, TimeConfigManager.getMidnightMinutes()));
		long minecraftMidnightTick = cycleMinutesToMinecraftTicks((int) midnightMinutes);
		return MINECRAFT_TICKS_PER_CYCLE - minecraftMidnightTick;
	}

	private static double resolveDesiredTicksPerServerTick(double segmentMinutes, long segmentWorldTicks) {
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

	private static boolean isWithinWrappedRange(int value, int startInclusive, int endExclusive) {
		int span = wrappedClockMinutes(startInclusive, endExclusive);
		if (span <= 0 || span >= CYCLE_MINUTES_PER_DAY) {
			return false;
		}
		int offset = Math.floorMod(value - startInclusive, (int) CYCLE_MINUTES_PER_DAY);
		return offset < span;
	}

	private static int wrappedClockMinutes(int startMinutes, int endMinutes) {
		return Math.floorMod(endMinutes - startMinutes, (int) CYCLE_MINUTES_PER_DAY);
	}

	private static long safeAdd(long base, long delta) {
		try {
			return Math.addExact(base, delta);
		} catch (ArithmeticException exception) {
			return delta >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
		}
	}

}

package madoku.craft.api.time;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ClockTimeMarker;

/** Runtime API subsystem orchestrating the time runtime, sleep, and time configuration groups. */
public final class MadokuTimeManager {
	public static final long TICKS_PER_SECOND = TimeManager.TICKS_PER_SECOND;
	public static final long SECONDS_PER_MINUTE = TimeManager.SECONDS_PER_MINUTE;
	public static final long MINECRAFT_TICKS_PER_CYCLE = TimeManager.MINECRAFT_TICKS_PER_CYCLE;

	private MadokuTimeManager() {
	}

	public static void initialize() {
		TimeConfigManager.initialize();
		TimeManager.initialize();
		SleepManager.reset();
	}

	public static void reset() {
		TimeManager.reset();
		SleepManager.reset();
	}

	public static void onServerStarted(MinecraftServer server) {
		TimeManager.onServerStarted(server);
	}

	public static void onServerStopping(MinecraftServer server) {
		TimeManager.onServerStopping(server);
	}

	public static void update(MinecraftServer server) {
		TimeManager.update(server);
	}

	public static float resolveWorldClockRate(MinecraftServer server) {
		return TimeManager.resolveWorldClockRate(server);
	}

	public static long getElapsedGameplayTicks() {
		return TimeManager.getElapsedGameplayTicks();
	}

	public static long getGameplayTickDelta() {
		return TimeManager.getGameplayTickDelta();
	}

	public static long getElapsedWorldTimeTicks() {
		return TimeManager.getElapsedWorldTimeTicks();
	}

	public static long getWorldTimeDelta() {
		return TimeManager.getWorldTimeDelta();
	}

	public static long getCurrentAbsoluteDayTime() {
		return TimeManager.getCurrentAbsoluteDayTime();
	}

	public static long getCurrentAbsoluteDayTime(ServerLevel world) {
		return TimeManager.getCurrentAbsoluteDayTime(world);
	}

	public static boolean isEnabled() {
		return TimeManager.isEnabled();
	}

	public static long getGameplayTicksPerDay() {
		return TimeManager.getGameplayTicksPerDay();
	}

	public static long getDay(long absoluteDayTime) {
		return TimeManager.getDay(absoluteDayTime);
	}

	public static long toAbsoluteDayTime(long day, int hour, int minute) {
		return TimeManager.toAbsoluteDayTime(day, hour, minute);
	}

	public static long toAbsoluteDayTime(long day, int totalMinutes) {
		return TimeManager.toAbsoluteDayTime(day, totalMinutes);
	}

	public static void setClockFromAbsoluteDayTime(long absoluteDayTime) {
		TimeManager.setClockFromAbsoluteDayTime(absoluteDayTime);
	}

	public static void setGameplayTicks(long value) {
		TimeManager.setGameplayTicks(value);
	}

	public static void setWorldTimeTicks(long value) {
		TimeManager.setWorldTimeTicks(value);
	}

	public static void tickGameplay() {
		TimeManager.tickGameplay();
	}

	public static void advance(MinecraftServer server, long ignoredAmount) {
		TimeManager.advance(server, ignoredAmount);
	}

	public static long getGameplayTicks() {
		return TimeManager.getGameplayTicks();
	}

	public static boolean isDaytime(long absoluteDayTime) {
		return TimeManager.isDaytime(absoluteDayTime);
	}

	public static boolean isSleepTime(long absoluteDayTime) {
		return TimeManager.isSleepTime(absoluteDayTime);
	}

	public static int getTotalMinutes(long absoluteDayTime) {
		return TimeManager.getTotalMinutes(absoluteDayTime);
	}

	public static int getClockHour(long absoluteDayTime) {
		return TimeManager.getClockHour(absoluteDayTime);
	}

	public static long resolveClockHourToMinecraftTimeTicks(int clockHour) {
		return TimeManager.resolveClockHourToMinecraftTimeTicks(clockHour);
	}

	public static long resolveConfiguredTimeMarkerTicks(ResourceKey<ClockTimeMarker> markerKey) {
		return TimeManager.resolveConfiguredTimeMarkerTicks(markerKey);
	}

	public static int getCycleMinutes(long absoluteDayTime) {
		return TimeManager.getCycleMinutes(absoluteDayTime);
	}
}

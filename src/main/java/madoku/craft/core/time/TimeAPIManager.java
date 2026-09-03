package madoku.craft.core.time;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Public contract for the Madoku Time subsystem. */
public final class TimeAPIManager {
	public static final long TICKS_PER_SECOND = MadokuTimeManager.TICKS_PER_SECOND;
	public static final long SECONDS_PER_MINUTE = MadokuTimeManager.SECONDS_PER_MINUTE;
	public static final long MINECRAFT_TICKS_PER_CYCLE = MadokuTimeManager.MINECRAFT_TICKS_PER_CYCLE;

	private TimeAPIManager() {
	}

	public static void initialize() { MadokuTimeManager.initialize(); }
	public static void reset() { MadokuTimeManager.reset(); }
	public static void onServerStarted(MinecraftServer server) { MadokuTimeManager.onServerStarted(server); }
	public static void onServerStopping(MinecraftServer server) { MadokuTimeManager.onServerStopping(server); }
	public static void update(MinecraftServer server) { MadokuTimeManager.update(server); }
	public static float resolveWorldClockRate(MinecraftServer server) { return MadokuTimeManager.resolveWorldClockRate(server); }
	public static long getElapsedGameplayTicks() { return MadokuTimeManager.getElapsedGameplayTicks(); }
	public static long getGameplayTickDelta() { return MadokuTimeManager.getGameplayTickDelta(); }
	public static long getElapsedWorldTimeTicks() { return MadokuTimeManager.getElapsedWorldTimeTicks(); }
	public static long getWorldTimeDelta() { return MadokuTimeManager.getWorldTimeDelta(); }
	public static long getCurrentAbsoluteDayTime() { return MadokuTimeManager.getCurrentAbsoluteDayTime(); }
	public static long getCurrentAbsoluteDayTime(ServerLevel world) { return MadokuTimeManager.getCurrentAbsoluteDayTime(world); }
	public static boolean isEnabled() { return MadokuTimeManager.isEnabled(); }
	public static long getGameplayTicksPerDay() { return MadokuTimeManager.getGameplayTicksPerDay(); }
	public static long getDay(long absoluteDayTime) { return MadokuTimeManager.getDay(absoluteDayTime); }
	public static long toAbsoluteDayTime(long day, int hour, int minute) { return MadokuTimeManager.toAbsoluteDayTime(day, hour, minute); }
	public static long toAbsoluteDayTime(long day, int totalMinutes) { return MadokuTimeManager.toAbsoluteDayTime(day, totalMinutes); }
	public static void setClockFromAbsoluteDayTime(long absoluteDayTime) { MadokuTimeManager.setClockFromAbsoluteDayTime(absoluteDayTime); }
	public static void setGameplayTicks(long value) { MadokuTimeManager.setGameplayTicks(value); }
	public static void setWorldTimeTicks(long value) { MadokuTimeManager.setWorldTimeTicks(value); }
	public static void tickGameplay() { MadokuTimeManager.tickGameplay(); }
	public static void advance(MinecraftServer server, long ignoredAmount) { MadokuTimeManager.advance(server, ignoredAmount); }
	public static long getGameplayTicks() { return MadokuTimeManager.getGameplayTicks(); }
	public static boolean isDaytime(long absoluteDayTime) { return MadokuTimeManager.isDaytime(absoluteDayTime); }
	public static boolean isSleepTime(long absoluteDayTime) { return MadokuTimeManager.isSleepTime(absoluteDayTime); }
	public static int getTotalMinutes(long absoluteDayTime) { return MadokuTimeManager.getTotalMinutes(absoluteDayTime); }
	public static int getClockHour(long absoluteDayTime) { return MadokuTimeManager.getClockHour(absoluteDayTime); }
	public static long resolveClockHourToMinecraftTimeTicks(int clockHour) { return MadokuTimeManager.resolveClockHourToMinecraftTimeTicks(clockHour); }
	public static long resolveConfiguredTimeMarkerTicks(ResourceKey<ClockTimeMarker> markerKey) { return MadokuTimeManager.resolveConfiguredTimeMarkerTicks(markerKey); }
	public static int getCycleMinutes(long absoluteDayTime) { return MadokuTimeManager.getCycleMinutes(absoluteDayTime); }
	public static void broadcastWorldTimeNow(MinecraftServer server) { MadokuTimeManager.broadcastWorldTimeNow(server); }
	public static void broadcastWorldTimeIfChanged(MinecraftServer server) { MadokuTimeManager.broadcastWorldTimeIfChanged(server); }

	public static boolean isSleepEnabled() { return TimeSleepManager.isEnabled(); }
	public static boolean shouldAllowResettingTime(Player player) { return TimeSleepManager.shouldAllowResettingTime(player); }
	public static long refreshSleepTickIncrement(MinecraftServer server) { return TimeSleepManager.refreshTickIncrement(server); }
	public static long refreshTickIncrement(MinecraftServer server) { return TimeSleepManager.refreshTickIncrement(server); }
	public static long getSleepTickIncrement(MinecraftServer server) { return TimeSleepManager.getTickIncrement(server); }
	public static long getTickIncrement(MinecraftServer server) { return TimeSleepManager.getTickIncrement(server); }
	public static long getCachedSleepTickIncrement() { return TimeSleepManager.getCachedTickIncrement(); }
	public static long getCachedTickIncrement() { return TimeSleepManager.getCachedTickIncrement(); }
	public static boolean canStartSleeping(Player player) { return TimeSleepManager.canStartSleeping(player); }
	public static boolean shouldAllowBedSleepByTime(BedRule bedRule, Level level, Player player) { return TimeSleepManager.shouldAllowBedSleepByTime(bedRule, level, player); }
	public static boolean shouldKeepSleepingWhileForwarding(BedRule bedRule, Level level, Player player) { return TimeSleepManager.shouldKeepSleepingWhileForwarding(bedRule, level, player); }
	public static void onSleepStarted(ServerPlayer player) { TimeSleepManager.onSleepStarted(player); }
	public static boolean isThunderstormBypassEnabled() { return TimeConfigManager.isThunderstormBypassEnabled(); }
}

package madoku.craft.java.core.time;

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
	public static final long TICKS_PER_SECOND = 20L;
	public static final long SECONDS_PER_MINUTE = 60L;
	public static final long MINECRAFT_TICKS_PER_CYCLE = 24_000L;
	private static final TimeProvider UNAVAILABLE_PROVIDER = new TimeProvider() { };
	private static volatile TimeProvider provider = UNAVAILABLE_PROVIDER;

	private TimeAPIManager() { }

	public static void registerProvider(TimeProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Time provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static void onServerStarted(MinecraftServer server) { provider.onServerStarted(server); }
	public static void onServerStopping(MinecraftServer server) { provider.onServerStopping(server); }
	public static void update(MinecraftServer server) { provider.update(server); }
	public static float resolveWorldClockRate(MinecraftServer server) { return provider.resolveWorldClockRate(server); }
	public static long getElapsedGameplayTicks() { return provider.getElapsedGameplayTicks(); }
	public static long getGameplayTickDelta() { return provider.getGameplayTickDelta(); }
	public static long getElapsedWorldTimeTicks() { return provider.getElapsedWorldTimeTicks(); }
	public static long getWorldTimeDelta() { return provider.getWorldTimeDelta(); }
	public static long getCurrentAbsoluteDayTime() { return provider.getCurrentAbsoluteDayTime(); }
	public static long getCurrentAbsoluteDayTime(ServerLevel world) { return provider.getCurrentAbsoluteDayTime(world); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static long getGameplayTicksPerDay() { return provider.getGameplayTicksPerDay(); }
	public static long getDay(long absoluteDayTime) { return provider.getDay(absoluteDayTime); }
	public static long toAbsoluteDayTime(long day, int hour, int minute) { return provider.toAbsoluteDayTime(day, hour, minute); }
	public static long toAbsoluteDayTime(long day, int totalMinutes) { return provider.toAbsoluteDayTime(day, totalMinutes); }
	public static void setClockFromAbsoluteDayTime(long absoluteDayTime) { provider.setClockFromAbsoluteDayTime(absoluteDayTime); }
	public static void setGameplayTicks(long value) { provider.setGameplayTicks(value); }
	public static void setWorldTimeTicks(long value) { provider.setWorldTimeTicks(value); }
	public static void tickGameplay() { provider.tickGameplay(); }
	public static void advance(MinecraftServer server, long ignoredAmount) { provider.advance(server, ignoredAmount); }
	public static long getGameplayTicks() { return provider.getGameplayTicks(); }
	public static boolean isDaytime(long absoluteDayTime) { return provider.isDaytime(absoluteDayTime); }
	public static boolean isSleepTime(long absoluteDayTime) { return provider.isSleepTime(absoluteDayTime); }
	public static int getTotalMinutes(long absoluteDayTime) { return provider.getTotalMinutes(absoluteDayTime); }
	public static int getClockHour(long absoluteDayTime) { return provider.getClockHour(absoluteDayTime); }
	public static long resolveClockHourToMinecraftTimeTicks(int clockHour) { return provider.resolveClockHourToMinecraftTimeTicks(clockHour); }
	public static long resolveConfiguredTimeMarkerTicks(ResourceKey<ClockTimeMarker> markerKey) { return provider.resolveConfiguredTimeMarkerTicks(markerKey); }
	public static int getCycleMinutes(long absoluteDayTime) { return provider.getCycleMinutes(absoluteDayTime); }
	public static void broadcastWorldTimeNow(MinecraftServer server) { provider.broadcastWorldTimeNow(server); }
	public static void broadcastWorldTimeIfChanged(MinecraftServer server) { provider.broadcastWorldTimeIfChanged(server); }
	public static boolean isSleepEnabled() { return provider.isSleepEnabled(); }
	public static boolean shouldAllowResettingTime(Player player) { return provider.shouldAllowResettingTime(player); }
	public static long refreshSleepTickIncrement(MinecraftServer server) { return provider.refreshSleepTickIncrement(server); }
	public static long refreshTickIncrement(MinecraftServer server) { return provider.refreshTickIncrement(server); }
	public static long getSleepTickIncrement(MinecraftServer server) { return provider.getSleepTickIncrement(server); }
	public static long getTickIncrement(MinecraftServer server) { return provider.getTickIncrement(server); }
	public static long getCachedSleepTickIncrement() { return provider.getCachedSleepTickIncrement(); }
	public static long getCachedTickIncrement() { return provider.getCachedTickIncrement(); }
	public static boolean canStartSleeping(Player player) { return provider.canStartSleeping(player); }
	public static boolean shouldAllowBedSleepByTime(BedRule bedRule, Level level, Player player) { return provider.shouldAllowBedSleepByTime(bedRule, level, player); }
	public static boolean shouldKeepSleepingWhileForwarding(BedRule bedRule, Level level, Player player) { return provider.shouldKeepSleepingWhileForwarding(bedRule, level, player); }
	public static void onSleepStarted(ServerPlayer player) { provider.onSleepStarted(player); }
	public static boolean isThunderstormBypassEnabled() { return provider.isThunderstormBypassEnabled(); }
}

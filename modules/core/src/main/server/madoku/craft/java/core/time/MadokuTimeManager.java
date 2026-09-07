package madoku.craft.java.core.time;

import madoku.craft.java.core.sync.SyncWorldAPIManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockTimeMarker;

/** Runtime API subsystem orchestrating the time runtime, sleep, and time configuration groups. */
public final class MadokuTimeManager {
	public static final long TICKS_PER_SECOND = TimeClockManager.TICKS_PER_SECOND;
	public static final long SECONDS_PER_MINUTE = TimeClockManager.SECONDS_PER_MINUTE;
	public static final long MINECRAFT_TICKS_PER_CYCLE = TimeClockManager.MINECRAFT_TICKS_PER_CYCLE;
	private static long lastSyncDay = -1L;
	private static long lastSyncTotalMinutes = -1L;

	private MadokuTimeManager() {
	}

	public static void initialize() {
		TimeConfigManager.initialize();
		TimeClockManager.initialize();
		TimeSleepManager.reset();
		resetSyncState();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			TimePayloadAPIManager payload = currentSyncPayload(server);
			if (payload != null) {
				SyncWorldAPIManager.send(handler.player, payload);
			}
		});
	}

	public static void reset() {
		TimeClockManager.reset();
		TimeSleepManager.reset();
		resetSyncState();
	}

	public static void onServerStarted(MinecraftServer server) {
		TimeClockManager.onServerStarted(server);
	}

	public static void onServerStopping(MinecraftServer server) {
		TimeClockManager.onServerStopping(server);
	}

	public static void update(MinecraftServer server) {
		TimeClockManager.update(server);
	}

	public static float resolveWorldClockRate(MinecraftServer server) {
		return TimeClockManager.resolveWorldClockRate(server);
	}

	public static long getElapsedGameplayTicks() {
		return TimeClockManager.getElapsedGameplayTicks();
	}

	public static long getGameplayTickDelta() {
		return TimeClockManager.getGameplayTickDelta();
	}

	public static long getElapsedWorldTimeTicks() {
		return TimeClockManager.getElapsedWorldTimeTicks();
	}

	public static long getWorldTimeDelta() {
		return TimeClockManager.getWorldTimeDelta();
	}

	public static long getCurrentAbsoluteDayTime() {
		return TimeClockManager.getCurrentAbsoluteDayTime();
	}

	public static long getCurrentAbsoluteDayTime(ServerLevel world) {
		return TimeClockManager.getCurrentAbsoluteDayTime(world);
	}

	public static boolean isEnabled() {
		return TimeClockManager.isEnabled();
	}

	public static long getGameplayTicksPerDay() {
		return TimeClockManager.getGameplayTicksPerDay();
	}

	public static long getDay(long absoluteDayTime) {
		return TimeClockManager.getDay(absoluteDayTime);
	}

	public static long toAbsoluteDayTime(long day, int hour, int minute) {
		return TimeClockManager.toAbsoluteDayTime(day, hour, minute);
	}

	public static long toAbsoluteDayTime(long day, int totalMinutes) {
		return TimeClockManager.toAbsoluteDayTime(day, totalMinutes);
	}

	public static void setClockFromAbsoluteDayTime(long absoluteDayTime) {
		TimeClockManager.setClockFromAbsoluteDayTime(absoluteDayTime);
	}

	public static void setGameplayTicks(long value) {
		TimeClockManager.setGameplayTicks(value);
	}

	public static void setWorldTimeTicks(long value) {
		TimeClockManager.setWorldTimeTicks(value);
	}

	public static void tickGameplay() {
		TimeClockManager.tickGameplay();
	}

	public static void advance(MinecraftServer server, long ignoredAmount) {
		TimeClockManager.advance(server, ignoredAmount);
	}

	public static long getGameplayTicks() {
		return TimeClockManager.getGameplayTicks();
	}

	public static boolean isDaytime(long absoluteDayTime) {
		return TimeClockManager.isDaytime(absoluteDayTime);
	}

	public static boolean isSleepTime(long absoluteDayTime) {
		return TimeClockManager.isSleepTime(absoluteDayTime);
	}

	public static int getTotalMinutes(long absoluteDayTime) {
		return TimeClockManager.getTotalMinutes(absoluteDayTime);
	}

	public static int getClockHour(long absoluteDayTime) {
		return TimeClockManager.getClockHour(absoluteDayTime);
	}

	public static long resolveClockHourToMinecraftTimeTicks(int clockHour) {
		return TimeClockManager.resolveClockHourToMinecraftTimeTicks(clockHour);
	}

	public static long resolveConfiguredTimeMarkerTicks(ResourceKey<ClockTimeMarker> markerKey) {
		return TimeClockManager.resolveConfiguredTimeMarkerTicks(markerKey);
	}

	public static int getCycleMinutes(long absoluteDayTime) {
		return TimeClockManager.getCycleMinutes(absoluteDayTime);
	}

	public static void broadcastWorldTimeNow(MinecraftServer server) {
		broadcastWorldTime(server, true);
	}

	public static void broadcastWorldTimeIfChanged(MinecraftServer server) {
		broadcastWorldTime(server, false);
	}

	private static void broadcastWorldTime(MinecraftServer server, boolean force) {
		TimePayloadAPIManager payload = currentSyncPayload(server);
		if (payload == null || server == null) {
			return;
		}

		long totalMinutes = (long) payload.hour() * 60L + payload.minute();
		if (!force && payload.day() == lastSyncDay && totalMinutes == lastSyncTotalMinutes) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			SyncWorldAPIManager.send(player, payload);
		}
		lastSyncDay = payload.day();
		lastSyncTotalMinutes = totalMinutes;
	}

	private static void resetSyncState() {
		lastSyncDay = -1L;
		lastSyncTotalMinutes = -1L;
	}

	public static boolean isSleepEnabled() { return TimeSleepManager.isEnabled(); }
	public static boolean shouldAllowResettingTime(net.minecraft.world.entity.player.Player player) { return TimeSleepManager.shouldAllowResettingTime(player); }
	public static long refreshSleepTickIncrement(MinecraftServer server) { return TimeSleepManager.refreshTickIncrement(server); }
	public static long refreshTickIncrement(MinecraftServer server) { return TimeSleepManager.refreshTickIncrement(server); }
	public static long getSleepTickIncrement(MinecraftServer server) { return TimeSleepManager.getTickIncrement(server); }
	public static long getTickIncrement(MinecraftServer server) { return TimeSleepManager.getTickIncrement(server); }
	public static long getCachedSleepTickIncrement() { return TimeSleepManager.getCachedTickIncrement(); }
	public static long getCachedTickIncrement() { return TimeSleepManager.getCachedTickIncrement(); }
	public static boolean canStartSleeping(net.minecraft.world.entity.player.Player player) { return TimeSleepManager.canStartSleeping(player); }
	public static boolean shouldAllowBedSleepByTime(net.minecraft.world.attribute.BedRule bedRule, net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player) { return TimeSleepManager.shouldAllowBedSleepByTime(bedRule, level, player); }
	public static boolean shouldKeepSleepingWhileForwarding(net.minecraft.world.attribute.BedRule bedRule, net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player) { return TimeSleepManager.shouldKeepSleepingWhileForwarding(bedRule, level, player); }
	public static void onSleepStarted(ServerPlayer player) { TimeSleepManager.onSleepStarted(player); }
	public static boolean isThunderstormBypassEnabled() { return TimeConfigManager.isThunderstormBypassEnabled(); }

	private static TimePayloadAPIManager currentSyncPayload(MinecraftServer server) {
		if (server == null || server.overworld() == null) {
			return null;
		}

		long dayTime = server.overworld().getOverworldClockTime();
		long day = getDay(dayTime);
		int totalMinutes = getTotalMinutes(dayTime);
		return new TimePayloadAPIManager(day, totalMinutes / 60, totalMinutes % 60);
	}
}

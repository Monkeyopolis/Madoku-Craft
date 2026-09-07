package madoku.craft.java.core.time;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Provider contract implemented by the module that owns Madoku time. */
public interface TimeProvider {
	default void initialize() { }
	default void reset() { }
	default void onServerStarted(MinecraftServer server) { }
	default void onServerStopping(MinecraftServer server) { }
	default void update(MinecraftServer server) { }
	default float resolveWorldClockRate(MinecraftServer server) { return 1.0F; }
	default long getElapsedGameplayTicks() { return 0L; }
	default long getGameplayTickDelta() { return 0L; }
	default long getElapsedWorldTimeTicks() { return 0L; }
	default long getWorldTimeDelta() { return 0L; }
	default long getCurrentAbsoluteDayTime() { return 0L; }
	default long getCurrentAbsoluteDayTime(ServerLevel world) { return world == null ? 0L : world.getOverworldClockTime(); }
	default boolean isEnabled() { return false; }
	default long getGameplayTicksPerDay() { return TimeAPIManager.MINECRAFT_TICKS_PER_CYCLE; }
	default long getDay(long absoluteDayTime) { return Math.floorDiv(absoluteDayTime, TimeAPIManager.MINECRAFT_TICKS_PER_CYCLE); }
	default long toAbsoluteDayTime(long day, int hour, int minute) { return day * TimeAPIManager.MINECRAFT_TICKS_PER_CYCLE + (hour * 60L + minute) * TimeAPIManager.TICKS_PER_SECOND * TimeAPIManager.SECONDS_PER_MINUTE; }
	default long toAbsoluteDayTime(long day, int totalMinutes) { return day * TimeAPIManager.MINECRAFT_TICKS_PER_CYCLE + totalMinutes * TimeAPIManager.TICKS_PER_SECOND * TimeAPIManager.SECONDS_PER_MINUTE; }
	default void setClockFromAbsoluteDayTime(long absoluteDayTime) { }
	default void setGameplayTicks(long value) { }
	default void setWorldTimeTicks(long value) { }
	default void tickGameplay() { }
	default void advance(MinecraftServer server, long ignoredAmount) { }
	default long getGameplayTicks() { return 0L; }
	default boolean isDaytime(long absoluteDayTime) { return false; }
	default boolean isSleepTime(long absoluteDayTime) { return false; }
	default int getTotalMinutes(long absoluteDayTime) { return 0; }
	default int getClockHour(long absoluteDayTime) { return 0; }
	default long resolveClockHourToMinecraftTimeTicks(int clockHour) { return 0L; }
	default long resolveConfiguredTimeMarkerTicks(ResourceKey<ClockTimeMarker> markerKey) { return 0L; }
	default int getCycleMinutes(long absoluteDayTime) { return 0; }
	default void broadcastWorldTimeNow(MinecraftServer server) { }
	default void broadcastWorldTimeIfChanged(MinecraftServer server) { }
	default boolean isSleepEnabled() { return false; }
	default boolean shouldAllowResettingTime(Player player) { return false; }
	default long refreshSleepTickIncrement(MinecraftServer server) { return 0L; }
	default long refreshTickIncrement(MinecraftServer server) { return 0L; }
	default long getSleepTickIncrement(MinecraftServer server) { return 0L; }
	default long getTickIncrement(MinecraftServer server) { return 0L; }
	default long getCachedSleepTickIncrement() { return 0L; }
	default long getCachedTickIncrement() { return 0L; }
	default boolean canStartSleeping(Player player) { return false; }
	default boolean shouldAllowBedSleepByTime(BedRule bedRule, Level level, Player player) { return false; }
	default boolean shouldKeepSleepingWhileForwarding(BedRule bedRule, Level level, Player player) { return false; }
	default void onSleepStarted(ServerPlayer player) { }
	default boolean isThunderstormBypassEnabled() { return false; }
}

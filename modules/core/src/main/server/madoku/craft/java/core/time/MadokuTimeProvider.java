package madoku.craft.java.core.time;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Built-in provider backed by the Madoku time implementation. */
public final class MadokuTimeProvider implements TimeProvider {
	@Override public void initialize() { MadokuTimeManager.initialize(); }
	@Override public void reset() { MadokuTimeManager.reset(); }
	@Override public void onServerStarted(MinecraftServer server) { MadokuTimeManager.onServerStarted(server); }
	@Override public void onServerStopping(MinecraftServer server) { MadokuTimeManager.onServerStopping(server); }
	@Override public void update(MinecraftServer server) { MadokuTimeManager.update(server); }
	@Override public float resolveWorldClockRate(MinecraftServer server) { return MadokuTimeManager.resolveWorldClockRate(server); }
	@Override public long getElapsedGameplayTicks() { return MadokuTimeManager.getElapsedGameplayTicks(); }
	@Override public long getGameplayTickDelta() { return MadokuTimeManager.getGameplayTickDelta(); }
	@Override public long getElapsedWorldTimeTicks() { return MadokuTimeManager.getElapsedWorldTimeTicks(); }
	@Override public long getWorldTimeDelta() { return MadokuTimeManager.getWorldTimeDelta(); }
	@Override public long getCurrentAbsoluteDayTime() { return MadokuTimeManager.getCurrentAbsoluteDayTime(); }
	@Override public long getCurrentAbsoluteDayTime(ServerLevel world) { return MadokuTimeManager.getCurrentAbsoluteDayTime(world); }
	@Override public boolean isEnabled() { return MadokuTimeManager.isEnabled(); }
	@Override public long getGameplayTicksPerDay() { return MadokuTimeManager.getGameplayTicksPerDay(); }
	@Override public long getDay(long absoluteDayTime) { return MadokuTimeManager.getDay(absoluteDayTime); }
	@Override public long toAbsoluteDayTime(long day, int hour, int minute) { return MadokuTimeManager.toAbsoluteDayTime(day, hour, minute); }
	@Override public long toAbsoluteDayTime(long day, int totalMinutes) { return MadokuTimeManager.toAbsoluteDayTime(day, totalMinutes); }
	@Override public void setClockFromAbsoluteDayTime(long absoluteDayTime) { MadokuTimeManager.setClockFromAbsoluteDayTime(absoluteDayTime); }
	@Override public void setGameplayTicks(long value) { MadokuTimeManager.setGameplayTicks(value); }
	@Override public void setWorldTimeTicks(long value) { MadokuTimeManager.setWorldTimeTicks(value); }
	@Override public void tickGameplay() { MadokuTimeManager.tickGameplay(); }
	@Override public void advance(MinecraftServer server, long ignoredAmount) { MadokuTimeManager.advance(server, ignoredAmount); }
	@Override public long getGameplayTicks() { return MadokuTimeManager.getGameplayTicks(); }
	@Override public boolean isDaytime(long absoluteDayTime) { return MadokuTimeManager.isDaytime(absoluteDayTime); }
	@Override public boolean isSleepTime(long absoluteDayTime) { return MadokuTimeManager.isSleepTime(absoluteDayTime); }
	@Override public int getTotalMinutes(long absoluteDayTime) { return MadokuTimeManager.getTotalMinutes(absoluteDayTime); }
	@Override public int getClockHour(long absoluteDayTime) { return MadokuTimeManager.getClockHour(absoluteDayTime); }
	@Override public long resolveClockHourToMinecraftTimeTicks(int clockHour) { return MadokuTimeManager.resolveClockHourToMinecraftTimeTicks(clockHour); }
	@Override public long resolveConfiguredTimeMarkerTicks(ResourceKey<ClockTimeMarker> markerKey) { return MadokuTimeManager.resolveConfiguredTimeMarkerTicks(markerKey); }
	@Override public int getCycleMinutes(long absoluteDayTime) { return MadokuTimeManager.getCycleMinutes(absoluteDayTime); }
	@Override public void broadcastWorldTimeNow(MinecraftServer server) { MadokuTimeManager.broadcastWorldTimeNow(server); }
	@Override public void broadcastWorldTimeIfChanged(MinecraftServer server) { MadokuTimeManager.broadcastWorldTimeIfChanged(server); }
	@Override public boolean isSleepEnabled() { return MadokuTimeManager.isSleepEnabled(); }
	@Override public boolean shouldAllowResettingTime(Player player) { return MadokuTimeManager.shouldAllowResettingTime(player); }
	@Override public long refreshSleepTickIncrement(MinecraftServer server) { return MadokuTimeManager.refreshSleepTickIncrement(server); }
	@Override public long refreshTickIncrement(MinecraftServer server) { return MadokuTimeManager.refreshTickIncrement(server); }
	@Override public long getSleepTickIncrement(MinecraftServer server) { return MadokuTimeManager.getSleepTickIncrement(server); }
	@Override public long getTickIncrement(MinecraftServer server) { return MadokuTimeManager.getTickIncrement(server); }
	@Override public long getCachedSleepTickIncrement() { return MadokuTimeManager.getCachedSleepTickIncrement(); }
	@Override public long getCachedTickIncrement() { return MadokuTimeManager.getCachedTickIncrement(); }
	@Override public boolean canStartSleeping(Player player) { return MadokuTimeManager.canStartSleeping(player); }
	@Override public boolean shouldAllowBedSleepByTime(BedRule bedRule, Level level, Player player) { return MadokuTimeManager.shouldAllowBedSleepByTime(bedRule, level, player); }
	@Override public boolean shouldKeepSleepingWhileForwarding(BedRule bedRule, Level level, Player player) { return MadokuTimeManager.shouldKeepSleepingWhileForwarding(bedRule, level, player); }
	@Override public void onSleepStarted(ServerPlayer player) { MadokuTimeManager.onSleepStarted(player); }
	@Override public boolean isThunderstormBypassEnabled() { return MadokuTimeManager.isThunderstormBypassEnabled(); }
}

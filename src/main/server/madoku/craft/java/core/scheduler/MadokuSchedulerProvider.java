package madoku.craft.java.core.scheduler;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

/** Built-in provider backed by the Madoku scheduler implementation. */
public final class MadokuSchedulerProvider implements SchedulerProvider {
	@Override public void initialize() { MadokuSchedulerManager.initialize(); }
	@Override public void reset() { MadokuSchedulerManager.reset(); }
	@Override public void loadPersistedData(MinecraftServer server) { MadokuSchedulerManager.loadPersistedData(server); }
	@Override public void savePersistedData(MinecraftServer server) { MadokuSchedulerManager.savePersistedData(server); }
	@Override public void autosavePersistedData(MinecraftServer server) { MadokuSchedulerManager.autosavePersistedData(server); }
	@Override public void onClockTick(MinecraftServer server) { MadokuSchedulerManager.onClockTick(server); }
	@Override public void onServerTick(MinecraftServer server) { MadokuSchedulerManager.onServerTick(server); }
	@Override public long resolveAdaptiveDelayTicks(MinecraftServer server, String schedulerOwnerId, long minimumIntervalTicks, long maximumIntervalTicks) { return MadokuSchedulerManager.resolveAdaptiveDelayTicks(server, schedulerOwnerId, minimumIntervalTicks, maximumIntervalTicks); }
	@Override public void clearAdaptiveDelayState(String schedulerOwnerId) { MadokuSchedulerManager.clearAdaptiveDelayState(schedulerOwnerId); }
	@Override public void clearQueuedRequests(String schedulerId) { MadokuSchedulerManager.clearQueuedRequests(schedulerId); }
	@Override public boolean hasQueuedTask(String schedulerId, String taskType) { return MadokuSchedulerManager.hasQueuedTask(schedulerId, taskType); }
	@Override public int defaultExpirationDays() { return MadokuSchedulerManager.defaultExpirationDays(); }
	@Override public String createOrGetScheduler(SchedulerAPIManager.SchedulerBinding binding) { return MadokuSchedulerManager.createOrGetScheduler(binding); }
	@Override public String createOrGetScheduler(SchedulerAPIManager.SchedulerBinding binding, int expirationDays) { return MadokuSchedulerManager.createOrGetScheduler(binding, expirationDays); }
	@Override public SchedulerAPIManager.EnqueueStatus enqueue(String schedulerId, long delayTicks, String taskType, JsonObject payload, SchedulerAPIManager.TickDomain domain) { return MadokuSchedulerManager.enqueue(schedulerId, delayTicks, taskType, payload, domain); }
	@Override public void registerTaskHandler(String taskType, SchedulerAPIManager.TaskHandler handler) { MadokuSchedulerManager.registerTaskHandler(taskType, handler); }
	@Override public void unregisterTaskHandler(String taskType) { MadokuSchedulerManager.unregisterTaskHandler(taskType); }
	@Override public String normalizeLevelIdentifier(String levelId) { return MadokuSchedulerManager.normalizeLevelIdentifier(levelId); }
}

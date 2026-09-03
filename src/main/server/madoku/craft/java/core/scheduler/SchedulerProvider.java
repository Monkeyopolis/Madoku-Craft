package madoku.craft.java.core.scheduler;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

/** Provider contract for the shared scheduler subsystem. */
public interface SchedulerProvider {
	default void initialize() { }
	default void reset() { }
	default void loadPersistedData(MinecraftServer server) { }
	default void savePersistedData(MinecraftServer server) { }
	default void autosavePersistedData(MinecraftServer server) { }
	default void onClockTick(MinecraftServer server) { }
	default void onServerTick(MinecraftServer server) { }
	default long resolveAdaptiveDelayTicks(MinecraftServer server, String schedulerOwnerId, long minimumIntervalTicks, long maximumIntervalTicks) { return 0L; }
	default void clearAdaptiveDelayState(String schedulerOwnerId) { }
	default void clearQueuedRequests(String schedulerId) { }
	default boolean hasQueuedTask(String schedulerId, String taskType) { return false; }
	default int defaultExpirationDays() { return 30; }
	default String createOrGetScheduler(SchedulerAPIManager.SchedulerBinding binding) { return null; }
	default String createOrGetScheduler(SchedulerAPIManager.SchedulerBinding binding, int expirationDays) { return null; }
	default SchedulerAPIManager.EnqueueStatus enqueue(String schedulerId, long delayTicks, String taskType, JsonObject payload, SchedulerAPIManager.TickDomain domain) { return SchedulerAPIManager.EnqueueStatus.SCHEDULER_NOT_FOUND; }
	default void registerTaskHandler(String taskType, SchedulerAPIManager.TaskHandler handler) { }
	default void unregisterTaskHandler(String taskType) { }
	default String normalizeLevelIdentifier(String levelId) { return levelId; }
}

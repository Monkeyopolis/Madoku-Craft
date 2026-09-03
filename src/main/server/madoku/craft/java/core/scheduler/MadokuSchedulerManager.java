package madoku.craft.java.core.scheduler;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

/** Orchestrates scheduler services through their public API contract. */
public final class MadokuSchedulerManager {
	private MadokuSchedulerManager() {
	}

	public static void initialize() { SchedulerRuntimeManager.initialize(); }
	public static void reset() { SchedulerRuntimeManager.reset(); }
	public static void loadPersistedData(MinecraftServer server) { SchedulerRuntimeManager.loadPersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { SchedulerRuntimeManager.savePersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { SchedulerRuntimeManager.autosavePersistedData(server); }
	public static void onClockTick(MinecraftServer server) { SchedulerRuntimeManager.onClockTick(server); }
	public static void onServerTick(MinecraftServer server) { SchedulerRuntimeManager.onServerTick(server); }
	public static long resolveAdaptiveDelayTicks(MinecraftServer server, String owner, long minimum, long maximum) { return SchedulerRuntimeManager.resolveAdaptiveDelayTicks(server, owner, minimum, maximum); }
	public static void clearAdaptiveDelayState(String owner) { SchedulerRuntimeManager.clearAdaptiveDelayState(owner); }
	public static void clearQueuedRequests(String schedulerId) { SchedulerRuntimeManager.clearQueuedRequests(schedulerId); }
	public static boolean hasQueuedTask(String schedulerId, String taskType) { return SchedulerRuntimeManager.hasQueuedTask(schedulerId, taskType); }
	public static int defaultExpirationDays() { return SchedulerRuntimeManager.defaultExpirationDays(); }
	public static String createOrGetScheduler(SchedulerAPIManager.SchedulerBinding binding) { return SchedulerRuntimeManager.createOrGetScheduler(binding, SchedulerRuntimeManager.defaultExpirationDays()); }
	public static String createOrGetScheduler(SchedulerAPIManager.SchedulerBinding binding, int expirationDays) { return SchedulerRuntimeManager.createOrGetScheduler(binding, expirationDays); }
	public static SchedulerAPIManager.EnqueueStatus enqueue(String schedulerId, long delayTicks, String taskType, JsonObject payload, SchedulerAPIManager.TickDomain domain) { return SchedulerRuntimeManager.enqueue(schedulerId, delayTicks, taskType, payload, domain); }
	public static void registerTaskHandler(String taskType, SchedulerAPIManager.TaskHandler handler) { SchedulerRuntimeManager.registerTaskHandler(taskType, handler); }
	public static void unregisterTaskHandler(String taskType) { SchedulerRuntimeManager.unregisterTaskHandler(taskType); }
	public static String normalizeLevelIdentifier(String levelId) {
		if (levelId == null) return null;
		String trimmed = levelId.trim();
		if (trimmed.isEmpty()) return null;
		if (net.minecraft.resources.Identifier.tryParse(trimmed) != null) return trimmed;
		int slashIndex = trimmed.lastIndexOf('/');
		int closeBracketIndex = trimmed.lastIndexOf(']');
		if (slashIndex >= 0 && closeBracketIndex > slashIndex) {
			String candidate = trimmed.substring(slashIndex + 1, closeBracketIndex).trim();
			if (net.minecraft.resources.Identifier.tryParse(candidate) != null) return candidate;
		}
		return trimmed;
	}
}

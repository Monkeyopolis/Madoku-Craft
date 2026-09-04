package madoku.craft.java.core.data;

import madoku.craft.java.attributes.HealthAPIManager;
import madoku.craft.java.attributes.HungerAPIManager;
import madoku.craft.java.core.scheduler.SchedulerAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import madoku.craft.java.ecosystem.MadokuEcosystemManager;
import madoku.craft.java.entity.MadokuEntities;
import madoku.craft.java.farming.FarmingAPIManager;
import madoku.craft.java.levels.LevelsPlayerAPIManager;
import madoku.craft.java.pet.PetAPIManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates vanilla SavedDataStorage autosaves and shutdown flushes. */
public final class DataSaveCoordinatorManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataSaveCoordinatorManager.class);
	private static long dirtyChunks;
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;
	private static SaveMetrics lastMetrics = SaveMetrics.empty();

	private DataSaveCoordinatorManager() { }

	public static synchronized void initialize() {
		lastAutosaveBucket = Long.MIN_VALUE;
		dirtyChunks = 0L;
		lastMetrics = SaveMetrics.empty();
	}

	public static synchronized void reset() {
		lastAutosaveBucket = Long.MIN_VALUE;
		dirtyChunks = 0L;
		lastMetrics = SaveMetrics.empty();
	}

	public static void autosave(MinecraftServer server) {
		if (server == null) return;
		long interval = Math.max(1L, WorldChunkDataAPIManager.getAutoSaveIntervalTicks());
		long bucket = Math.floorDiv(TimeAPIManager.getGameplayTicks(), interval);
		if (bucket == lastAutosaveBucket) return;
		lastAutosaveBucket = bucket;
		captureAndSchedule(server, false, "autosave");
	}

	public static void saveAndWait(MinecraftServer server) {
		if (server == null) return;
		long start = System.nanoTime();
		captureAndSchedule(server, true, "shutdown");
		for (SavedDataStorage storage : MadokuSavedDataManager.storages()) storage.saveAndJoin();
		lastMetrics = new SaveMetrics("shutdown", dirtyChunks, 0L, 0L,
			Math.max(0L, (System.nanoTime() - start) / 1_000_000L), 0L);
		LOGGER.info("Madoku vanilla SavedData save completed: {}", lastMetrics);
	}

	public static SaveMetrics getLastMetrics() { return lastMetrics; }
	public static void recordDirtyChunks(long count) { if (count > 0L) dirtyChunks += count; }

	private static void captureAndSchedule(MinecraftServer server, boolean shutdown, String reason) {
		if (shutdown) {
			MadokuEntities.savePersistedData(server);
			FarmingAPIManager.savePersistedData(server);
			MadokuEcosystemManager.savePersistedData(server);
			SchedulerAPIManager.savePersistedData(server);
			HealthAPIManager.savePersistedData(server);
			HungerAPIManager.savePersistedData(server);
			LevelsPlayerAPIManager.savePersistedData(server);
			PetAPIManager.savePersistedData(server);
		} else {
			MadokuEntities.autosavePersistedData(server);
			FarmingAPIManager.autosavePersistedData(server);
			MadokuEcosystemManager.autosavePersistedData(server);
			SchedulerAPIManager.autosavePersistedData(server);
			HealthAPIManager.autosavePersistedData(server);
			HungerAPIManager.autosavePersistedData(server);
			LevelsPlayerAPIManager.autosavePersistedData(server);
			PetAPIManager.autosavePersistedData(server);
		}
		MadokuChunkDataManager.savePersistedData(server);
		WorldDataAPIManager.savePersistedData(server);
		WorldChunkDataAPIManager.savePersistedData(server);
		PlayerDataAPIManager.savePersistedData(server);
		for (SavedDataStorage storage : MadokuSavedDataManager.storages()) storage.scheduleSave();
		if (!shutdown) LOGGER.debug("Madoku {} scheduled through vanilla SavedDataStorage", reason);
	}

	public record SaveMetrics(String reason, long dirtyChunks, long filesWritten, long bytesWritten,
		long lastWriteDurationMillis, long saveTasks) {
		private static SaveMetrics empty() { return new SaveMetrics("none", 0L, 0L, 0L, 0L, 0L); }
	}
}

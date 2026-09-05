package madoku.craft.java.core.data;

import madoku.craft.java.attributes.HealthAPIManager;
import madoku.craft.java.attributes.HungerAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import madoku.craft.java.ecosystem.MadokuEcosystemManager;
import madoku.craft.java.entity.MadokuEntities;
import madoku.craft.java.farming.FarmingAPIManager;
import madoku.craft.java.levels.LevelsPlayerAPIManager;
import madoku.craft.java.pet.PetAPIManager;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates state capture and vanilla player, chunk, and SavedData flushes. */
public final class DataSaveCoordinatorManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataSaveCoordinatorManager.class);
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;
	private static SaveMetrics lastMetrics = SaveMetrics.empty();

	private DataSaveCoordinatorManager() { }

	public static synchronized void initialize() {
		lastAutosaveBucket = Long.MIN_VALUE;
		lastMetrics = SaveMetrics.empty();
	}

	public static synchronized void reset() {
		lastAutosaveBucket = Long.MIN_VALUE;
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
		server.saveEverything(false, false, false);
		lastMetrics = new SaveMetrics("shutdown", 0L, 0L, 0L,
			Math.max(0L, (System.nanoTime() - start) / 1_000_000L), 0L);
		LOGGER.info("Madoku vanilla SavedData save completed: {}", lastMetrics);
	}

	public static SaveMetrics getLastMetrics() { return lastMetrics; }
	private static void captureAndSchedule(MinecraftServer server, boolean shutdown, String reason) {
		if (shutdown) {
			MadokuEntities.savePersistedData(server);
			FarmingAPIManager.savePersistedData(server);
			MadokuEcosystemManager.savePersistedData(server);
			HealthAPIManager.savePersistedData(server);
			HungerAPIManager.savePersistedData(server);
			LevelsPlayerAPIManager.savePersistedData(server);
			PetAPIManager.savePersistedData(server);
		} else {
			MadokuEntities.autosavePersistedData(server);
			FarmingAPIManager.autosavePersistedData(server);
			MadokuEcosystemManager.autosavePersistedData(server);
			HealthAPIManager.autosavePersistedData(server);
			HungerAPIManager.autosavePersistedData(server);
			LevelsPlayerAPIManager.autosavePersistedData(server);
			PetAPIManager.autosavePersistedData(server);
		}
		MadokuChunkDataManager.savePersistedData(server);
		WorldDataAPIManager.savePersistedData(server);
		WorldChunkDataAPIManager.savePersistedData(server);
		PlayerDataAPIManager.savePersistedData(server);
		if (!shutdown) {
			server.saveEverything(true, false, false);
			LOGGER.debug("Madoku {} completed through vanilla player, chunk, and SavedData saves", reason);
		}
	}

	public record SaveMetrics(String reason, long dirtyChunks, long filesWritten, long bytesWritten,
		long lastWriteDurationMillis, long saveTasks) {
		private static SaveMetrics empty() { return new SaveMetrics("none", 0L, 0L, 0L, 0L, 0L); }
	}
}

package madoku.craft.api.data;

import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.attributes.health.MadokuHealthManager;
import madoku.craft.attributes.hunger.MadokuHungerManager;
import madoku.craft.attributes.oxygen.MadokuOxygenManager;
import madoku.craft.ecosystem.MadokuEcosystemManager;
import madoku.craft.entity.MadokuEntities;
import madoku.craft.farming.system.MadokuFarming;
import madoku.craft.itemstack.system.MadokuItemStack;
import madoku.craft.levels.MadokuLevels;
import madoku.craft.pet.PlayerEntitiesSystem;
import madoku.craft.scheduler.SchedulerManagerSystem;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

	/** Coordinates all managed data snapshots and serializes their disk writes off the server thread. */
public final class DataSaveCoordinatorManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataSaveCoordinatorManager.class);
	private static final ThreadFactory THREAD_FACTORY = runnable -> {
		Thread thread = new Thread(runnable, "madoku-save-worker");
		thread.setDaemon(true);
		return thread;
	};
	private static final AtomicLong DIRTY_CHUNKS = new AtomicLong();
	private static final AtomicLong FILES_WRITTEN = new AtomicLong();
	private static final AtomicLong BYTES_WRITTEN = new AtomicLong();
	private static final AtomicLong SAVE_TASKS = new AtomicLong();
	private static volatile ExecutorService executor;
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;
	private static volatile SaveMetrics lastMetrics = SaveMetrics.empty();

	private DataSaveCoordinatorManager() { }

	public static synchronized void initialize() {
		if (executor == null || executor.isShutdown()) {
			executor = Executors.newSingleThreadExecutor(THREAD_FACTORY);
			DIRTY_CHUNKS.set(0L);
			FILES_WRITTEN.set(0L);
			BYTES_WRITTEN.set(0L);
			SAVE_TASKS.set(0L);
			lastMetrics = SaveMetrics.empty();
		}
		lastAutosaveBucket = Long.MIN_VALUE;
	}

	public static synchronized void reset() {
		ExecutorService current = executor;
		executor = null;
		lastAutosaveBucket = Long.MIN_VALUE;
		if (current != null) current.shutdownNow();
	}

	public static void autosave(MinecraftServer server) {
		if (server == null) return;
		long interval = Math.max(1L, DataWorldChunkManager.getAutoSaveIntervalTicks());
		long bucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), interval);
		if (bucket == lastAutosaveBucket) return;
		lastAutosaveBucket = bucket;
		captureAndQueue(server, false, "autosave");
	}

	public static void saveAndWait(MinecraftServer server) {
		if (server == null) return;
		long start = System.nanoTime();
		long filesBefore = FILES_WRITTEN.get();
		long bytesBefore = BYTES_WRITTEN.get();
		long tasksBefore = SAVE_TASKS.get();
		captureAndQueue(server, true, "shutdown");
		awaitWrites();
		lastMetrics = new SaveMetrics("shutdown", DIRTY_CHUNKS.get(),
			Math.max(0L, FILES_WRITTEN.get() - filesBefore),
			Math.max(0L, BYTES_WRITTEN.get() - bytesBefore),
			Math.max(0L, (System.nanoTime() - start) / 1_000_000L),
			Math.max(0L, SAVE_TASKS.get() - tasksBefore));
		LOGGER.info("Madoku save completed: {}", lastMetrics);
	}

	public static void submit(String subsystem, Path file, IoTask task) {
		ExecutorService current = ensureExecutor();
		if (file == null || task == null) return;
		SAVE_TASKS.incrementAndGet();
		current.submit(() -> {
			long start = System.nanoTime();
			try {
				task.run();
				FILES_WRITTEN.incrementAndGet();
				try {
					if (Files.isRegularFile(file)) BYTES_WRITTEN.addAndGet(Math.max(0L, Files.size(file)));
				} catch (IOException ignored) { }
			} catch (Exception exception) {
				LOGGER.error("Failed to save {}", subsystem == null ? "world data" : subsystem, exception);
			} finally {
				lastMetrics = new SaveMetrics(subsystem == null ? "world-data" : subsystem,
					DIRTY_CHUNKS.get(), FILES_WRITTEN.get(), BYTES_WRITTEN.get(),
					Math.max(0L, (System.nanoTime() - start) / 1_000_000L), SAVE_TASKS.get());
			}
		});
	}

	public static void recordDirtyChunks(long count) {
		if (count > 0L) DIRTY_CHUNKS.addAndGet(count);
	}

	public static SaveMetrics getLastMetrics() { return lastMetrics; }

	private static void captureAndQueue(MinecraftServer server, boolean shutdown, String reason) {
		long start = System.nanoTime();
		long filesBefore = FILES_WRITTEN.get();
		long bytesBefore = BYTES_WRITTEN.get();
		long tasksBefore = SAVE_TASKS.get();
		if (shutdown) {
			MadokuEntities.savePersistedData(server);
			MadokuFarming.savePersistedData(server);
			MadokuEcosystemManager.savePersistedData(server);
			SchedulerManagerSystem.savePersistedData(server);
			MadokuHealthManager.savePersistedData(server);
			MadokuHungerManager.savePersistedData(server);
			MadokuOxygenManager.savePersistedData(server);
			MadokuLevels.savePersistedData(server);
			PlayerEntitiesSystem.savePersistedData(server);
			MadokuItemStack.savePersistedData(server);
		} else {
			MadokuEntities.autosavePersistedData(server);
			MadokuFarming.autosavePersistedData(server);
			MadokuEcosystemManager.autosavePersistedData(server);
			SchedulerManagerSystem.autosavePersistedData(server);
			MadokuHealthManager.autosavePersistedData(server);
			MadokuHungerManager.autosavePersistedData(server);
			MadokuOxygenManager.autosavePersistedData(server);
			MadokuLevels.autosavePersistedData(server);
			PlayerEntitiesSystem.autosavePersistedData(server);
			MadokuItemStack.autosavePersistedData(server);
		}
		if (shutdown) MadokuChunkDataManager.savePersistedData(server);
		else MadokuChunkDataManager.autosavePersistedData(server);
		DataWorldChunkManager.savePersistedData(server);
		DataPlayerManager.savePersistedData(server);
		lastMetrics = new SaveMetrics(reason, DIRTY_CHUNKS.get(),
			Math.max(0L, FILES_WRITTEN.get() - filesBefore),
			Math.max(0L, BYTES_WRITTEN.get() - bytesBefore),
			Math.max(0L, (System.nanoTime() - start) / 1_000_000L),
			Math.max(0L, SAVE_TASKS.get() - tasksBefore));
		if (!shutdown) LOGGER.debug("Madoku {} queued: {}", reason, lastMetrics);
	}

	private static void awaitWrites() {
		ExecutorService current = ensureExecutor();
		try {
			Future<?> barrier = current.submit(() -> { });
			barrier.get();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			LOGGER.warn("Interrupted while waiting for Madoku saves to finish.");
		} catch (ExecutionException exception) {
			LOGGER.error("Madoku save worker failed while draining queued writes.", exception.getCause());
		}
	}

	private static synchronized ExecutorService ensureExecutor() {
		if (executor == null || executor.isShutdown()) initialize();
		return executor;
	}

	@FunctionalInterface
	public interface IoTask { void run() throws Exception; }

	public record SaveMetrics(String reason, long dirtyChunks, long filesWritten, long bytesWritten, long lastWriteDurationMillis, long saveTasks) {
		private static SaveMetrics empty() { return new SaveMetrics("none", 0L, 0L, 0L, 0L, 0L); }
	}
}

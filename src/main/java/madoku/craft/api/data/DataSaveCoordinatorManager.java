package madoku.craft.api.data;

import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.attributes.health.MadokuHealthManager;
import madoku.craft.attributes.hunger.MadokuHungerManager;
import madoku.craft.ecosystem.MadokuEcosystemManager;
import madoku.craft.entity.MadokuEntities;
import madoku.craft.farming.MadokuFarmingManager;
import madoku.craft.levels.MadokuLevelsManager;
import madoku.craft.pet.MadokuPetManager;
import madoku.craft.api.scheduler.MadokuSchedulerManager;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
	private static final Map<Path, PendingTask> PENDING_TASKS = new HashMap<>();
	private static final Set<Path> ACTIVE_PATHS = new HashSet<>();

	private DataSaveCoordinatorManager() { }

	public static synchronized void initialize() {
		if (executor == null || executor.isShutdown()) {
			executor = Executors.newSingleThreadExecutor(THREAD_FACTORY);
			DIRTY_CHUNKS.set(0L);
			FILES_WRITTEN.set(0L);
			BYTES_WRITTEN.set(0L);
			SAVE_TASKS.set(0L);
			lastMetrics = SaveMetrics.empty();
			PENDING_TASKS.clear();
			ACTIVE_PATHS.clear();
		}
		lastAutosaveBucket = Long.MIN_VALUE;
	}

	public static synchronized void reset() {
		ExecutorService current = executor;
		executor = null;
		lastAutosaveBucket = Long.MIN_VALUE;
		PENDING_TASKS.clear();
		ACTIVE_PATHS.clear();
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
		if (file == null || task == null) return;
		Path key = file.toAbsolutePath().normalize();
		ExecutorService current = ensureExecutor();
		synchronized (DataSaveCoordinatorManager.class) {
			PENDING_TASKS.put(key, new PendingTask(subsystem, task));
			if (ACTIVE_PATHS.add(key)) {
				current.submit(() -> drain(key));
			}
		}
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
			MadokuFarmingManager.savePersistedData(server);
			MadokuEcosystemManager.savePersistedData(server);
			MadokuSchedulerManager.savePersistedData(server);
			MadokuHealthManager.savePersistedData(server);
			MadokuHungerManager.savePersistedData(server);
			MadokuLevelsManager.savePersistedData(server);
			MadokuPetManager.savePersistedData(server);
		} else {
			MadokuEntities.autosavePersistedData(server);
			MadokuFarmingManager.autosavePersistedData(server);
			MadokuEcosystemManager.autosavePersistedData(server);
			MadokuSchedulerManager.autosavePersistedData(server);
			MadokuHealthManager.autosavePersistedData(server);
			MadokuHungerManager.autosavePersistedData(server);
			MadokuLevelsManager.autosavePersistedData(server);
			MadokuPetManager.autosavePersistedData(server);
		}
		if (shutdown) MadokuChunkDataManager.savePersistedData(server);
		else MadokuChunkDataManager.autosavePersistedData(server);
		if (shutdown) DataWorldManager.savePersistedData(server);
		else DataWorldManager.autosavePersistedData(server);
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

	private static void drain(Path file) {
		while (true) {
			PendingTask pending;
			synchronized (DataSaveCoordinatorManager.class) {
				pending = PENDING_TASKS.remove(file);
				if (pending == null) {
					ACTIVE_PATHS.remove(file);
					return;
				}
			}
			runTask(file, pending);
		}
	}

	private static void runTask(Path file, PendingTask pending) {
		long start = System.nanoTime();
		SAVE_TASKS.incrementAndGet();
		try {
			pending.task().run();
			FILES_WRITTEN.incrementAndGet();
			try {
				if (Files.isRegularFile(file)) BYTES_WRITTEN.addAndGet(Math.max(0L, Files.size(file)));
			} catch (IOException ignored) { }
		} catch (Exception exception) {
			LOGGER.error("Failed to save {}", pending.subsystem() == null ? "world data" : pending.subsystem(), exception);
		} finally {
			lastMetrics = new SaveMetrics(pending.subsystem() == null ? "world-data" : pending.subsystem(),
				DIRTY_CHUNKS.get(), FILES_WRITTEN.get(), BYTES_WRITTEN.get(),
				Math.max(0L, (System.nanoTime() - start) / 1_000_000L), SAVE_TASKS.get());
		}
	}

	@FunctionalInterface
	public interface IoTask { void run() throws Exception; }

	private record PendingTask(String subsystem, IoTask task) { }

	public record SaveMetrics(String reason, long dirtyChunks, long filesWritten, long bytesWritten, long lastWriteDurationMillis, long saveTasks) {
		private static SaveMetrics empty() { return new SaveMetrics("none", 0L, 0L, 0L, 0L, 0L); }
	}
}

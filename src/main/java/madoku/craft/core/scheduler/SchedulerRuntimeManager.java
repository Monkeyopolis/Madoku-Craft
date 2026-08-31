package madoku.craft.core.scheduler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.core.MadokuCoreManager;
import madoku.craft.core.chunk.MadokuChunkManager;
import madoku.craft.core.data.DataSaveCoordinatorManager;
import madoku.craft.core.data.DataWorldChunkManager;
import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.json.JSONTypeManager;
import madoku.craft.core.json.MadokuJSONManager;
import madoku.craft.core.time.MadokuTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/** Owns scheduler state, execution, expiration, and persistence behind the public facade. */
final class SchedulerRuntimeManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerRuntimeManager.class);
	private static final int DEFAULT_EXPIRATION_DAYS = 14;
	private static final long DEFAULT_INACTIVE_EXPIRATION_MINUTES = 5L;
	private static final String DATA_FOLDER_NAME = MadokuCoreManager.CORE_FOLDER_NAME + "/madoku-scheduler";
	private static final String DATA_FILE_NAME = "madoku-scheduler";
	private static final String SCHEDULER_FILES_DIRECTORY = "schedulers";
	private static final String GROUP_GENERAL = "general";
	private static final String FIELD_EXPIRATION = "expiration";
	private static final String FIELD_LAST_TOUCHED_DAY = "last-touched-day";
	private static final Map<String, SchedulerEntry> SCHEDULERS = new LinkedHashMap<>();
	private static final Map<String, String> SCHEDULER_IDS_BY_BINDING = new HashMap<>();
	private static final Map<String, MadokuSchedulerManager.TaskHandler> TASK_HANDLERS = new HashMap<>();

	private static long lastAutosaveBucket = Long.MIN_VALUE;
	private static boolean dirty;

	private SchedulerRuntimeManager() { }

	static int defaultExpirationDays() {
		return DEFAULT_EXPIRATION_DAYS;
	}

	static long getInactiveExpirationTicks() {
		try {
			return Math.multiplyExact(
				Math.multiplyExact(DEFAULT_INACTIVE_EXPIRATION_MINUTES, MadokuTimeManager.SECONDS_PER_MINUTE),
				MadokuTimeManager.TICKS_PER_SECOND);
		} catch (ArithmeticException exception) {
			return DEFAULT_INACTIVE_EXPIRATION_MINUTES
				* MadokuTimeManager.SECONDS_PER_MINUTE
				* MadokuTimeManager.TICKS_PER_SECOND;
		}
	}

	static void initialize() {
		SchedulerAdaptiveIntervalManager.clearAll();
	}

	static void reset() {
		SCHEDULERS.clear();
		SCHEDULER_IDS_BY_BINDING.clear();
		SchedulerAdaptiveIntervalManager.clearAll();
		lastAutosaveBucket = Long.MIN_VALUE;
		dirty = false;
	}

	static void loadPersistedData(MinecraftServer server) {
		if (server == null) return;
		reset();
		JsonObject persistedData = MadokuJSONManager.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		applyPersistedData(server, persistedData);
		dirty = false;
		lastAutosaveBucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), getAutoSaveIntervalTicks(server));
	}

	static void savePersistedData(MinecraftServer server) {
		if (server == null) return;
		saveSchedulerFiles(server);
		MadokuJSONManager.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, toPersistedData());
		dirty = false;
		lastAutosaveBucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), getAutoSaveIntervalTicks(server));
	}

	static void autosavePersistedData(MinecraftServer server) {
		if (server == null) return;
		long currentBucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), getAutoSaveIntervalTicks(server));
		if (currentBucket == lastAutosaveBucket) return;
		if (!dirty) {
			lastAutosaveBucket = currentBucket;
			return;
		}
		saveSchedulerFiles(server);
		MadokuJSONManager.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, toPersistedData());
		dirty = false;
		lastAutosaveBucket = currentBucket;
	}

	static void onClockTick(MinecraftServer server) {
		if (server != null && MadokuTimeManager.isEnabled()) {
			processDue(server);
		}
	}

	static void onServerTick(MinecraftServer server) {
		if (server != null && !MadokuTimeManager.isEnabled()) {
			processDue(server);
		}
	}

	static long resolveAdaptiveDelayTicks(MinecraftServer server, String ownerId, long minimum, long maximum) {
		return SchedulerAdaptiveIntervalManager.resolve(adaptiveSystemIdForSchedulerOwner(ownerId), server, minimum, maximum);
	}

	static void clearAdaptiveDelayState(String ownerId) {
		SchedulerAdaptiveIntervalManager.clearSystem(adaptiveSystemIdForSchedulerOwner(ownerId));
	}

	static void clearQueuedRequests(String schedulerId) {
		if (schedulerId == null || schedulerId.isBlank()) return;
		SchedulerEntry entry = SCHEDULERS.get(schedulerId);
		if (entry == null || entry.tasks.isEmpty()) return;
		entry.tasks.clear();
		entry.touch(resolveCurrentSchedulerDay());
		markDirty();
	}

	static boolean hasQueuedTask(String schedulerId, String taskType) {
		if (schedulerId == null || schedulerId.isBlank()) return false;
		SchedulerEntry entry = SCHEDULERS.get(schedulerId);
		String normalizedTaskType = normalizeKey(taskType);
		if (entry == null || entry.tasks.isEmpty() || normalizedTaskType.isEmpty()) return false;
		for (ScheduledTask task : entry.tasks) {
			if (task != null && normalizedTaskType.equals(task.taskType)) return true;
		}
		return false;
	}

	static String createOrGetScheduler(MadokuSchedulerManager.SchedulerBinding binding, int expirationDays) {
		MadokuSchedulerManager.SchedulerBinding normalizedBinding = normalizeBinding(binding);
		int normalizedExpirationDays = normalizeExpirationDays(expirationDays);
		long currentDay = resolveCurrentSchedulerDay();
		String bindingKey = bindingKey(normalizedBinding);
		String existingId = SCHEDULER_IDS_BY_BINDING.get(bindingKey);
		if (existingId != null) {
			SchedulerEntry existing = SCHEDULERS.get(existingId);
			if (existing != null && existing.binding.sameTarget(normalizedBinding)) {
				existing.setExpirationDays(normalizedExpirationDays);
				existing.touch(currentDay);
				return existing.schedulerId;
			}
			SCHEDULER_IDS_BY_BINDING.remove(bindingKey);
		}

		SchedulerEntry created = new SchedulerEntry(UUID.randomUUID().toString(), normalizedBinding, normalizedExpirationDays, currentDay);
		SCHEDULERS.put(created.schedulerId, created);
		SCHEDULER_IDS_BY_BINDING.put(bindingKey, created.schedulerId);
		markDirty();
		return created.schedulerId;
	}

	static MadokuSchedulerManager.EnqueueStatus enqueue(
		String schedulerId, long delayTicks, String taskType, JsonObject payload, MadokuSchedulerManager.TickDomain domain) {
		SchedulerEntry entry = SCHEDULERS.get(schedulerId);
		if (entry == null) return MadokuSchedulerManager.EnqueueStatus.SCHEDULER_NOT_FOUND;
		String normalizedTaskType = normalizeKey(taskType);
		if (normalizedTaskType.isEmpty()) return MadokuSchedulerManager.EnqueueStatus.INVALID_TASK_TYPE;
		long nowTick = Math.max(0L, MadokuTimeManager.getGameplayTicks());
		entry.touch(resolveCurrentSchedulerDay());
		long dueTick = Math.max(0L, nowTick + Math.max(0L, delayTicks));
		entry.tasks.add(new ScheduledTask(entry.nextRequestId++, nowTick, dueTick,
			domain == null ? MadokuSchedulerManager.TickDomain.GAMEPLAY : domain,
			normalizedTaskType, payload));
		markDirty();
		return MadokuSchedulerManager.EnqueueStatus.ACCEPTED;
	}

	static void registerTaskHandler(String taskType, MadokuSchedulerManager.TaskHandler handler) {
		String normalized = normalizeKey(taskType);
		if (normalized.isEmpty() || handler == null) {
			throw new IllegalArgumentException("Task type and handler must not be blank.");
		}
		TASK_HANDLERS.put(normalized, handler);
	}

	static void unregisterTaskHandler(String taskType) {
		String normalized = normalizeKey(taskType);
		if (!normalized.isEmpty()) TASK_HANDLERS.remove(normalized);
	}

	private static void processDue(MinecraftServer server) {
		long nowTick = Math.max(0L, MadokuTimeManager.getGameplayTicks());
		long currentDay = resolveCurrentSchedulerDay();
		List<SchedulerEntry> snapshot = new ArrayList<>(SCHEDULERS.values());
		for (SchedulerEntry entry : snapshot) {
			if (entry == null) continue;
			if (entry.tasks.isEmpty()) {
				removeScheduler(entry);
				continue;
			}
			if (entry.isExpiredForStaleness(currentDay)) {
				removeExpiredScheduler(entry, "stale", nowTick, currentDay);
				continue;
			}
			ScheduledTask next = entry.tasks.peek();
			if (next == null) {
				removeScheduler(entry);
				continue;
			}
			// A future task does not need its target validated yet. This avoids
			// querying live chunk ticking state for every queued scheduler every tick.
			if (next.dueTick > nowTick) {
				continue;
			}
			if (!isRunnable(server, entry.binding)) {
				if (entry.isExpiredForInactivity(nowTick)) removeExpiredScheduler(entry, "inactive", nowTick, currentDay);
				continue;
			}
			entry.markRunnable(nowTick);
			processDueTasks(server, entry, nowTick, currentDay);
			if (entry.tasks.isEmpty()) removeScheduler(entry);
		}
	}

	private static void processDueTasks(MinecraftServer server, SchedulerEntry entry, long nowTick, long currentDay) {
		boolean changed = false;
		while (true) {
			ScheduledTask next = entry.tasks.peek();
			if (next == null || next.dueTick > nowTick) break;
			entry.tasks.poll();
			changed = true;
			entry.touch(currentDay);
			MadokuSchedulerManager.TaskHandler handler = TASK_HANDLERS.get(next.taskType);
			if (handler == null) {
				LOGGER.warn("Scheduler task type '{}' has no handler: scheduler={} binding={}", next.taskType, entry.schedulerId, describeBinding(entry.binding));
				continue;
			}
			try {
				handler.execute(
					server,
					new MadokuSchedulerManager.TaskContext(entry.schedulerId, next.requestId, nowTick, entry.binding, next.domain),
					next.payload.deepCopy()
				);
			} catch (RuntimeException exception) {
				LOGGER.error("Scheduler task failed: scheduler={} request_id={} task_type={}", entry.schedulerId, next.requestId, next.taskType, exception);
			}
		}
		if (changed) markDirty();
	}

	private static boolean isRunnable(MinecraftServer server, MadokuSchedulerManager.SchedulerBinding binding) {
		if (server == null || binding == null) return false;
		return switch (binding.getType()) {
			case GLOBAL -> true;
			case CHUNK -> isChunkRunnable(server, binding);
			case EVENT -> isEventRunnable(server, binding);
		};
	}

	private static boolean isChunkRunnable(MinecraftServer server, MadokuSchedulerManager.SchedulerBinding binding) {
		ServerLevel level = resolveLevel(server, binding.getLevelId());
		return level != null && MadokuChunkManager.isChunkLoaded(level, binding.getChunkX(), binding.getChunkZ())
			&& MadokuChunkManager.isChunkBlockTicking(level, binding.getChunkX(), binding.getChunkZ());
	}

	private static boolean isEventRunnable(MinecraftServer server, MadokuSchedulerManager.SchedulerBinding binding) {
		if (binding.getEventType() == null || binding.getEventId() == null || binding.getEventId().isBlank()) return false;
		return switch (binding.getEventType()) {
			case ENTITY -> findEntity(server, binding.getEntityUuid()) != null;
			case BLOCK -> isBlockValid(server, binding);
			case BLOCK_ENTITY -> isBlockEntityValid(server, binding);
		};
	}

	private static boolean isBlockValid(MinecraftServer server, MadokuSchedulerManager.SchedulerBinding binding) {
		ServerLevel level = resolveLevel(server, binding.getLevelId());
		Long packedPos = binding.getBlockPosLong();
		if (level == null || packedPos == null) return false;
		BlockPos blockPos = BlockPos.of(packedPos);
		int chunkX = blockPos.getX() >> 4;
		int chunkZ = blockPos.getZ() >> 4;
		return MadokuChunkManager.isChunkLoaded(level, chunkX, chunkZ)
			&& MadokuChunkManager.isChunkBlockTicking(level, chunkX, chunkZ)
			&& !level.isEmptyBlock(blockPos);
	}

	private static boolean isBlockEntityValid(MinecraftServer server, MadokuSchedulerManager.SchedulerBinding binding) {
		ServerLevel level = resolveLevel(server, binding.getLevelId());
		Long packedPos = binding.getBlockPosLong();
		if (level == null || packedPos == null) return false;
		BlockPos blockPos = BlockPos.of(packedPos);
		int chunkX = blockPos.getX() >> 4;
		int chunkZ = blockPos.getZ() >> 4;
		return MadokuChunkManager.isChunkLoaded(level, chunkX, chunkZ)
			&& MadokuChunkManager.isChunkBlockTicking(level, chunkX, chunkZ)
			&& level.getBlockEntity(blockPos) != null;
	}

	private static Entity findEntity(MinecraftServer server, UUID uuid) {
		if (server == null || uuid == null) return null;
		Entity player = server.getPlayerList().getPlayer(uuid);
		if (player != null && player.isAlive()) return player;
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(uuid);
			if (entity != null && entity.isAlive()) return entity;
		}
		return null;
	}

	private static JsonObject createDefaultData() {
		return JSONFormatManager.object().put("gameplay-ticks", 0L).array("schedulers", ignored -> { }).build();
	}

	private static JsonObject toPersistedData() {
		return JSONFormatManager.object().put("gameplay-ticks", Math.max(0L, MadokuTimeManager.getGameplayTicks())).array("schedulers", schedulers -> {
			for (SchedulerEntry entry : SCHEDULERS.values()) if (entry != null && !entry.tasks.isEmpty()) schedulers.add(entry.schedulerId);
		}).build();
	}

	private static void applyPersistedData(MinecraftServer server, JsonObject source) {
		SCHEDULERS.clear();
		SCHEDULER_IDS_BY_BINDING.clear();
		if (source == null) return;
		MadokuTimeManager.setGameplayTicks(Math.max(0L, getLong(source, "gameplay-ticks", 0L)));
		JsonArray schedulers = getArray(source, "schedulers");
		if (schedulers == null) return;
		for (JsonElement element : schedulers) {
			SchedulerEntry entry = element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
				? loadSchedulerEntry(server, element.getAsString()) : SchedulerEntry.fromJson(element);
			if (entry == null || entry.tasks.isEmpty()) continue;
			SCHEDULERS.put(entry.schedulerId, entry);
			SCHEDULER_IDS_BY_BINDING.put(bindingKey(entry.binding), entry.schedulerId);
		}
	}

	private static long getAutoSaveIntervalTicks(MinecraftServer server) {
		return DataWorldChunkManager.getAutoSaveIntervalTicks();
	}

	private static SchedulerEntry loadSchedulerEntry(MinecraftServer server, String schedulerId) {
		String normalizedSchedulerId = schedulerId == null ? "" : schedulerId.trim();
		Path file = resolveSchedulerFile(server, normalizedSchedulerId, false);
		if (server == null || normalizedSchedulerId.isBlank() || file == null || !Files.isRegularFile(file)) return null;
		try {
			return SchedulerEntry.fromJson(JSONFormatManager.readManagedDocument(file).data());
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load scheduler data file {}", file, exception);
			return null;
		}
	}

	private static void saveSchedulerFiles(MinecraftServer server) {
		if (server == null) return;
		Set<String> activeSchedulerIds = new LinkedHashSet<>();
		for (SchedulerEntry entry : SCHEDULERS.values()) {
			if (entry == null || entry.tasks.isEmpty()) continue;
			activeSchedulerIds.add(entry.schedulerId);
			Path file = resolveSchedulerFile(server, entry.schedulerId, false);
			if (file == null) continue;
			JsonObject data = entry.toJson();
			JsonObject general = new JsonObject();
			general.addProperty("scheduler-id", entry.schedulerId);
			DataSaveCoordinatorManager.submit("scheduler-" + entry.schedulerId, file,
				() -> JSONFormatManager.writeManagedDocument(file, data, general, JSONTypeManager.STATIC_DATA));
		}
		Path schedulerDirectory = resolveSchedulerDirectory(server, false);
		Set<String> capturedActiveSchedulerIds = new LinkedHashSet<>(activeSchedulerIds);
		DataSaveCoordinatorManager.submit("scheduler-cleanup", schedulerDirectory,
			() -> deleteStaleSchedulerFiles(server, capturedActiveSchedulerIds));
	}

	private static void deleteStaleSchedulerFiles(MinecraftServer server, Set<String> activeSchedulerIds) {
		Path schedulerDirectory = resolveSchedulerDirectory(server, false);
		if (schedulerDirectory == null || !Files.isDirectory(schedulerDirectory)) return;
		try (var files = Files.list(schedulerDirectory)) {
			files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
				String fileName = path.getFileName().toString();
				String schedulerId = fileName.substring(0, fileName.length() - ".json".length());
				if (activeSchedulerIds.contains(schedulerId)) return;
				try { Files.deleteIfExists(path); }
				catch (IOException exception) { LOGGER.error("Failed to delete stale scheduler data file {}", path, exception); }
			});
		} catch (IOException exception) {
			LOGGER.error("Failed to enumerate scheduler data directory {}", schedulerDirectory, exception);
		}
	}

	private static Path resolveSchedulerDirectory(MinecraftServer server, boolean createDirectories) {
		if (server == null) return null;
		Path directory = MadokuJSONManager.getWorldRootDirectory(server).resolve(DATA_FOLDER_NAME).resolve(SCHEDULER_FILES_DIRECTORY);
		if (!createDirectories) return directory;
		try { Files.createDirectories(directory); return directory; }
		catch (IOException exception) { throw new IllegalStateException("Failed to create scheduler directory: " + directory, exception); }
	}

	private static Path resolveSchedulerFile(MinecraftServer server, String schedulerId, boolean createDirectories) {
		String normalized = schedulerId == null ? "" : schedulerId.trim();
		if (normalized.isBlank()) return null;
		Path directory = resolveSchedulerDirectory(server, createDirectories);
		return directory == null ? null : directory.resolve(normalized + ".json");
	}

	private static void removeScheduler(SchedulerEntry entry) {
		if (entry == null) return;
		SCHEDULERS.remove(entry.schedulerId);
		SCHEDULER_IDS_BY_BINDING.remove(bindingKey(entry.binding), entry.schedulerId);
		markDirty();
	}

	private static void removeExpiredScheduler(SchedulerEntry entry, String reason, long nowTick, long currentDay) {
		if (entry == null) return;
		LOGGER.debug("Expiring scheduler {} reason={} binding={} now_tick={} current_day={} expiration_days={}",
			entry.schedulerId, reason, describeBinding(entry.binding), nowTick, currentDay, entry.expirationDays);
		removeScheduler(entry);
	}

	private static void markDirty() { dirty = true; }

	private static MadokuSchedulerManager.SchedulerBinding normalizeBinding(MadokuSchedulerManager.SchedulerBinding binding) {
		if (binding == null) throw new IllegalArgumentException("Scheduler binding must not be null.");
		return binding.normalized();
	}

	private static String bindingKey(MadokuSchedulerManager.SchedulerBinding binding) {
		if (binding == null) return "";
		return binding.getType().id() + '\u0000' + binding.getKey() + '\u0000'
			+ (binding.getLevelId() == null ? "" : binding.getLevelId()) + '\u0000'
			+ binding.getChunkX() + '\u0000' + binding.getChunkZ() + '\u0000'
			+ (binding.getEventType() == null ? "" : binding.getEventType().id()) + '\u0000'
			+ (binding.getEventId() == null ? "" : binding.getEventId());
	}

	private static String describeBinding(MadokuSchedulerManager.SchedulerBinding binding) {
		return binding == null ? "unknown" : binding.getType().id() + ":" + binding.getKey();
	}

	private static ServerLevel resolveLevel(MinecraftServer server, String levelId) {
		if (server == null || levelId == null || levelId.isBlank()) return null;
		Identifier location = Identifier.tryParse(levelId);
		if (location == null) location = Identifier.tryParse(normalizeLevelId(levelId));
		if (location == null) return null;
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
	}

	private static long resolveCurrentSchedulerDay() {
		return Math.max(0L, Math.floorDiv(MadokuTimeManager.getGameplayTicks(), MadokuTimeManager.MINECRAFT_TICKS_PER_CYCLE));
	}

	private static JsonArray getArray(JsonObject object, String key) {
		JsonElement element = object == null ? null : object.get(key);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
	}

	private static JsonObject getObject(JsonObject object, String key) {
		JsonElement element = object == null ? null : object.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	private static long getLong(JsonObject object, String key, long fallback) {
		JsonElement element = object == null ? null : object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return fallback;
		try { return element.getAsLong(); } catch (RuntimeException exception) { return fallback; }
	}

	private static int getInt(JsonObject object, String key, int fallback) {
		JsonElement element = object == null ? null : object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return fallback;
		try { return element.getAsInt(); } catch (RuntimeException exception) { return fallback; }
	}

	private static String getString(JsonObject object, String key, String fallback) {
		JsonElement element = object == null ? null : object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return fallback;
		String value = element.getAsString();
		return value == null ? fallback : value.trim();
	}

	private static String normalizeKey(String value) {
		return value == null ? "" : value.trim().toLowerCase();
	}

	private static String adaptiveSystemIdForSchedulerOwner(String ownerId) {
		String normalized = normalizeKey(ownerId);
		return normalized.isEmpty() ? "scheduler:default" : "scheduler:" + normalized;
	}

	private static int normalizeExpirationDays(int expirationDays) { return Math.max(0, expirationDays); }

	private static String normalizeLevelId(String levelId) {
		if (levelId == null) return null;
		String trimmed = levelId.trim();
		if (trimmed.isEmpty()) return null;
		if (Identifier.tryParse(trimmed) != null) return trimmed;
		int slashIndex = trimmed.lastIndexOf('/');
		int closeBracketIndex = trimmed.lastIndexOf(']');
		if (slashIndex >= 0 && closeBracketIndex > slashIndex) {
			String candidate = trimmed.substring(slashIndex + 1, closeBracketIndex).trim();
			if (Identifier.tryParse(candidate) != null) return candidate;
		}
		return trimmed;
	}

	private static final class SchedulerEntry {
		private final String schedulerId;
		private final MadokuSchedulerManager.SchedulerBinding binding;
		private final PriorityQueue<ScheduledTask> tasks = new PriorityQueue<>(
			Comparator.comparingLong((ScheduledTask task) -> task.dueTick).thenComparingLong(task -> task.requestId));
		private int expirationDays;
		private long lastTouchedDay;
		private long lastRunnableGameplayTick;
		private long nextRequestId = 1L;

		private SchedulerEntry(String schedulerId, MadokuSchedulerManager.SchedulerBinding binding, int expirationDays, long lastTouchedDay) {
			this.schedulerId = schedulerId;
			this.binding = binding;
			this.expirationDays = normalizeExpirationDays(expirationDays);
			this.lastTouchedDay = Math.max(0L, lastTouchedDay);
			this.lastRunnableGameplayTick = Math.max(0L, MadokuTimeManager.getGameplayTicks());
		}

		private void setExpirationDays(int value) {
			int normalized = normalizeExpirationDays(value);
			if (expirationDays != normalized) { expirationDays = normalized; markDirty(); }
		}

		private void touch(long currentDay) {
			long normalized = Math.max(0L, currentDay);
			if (lastTouchedDay != normalized) { lastTouchedDay = normalized; markDirty(); }
		}

		private void markRunnable(long gameplayTick) { lastRunnableGameplayTick = Math.max(0L, gameplayTick); }

		private boolean isExpiredForStaleness(long currentDay) {
			return expirationDays > 0 && currentDay >= lastTouchedDay && currentDay - lastTouchedDay >= expirationDays;
		}

		private boolean isExpiredForInactivity(long nowTick) {
			return nowTick >= lastRunnableGameplayTick
				&& nowTick - lastRunnableGameplayTick >= getInactiveExpirationTicks();
		}

		private JsonObject toJson() {
			JSONFormatManager.ArrayBuilder tasksArray = JSONFormatManager.array();
			List<ScheduledTask> snapshot = new ArrayList<>(tasks);
			snapshot.sort(Comparator.comparingLong((ScheduledTask task) -> task.dueTick).thenComparingLong(task -> task.requestId));
			for (ScheduledTask task : snapshot) tasksArray.add(task.toJson());
			return JSONFormatManager.object().put("scheduler-id", schedulerId)
				.object(GROUP_GENERAL, general -> general.put(FIELD_EXPIRATION, expirationDays).put(FIELD_LAST_TOUCHED_DAY, Math.max(0L, lastTouchedDay)))
				.put("binding", binding.toJson()).put("next-request-id", Math.max(1L, nextRequestId))
				.put("tasks", tasksArray.build()).build();
		}

		private static SchedulerEntry fromJson(JsonElement element) {
			if (element == null || !element.isJsonObject()) return null;
			JsonObject source = element.getAsJsonObject();
			String schedulerId = getString(source, "scheduler-id", "");
			MadokuSchedulerManager.SchedulerBinding binding = MadokuSchedulerManager.SchedulerBinding.fromJson(getObject(source, "binding"));
			JsonObject general = getObject(source, GROUP_GENERAL);
			if (schedulerId.isBlank() || binding == null) return null;
			SchedulerEntry entry = new SchedulerEntry(schedulerId, binding,
				normalizeExpirationDays(getInt(general, FIELD_EXPIRATION, DEFAULT_EXPIRATION_DAYS)),
				Math.max(0L, getLong(general, FIELD_LAST_TOUCHED_DAY, resolveCurrentSchedulerDay())));
			entry.nextRequestId = Math.max(1L, getLong(source, "next-request-id", 1L));
			JsonArray tasksArray = getArray(source, "tasks");
			if (tasksArray != null) for (JsonElement taskElement : tasksArray) {
				ScheduledTask task = ScheduledTask.fromJson(taskElement);
				if (task != null) { entry.tasks.add(task); entry.nextRequestId = Math.max(entry.nextRequestId, task.requestId + 1L); }
			}
			return entry;
		}
	}

	private static final class ScheduledTask {
		private final long requestId;
		private final long enqueuedTick;
		private final long dueTick;
		private final MadokuSchedulerManager.TickDomain domain;
		private final String taskType;
		private final JsonObject payload;

		private ScheduledTask(long requestId, long enqueuedTick, long dueTick, MadokuSchedulerManager.TickDomain domain, String taskType, JsonObject payload) {
			this.requestId = requestId;
			this.enqueuedTick = enqueuedTick;
			this.dueTick = dueTick;
			this.domain = domain;
			this.taskType = taskType;
			this.payload = payload == null ? new JsonObject() : payload.deepCopy();
		}

		private JsonObject toJson() {
			return JSONFormatManager.object().put("request-id", requestId).put("enqueued-tick", enqueuedTick)
				.put("due-tick", dueTick).put("domain", domain.id()).put("task-type", taskType)
				.put("payload", payload.deepCopy()).build();
		}

		private static ScheduledTask fromJson(JsonElement element) {
			if (element == null || !element.isJsonObject()) return null;
			JsonObject source = element.getAsJsonObject();
			MadokuSchedulerManager.TickDomain domain = MadokuSchedulerManager.TickDomain.fromId(getString(source, "domain", ""));
			String taskType = normalizeKey(getString(source, "task-type", ""));
			if (domain == null || taskType.isBlank()) return null;
			JsonElement payloadElement = source.get("payload");
			JsonObject payload = payloadElement != null && payloadElement.isJsonObject() ? payloadElement.getAsJsonObject().deepCopy() : new JsonObject();
			return new ScheduledTask(Math.max(1L, getLong(source, "request-id", 1L)), Math.max(0L, getLong(source, "enqueued-tick", 0L)),
				Math.max(0L, getLong(source, "due-tick", 0L)), domain, taskType, payload);
		}
	}
}

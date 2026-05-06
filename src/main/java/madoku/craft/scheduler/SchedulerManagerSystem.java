package madoku.craft.scheduler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import madoku.craft.chunk.ChunkManagerSystem;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.data.DataManagerSystem;
import madoku.craft.time.MadokuTime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SchedulerManagerSystem {
	private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerManagerSystem.class);
	private static final String DATA_FOLDER_NAME = "madoku-craft-schedulers";
	private static final String DATA_FILE_NAME = "madoku-schedulers";
	private static final String SCHEDULER_FILES_DIRECTORY = "schedulers";
	private static final String GROUP_GENERAL = "general";
	private static final String FIELD_EXPIRATION = "expiration";
	private static final String FIELD_LAST_TOUCHED_DAY = "last-touched-day";
	private static final int DEFAULT_EXPIRATION_DAYS = 14;
	private static final long INACTIVE_EXPIRATION_TICKS =
		5L * 60L * MadokuTicks.TICKS_PER_SECOND;
	private static final Comparator<ScheduledTask> TASK_COMPARATOR =
		Comparator.comparingLong((ScheduledTask task) -> task.dueTick)
			.thenComparingLong(task -> task.requestId);

	private static final Map<String, SchedulerEntry> SCHEDULERS = new LinkedHashMap<>();
	private static final Map<String, String> SCHEDULER_IDS_BY_BINDING = new HashMap<>();
	private static final Map<String, TaskHandler> TASK_HANDLERS = new HashMap<>();

	private static long lastAutosaveBucket = Long.MIN_VALUE;
	private static boolean dirty;

	private SchedulerManagerSystem() {
	}

	public static void reset() {
		SCHEDULERS.clear();
		SCHEDULER_IDS_BY_BINDING.clear();
		lastAutosaveBucket = Long.MIN_VALUE;
		dirty = false;
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		reset();
		JsonObject persistedData = DataManagerSystem.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		applyPersistedData(server, persistedData);
		dirty = false;
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), getAutoSaveIntervalTicks(server));
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		saveSchedulerFiles(server);
		DataManagerSystem.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, toPersistedData());
		dirty = false;
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), getAutoSaveIntervalTicks(server));
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		long currentBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), getAutoSaveIntervalTicks(server));
		if (currentBucket == lastAutosaveBucket) {
			return;
		}
		if (!dirty) {
			lastAutosaveBucket = currentBucket;
			return;
		}

		saveSchedulerFiles(server);
		DataManagerSystem.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, toPersistedData());
		dirty = false;
		lastAutosaveBucket = currentBucket;
	}

	public static void onClockTick(MinecraftServer server) {
		if (server == null || !MadokuTime.isEnabled()) {
			return;
		}

		processDue(server);
	}

	public static void onServerTick(MinecraftServer server) {
		if (server == null || MadokuTime.isEnabled()) {
			return;
		}

		processDue(server);
	}

	public static void clearQueuedRequests(String schedulerId) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return;
		}

		SchedulerEntry entry = SCHEDULERS.get(schedulerId);
		if (entry == null || entry.tasks.isEmpty()) {
			return;
		}

		entry.tasks.clear();
		entry.touch(resolveCurrentSchedulerDay(null));
		markDirty();
	}

	public static boolean hasQueuedTask(String schedulerId, String taskType) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return false;
		}

		SchedulerEntry entry = SCHEDULERS.get(schedulerId);
		if (entry == null || entry.tasks.isEmpty()) {
			return false;
		}

		String normalizedTaskType = normalizeKey(taskType);
		if (normalizedTaskType.isEmpty()) {
			return false;
		}

		for (ScheduledTask task : entry.tasks) {
			if (task != null && normalizedTaskType.equals(task.taskType)) {
				return true;
			}
		}
		return false;
	}

	public static String createOrGetScheduler(SchedulerBinding binding) {
		return createOrGetScheduler(binding, DEFAULT_EXPIRATION_DAYS);
	}

	public static String createOrGetScheduler(SchedulerBinding binding, int expirationDays) {
		SchedulerBinding normalizedBinding = normalizeBinding(binding);
		int normalizedExpirationDays = normalizeExpirationDays(expirationDays);
		long currentDay = resolveCurrentSchedulerDay(null);
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

		SchedulerEntry created = new SchedulerEntry(
			UUID.randomUUID().toString(),
			normalizedBinding,
			normalizedExpirationDays,
			currentDay
		);
		SCHEDULERS.put(created.schedulerId, created);
		SCHEDULER_IDS_BY_BINDING.put(bindingKey, created.schedulerId);
		markDirty();
		return created.schedulerId;
	}

	public static EnqueueStatus enqueue(
		String schedulerId,
		long delayTicks,
		String taskType,
		JsonObject payload,
		TickDomain domain
	) {
		SchedulerEntry entry = SCHEDULERS.get(schedulerId);
		if (entry == null) {
			return EnqueueStatus.SCHEDULER_NOT_FOUND;
		}

		String normalizedTaskType = normalizeKey(taskType);
		if (normalizedTaskType.isEmpty()) {
			return EnqueueStatus.INVALID_TASK_TYPE;
		}

		long nowTick = Math.max(0L, MadokuTicks.getGameplayTicks());
		entry.touch(resolveCurrentSchedulerDay(null));
		long dueTick = Math.max(0L, nowTick + Math.max(0L, delayTicks));
		entry.tasks.add(new ScheduledTask(
			entry.nextRequestId++,
			nowTick,
			dueTick,
			Objects.requireNonNullElse(domain, TickDomain.GAMEPLAY),
			normalizedTaskType,
			payload == null ? new JsonObject() : payload.deepCopy()
		));
		markDirty();
		return EnqueueStatus.ACCEPTED;
	}

	public static void registerTaskHandler(String taskType, TaskHandler handler) {
		String normalized = normalizeKey(taskType);
		if (normalized.isEmpty() || handler == null) {
			throw new IllegalArgumentException("Task type and handler must not be blank.");
		}
		TASK_HANDLERS.put(normalized, handler);
	}

	public static void unregisterTaskHandler(String taskType) {
		String normalized = normalizeKey(taskType);
		if (normalized.isEmpty()) {
			return;
		}
		TASK_HANDLERS.remove(normalized);
	}

	public static String normalizeLevelIdentifier(String levelId) {
		return normalizeLevelId(levelId);
	}

	private static void processDue(MinecraftServer server) {
		long nowTick = Math.max(0L, MadokuTicks.getGameplayTicks());
		long currentDay = resolveCurrentSchedulerDay(server);
		List<SchedulerEntry> snapshot = new ArrayList<>(SCHEDULERS.values());
		for (SchedulerEntry entry : snapshot) {
			if (entry == null) {
				continue;
			}
			if (entry.tasks.isEmpty()) {
				removeScheduler(entry);
				continue;
			}
			if (entry.isExpiredForStaleness(currentDay)) {
				removeExpiredScheduler(entry, "stale", nowTick, currentDay);
				continue;
			}

			boolean runnable = isRunnable(server, entry.binding);
			if (!runnable) {
				if (entry.isExpiredForInactivity(nowTick)) {
					removeExpiredScheduler(entry, "inactive", nowTick, currentDay);
				}
				continue;
			}

			entry.markRunnable(nowTick);
			processDueTasks(server, entry, nowTick, currentDay);
			if (entry.tasks.isEmpty()) {
				removeScheduler(entry);
			}
		}
	}

	private static void processDueTasks(MinecraftServer server, SchedulerEntry entry, long nowTick, long currentDay) {
		boolean changed = false;
		while (true) {
			ScheduledTask next = entry.tasks.peek();
			if (next == null || next.dueTick > nowTick) {
				break;
			}

			entry.tasks.poll();
			changed = true;
			entry.touch(currentDay);
			TaskHandler handler = TASK_HANDLERS.get(next.taskType);
			if (handler == null) {
				LOGGER.warn(
					"Scheduler task type '{}' has no handler: scheduler={} binding={}",
					next.taskType,
					entry.schedulerId,
					describeBinding(entry.binding)
				);
				continue;
			}

			try {
				handler.execute(
					server,
					new TaskContext(entry.schedulerId, next.requestId, nowTick, entry.binding, next.domain),
					next.payload.deepCopy()
				);
			} catch (RuntimeException exception) {
				LOGGER.error(
					"Scheduler task failed: scheduler={} request_id={} task_type={}",
					entry.schedulerId,
					next.requestId,
					next.taskType,
					exception
				);
			}
		}

		if (changed) {
			markDirty();
		}
	}

	private static boolean isRunnable(MinecraftServer server, SchedulerBinding binding) {
		if (server == null || binding == null) {
			return false;
		}

		return switch (binding.type) {
			case GLOBAL -> true;
			case CHUNK -> isChunkRunnable(server, binding);
			case EVENT -> isEventRunnable(server, binding);
		};
	}

	private static boolean isChunkRunnable(MinecraftServer server, SchedulerBinding binding) {
		ServerLevel level = resolveLevel(server, binding.levelId);
		return level != null
			&& ChunkManagerSystem.isChunkLoaded(level, binding.chunkX, binding.chunkZ)
			&& ChunkManagerSystem.isChunkBlockTicking(level, binding.chunkX, binding.chunkZ);
	}

	private static boolean isEventRunnable(MinecraftServer server, SchedulerBinding binding) {
		if (binding.eventType == null || binding.eventId == null || binding.eventId.isBlank()) {
			return false;
		}

		return switch (binding.eventType) {
			case ENTITY -> findEntity(server, binding.eventId) != null;
			case BLOCK -> isBlockValid(server, binding);
			case BLOCK_ENTITY -> isBlockEntityValid(server, binding);
		};
	}

	private static boolean isBlockValid(MinecraftServer server, SchedulerBinding binding) {
		ServerLevel level = resolveLevel(server, binding.levelId);
		Long packedPos = parseLong(binding.eventId);
		if (level == null || packedPos == null) {
			return false;
		}

		BlockPos blockPos = BlockPos.of(packedPos);
		int chunkX = blockPos.getX() >> 4;
		int chunkZ = blockPos.getZ() >> 4;
		if (!ChunkManagerSystem.isChunkLoaded(level, chunkX, chunkZ) || !ChunkManagerSystem.isChunkBlockTicking(level, chunkX, chunkZ)) {
			return false;
		}
		return !level.isEmptyBlock(blockPos);
	}

	private static boolean isBlockEntityValid(MinecraftServer server, SchedulerBinding binding) {
		ServerLevel level = resolveLevel(server, binding.levelId);
		Long packedPos = parseLong(binding.eventId);
		if (level == null || packedPos == null) {
			return false;
		}

		BlockPos blockPos = BlockPos.of(packedPos);
		int chunkX = blockPos.getX() >> 4;
		int chunkZ = blockPos.getZ() >> 4;
		if (!ChunkManagerSystem.isChunkLoaded(level, chunkX, chunkZ) || !ChunkManagerSystem.isChunkBlockTicking(level, chunkX, chunkZ)) {
			return false;
		}
		return level.getBlockEntity(blockPos) != null;
	}

	private static Entity findEntity(MinecraftServer server, String entityId) {
		UUID uuid = parseUuid(entityId);
		if (uuid == null) {
			return null;
		}

		Entity player = server.getPlayerList().getPlayer(uuid);
		if (player != null && player.isAlive()) {
			return player;
		}

		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(uuid);
			if (entity != null && entity.isAlive()) {
				return entity;
			}
		}
		return null;
	}

	private static JsonObject createDefaultData() {
		JsonObject root = new JsonObject();
		root.addProperty("gameplay-ticks", 0L);
		root.add("schedulers", new JsonArray());
		return root;
	}

	private static JsonObject toPersistedData() {
		JsonObject root = createDefaultData();
		root.addProperty("gameplay-ticks", Math.max(0L, MadokuTicks.getGameplayTicks()));
		JsonArray schedulers = new JsonArray();
		for (SchedulerEntry entry : SCHEDULERS.values()) {
			if (entry != null && !entry.tasks.isEmpty()) {
				schedulers.add(entry.schedulerId);
			}
		}
		root.add("schedulers", schedulers);
		return root;
	}

	private static void applyPersistedData(MinecraftServer server, JsonObject source) {
		SCHEDULERS.clear();
		SCHEDULER_IDS_BY_BINDING.clear();
		if (source == null) {
			return;
		}

		MadokuTicks.setGameplayTicks(Math.max(0L, getLong(source, "gameplay-ticks", 0L)));
		JsonArray schedulers = getArray(source, "schedulers");
		if (schedulers == null) {
			return;
		}

		for (JsonElement element : schedulers) {
			SchedulerEntry entry = null;
			if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
				entry = loadSchedulerEntry(server, element.getAsString());
			} else {
				entry = SchedulerEntry.fromJson(element);
			}
			if (entry == null || entry.tasks.isEmpty()) {
				continue;
			}
			SCHEDULERS.put(entry.schedulerId, entry);
			SCHEDULER_IDS_BY_BINDING.put(bindingKey(entry.binding), entry.schedulerId);
		}
	}

	private static long getAutoSaveIntervalTicks(MinecraftServer server) {
		return DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
	}

	private static SchedulerEntry loadSchedulerEntry(MinecraftServer server, String schedulerId) {
		String normalizedSchedulerId = schedulerId == null ? "" : schedulerId.trim();
		if (server == null || normalizedSchedulerId.isBlank()) {
			return null;
		}

		Path file = resolveSchedulerFile(server, normalizedSchedulerId, false);
		if (file == null || !Files.isRegularFile(file)) {
			return null;
		}

		try {
			return SchedulerEntry.fromJson(JsonStaticSystem.readManagedDocument(file).main());
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load scheduler data file {}", file, exception);
			return null;
		}
	}

	private static void saveSchedulerFiles(MinecraftServer server) {
		if (server == null) {
			return;
		}

		Set<String> activeSchedulerIds = new LinkedHashSet<>();
		for (SchedulerEntry entry : SCHEDULERS.values()) {
			if (entry == null || entry.tasks.isEmpty()) {
				continue;
			}

			activeSchedulerIds.add(entry.schedulerId);
			Path file = resolveSchedulerFile(server, entry.schedulerId, true);
			if (file == null) {
				continue;
			}

			try {
				JsonObject general = new JsonObject();
				general.addProperty("scheduler-id", entry.schedulerId);
				JsonStaticSystem.writeManagedDocument(file, entry.toJson(), general);
			} catch (IOException | RuntimeException exception) {
				LOGGER.error("Failed to save scheduler data file {}", file, exception);
			}
		}

		deleteStaleSchedulerFiles(server, activeSchedulerIds);
	}

	private static void deleteStaleSchedulerFiles(MinecraftServer server, Set<String> activeSchedulerIds) {
		Path schedulerDirectory = resolveSchedulerDirectory(server, false);
		if (schedulerDirectory == null || !Files.isDirectory(schedulerDirectory)) {
			return;
		}

		try (var files = Files.list(schedulerDirectory)) {
			files.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(".json"))
				.forEach(path -> {
					String fileName = path.getFileName().toString();
					String schedulerId = fileName.substring(0, fileName.length() - ".json".length());
					if (activeSchedulerIds.contains(schedulerId)) {
						return;
					}

					try {
						Files.deleteIfExists(path);
					} catch (IOException exception) {
						LOGGER.error("Failed to delete stale scheduler data file {}", path, exception);
					}
				});
		} catch (IOException exception) {
			LOGGER.error("Failed to enumerate scheduler data directory {}", schedulerDirectory, exception);
		}
	}

	private static Path resolveSchedulerDirectory(MinecraftServer server, boolean createDirectories) {
		if (server == null) {
			return null;
		}

		Path schedulerDirectory = JsonManagerSystem.getWorldRootDirectory(server)
			.resolve(DATA_FOLDER_NAME)
			.resolve(SCHEDULER_FILES_DIRECTORY);
		if (!createDirectories) {
			return schedulerDirectory;
		}

		try {
			Files.createDirectories(schedulerDirectory);
			return schedulerDirectory;
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to create scheduler directory: " + schedulerDirectory, exception);
		}
	}

	private static Path resolveSchedulerFile(MinecraftServer server, String schedulerId, boolean createDirectories) {
		String normalizedSchedulerId = schedulerId == null ? "" : schedulerId.trim();
		if (normalizedSchedulerId.isBlank()) {
			return null;
		}

		Path schedulerDirectory = resolveSchedulerDirectory(server, createDirectories);
		return schedulerDirectory == null ? null : schedulerDirectory.resolve(normalizedSchedulerId + ".json");
	}

	private static void removeScheduler(SchedulerEntry entry) {
		if (entry == null) {
			return;
		}

		SCHEDULERS.remove(entry.schedulerId);
		SCHEDULER_IDS_BY_BINDING.remove(bindingKey(entry.binding), entry.schedulerId);
		markDirty();
	}

	private static void removeExpiredScheduler(SchedulerEntry entry, String reason, long nowTick, long currentDay) {
		if (entry == null) {
			return;
		}

		LOGGER.debug(
			"Expiring scheduler {} reason={} binding={} now_tick={} current_day={} expiration_days={}",
			entry.schedulerId,
			reason,
			describeBinding(entry.binding),
			nowTick,
			currentDay,
			entry.expirationDays
		);
		removeScheduler(entry);
	}

	private static void markDirty() {
		dirty = true;
	}

	private static SchedulerBinding normalizeBinding(SchedulerBinding binding) {
		if (binding == null) {
			throw new IllegalArgumentException("Scheduler binding must not be null.");
		}
		return binding.normalized();
	}

	private static String bindingKey(SchedulerBinding binding) {
		if (binding == null) {
			return "";
		}
		String levelId = binding.levelId == null ? "" : binding.levelId;
		String eventType = binding.eventType == null ? "" : binding.eventType.id;
		String eventId = binding.eventId == null ? "" : binding.eventId;
		return binding.type.id + '\u0000'
			+ binding.key + '\u0000'
			+ levelId + '\u0000'
			+ binding.chunkX + '\u0000'
			+ binding.chunkZ + '\u0000'
			+ eventType + '\u0000'
			+ eventId;
	}

	private static String describeBinding(SchedulerBinding binding) {
		if (binding == null) {
			return "unknown";
		}
		return binding.type.id + ":" + binding.key;
	}

	private static ServerLevel resolveLevel(MinecraftServer server, String levelId) {
		if (server == null || levelId == null || levelId.isBlank()) {
			return null;
		}
		Identifier location = Identifier.tryParse(levelId);
		if (location == null) {
			location = Identifier.tryParse(normalizeLevelId(levelId));
		}
		if (location == null) {
			return null;
		}
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, location);
		return server.getLevel(key);
	}

	private static long resolveCurrentSchedulerDay(MinecraftServer server) {
		// Use gameplay-tick days, not world absolute-day time, so `/time` commands
		// do not instantly age and expire active schedulers.
		return Math.max(0L, Math.floorDiv(MadokuTicks.getGameplayTicks(), MadokuTime.MINECRAFT_TICKS_PER_CYCLE));
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
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsLong();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static int getInt(JsonObject object, String key, int fallback) {
		JsonElement element = object == null ? null : object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsInt();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static String getString(JsonObject object, String key, String fallback) {
		JsonElement element = object == null ? null : object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}
		String value = element.getAsString();
		return value == null ? fallback : value.trim();
	}

	private static String getNullableString(JsonObject object, String key) {
		JsonElement element = object == null ? null : object.get(key);
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return null;
		}
		String value = element.getAsString();
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static UUID parseUuid(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(value.trim());
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static Long parseLong(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private static String normalizeKey(String value) {
		return value == null ? "" : value.trim().toLowerCase();
	}

	private static int normalizeExpirationDays(int expirationDays) {
		return Math.max(0, expirationDays);
	}

	private static String normalizeLevelId(String levelId) {
		if (levelId == null) {
			return null;
		}
		String trimmed = levelId.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		if (Identifier.tryParse(trimmed) != null) {
			return trimmed;
		}

		int slashIndex = trimmed.lastIndexOf('/');
		int closeBracketIndex = trimmed.lastIndexOf(']');
		if (slashIndex >= 0 && closeBracketIndex > slashIndex) {
			String candidate = trimmed.substring(slashIndex + 1, closeBracketIndex).trim();
			if (Identifier.tryParse(candidate) != null) {
				return candidate;
			}
		}
		return trimmed;
	}

	@FunctionalInterface
	public interface TaskHandler {
		void execute(MinecraftServer server, TaskContext context, JsonObject payload);
	}

	public enum EnqueueStatus {
		ACCEPTED,
		SCHEDULER_NOT_FOUND,
		INVALID_TASK_TYPE,
		QUEUE_FULL
	}

	public enum TickDomain {
		GAMEPLAY("gameplay");

		private final String id;

		TickDomain(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		static TickDomain fromId(String value) {
			return "gameplay".equals(normalizeKey(value)) || "time".equals(normalizeKey(value)) ? GAMEPLAY : null;
		}
	}

	public enum SchedulerType {
		GLOBAL("global"),
		CHUNK("chunk"),
		EVENT("event");

		private final String id;

		SchedulerType(String id) {
			this.id = id;
		}

		static SchedulerType fromId(String value) {
			String normalized = normalizeKey(value);
			for (SchedulerType type : values()) {
				if (type.id.equals(normalized)) {
					return type;
				}
			}
			return null;
		}
	}

	public enum EventType {
		ENTITY("entity"),
		BLOCK("block"),
		BLOCK_ENTITY("blockentity");

		private final String id;

		EventType(String id) {
			this.id = id;
		}

		static EventType fromId(String value) {
			String normalized = normalizeKey(value);
			for (EventType type : values()) {
				if (type.id.equals(normalized)) {
					return type;
				}
			}
			return null;
		}
	}

	public static final class TaskContext {
		private final String schedulerId;
		private final long requestId;
		private final long nowTick;
		private final SchedulerBinding binding;
		private final TickDomain domain;

		private TaskContext(String schedulerId, long requestId, long nowTick, SchedulerBinding binding, TickDomain domain) {
			this.schedulerId = schedulerId;
			this.requestId = requestId;
			this.nowTick = nowTick;
			this.binding = binding;
			this.domain = domain;
		}

		public String getSchedulerId() {
			return schedulerId;
		}

		public long getRequestId() {
			return requestId;
		}

		public long getNowTick() {
			return nowTick;
		}

		public SchedulerBinding getBinding() {
			return binding;
		}

		public TickDomain getDomain() {
			return domain;
		}
	}

	public static final class SchedulerBinding {
		private final SchedulerType type;
		private final String key;
		private final String levelId;
		private final int chunkX;
		private final int chunkZ;
		private final EventType eventType;
		private final String eventId;

		private SchedulerBinding(
			SchedulerType type,
			String key,
			String levelId,
			int chunkX,
			int chunkZ,
			EventType eventType,
			String eventId
		) {
			this.type = Objects.requireNonNull(type, "Scheduler type must not be null.");
			this.key = normalizeKey(key);
			this.levelId = normalizeLevelId(levelId);
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.eventType = eventType;
			this.eventId = eventId == null || eventId.isBlank() ? null : eventId.trim();
			validate();
		}

		public static SchedulerBinding global(String key) {
			return new SchedulerBinding(SchedulerType.GLOBAL, key, null, 0, 0, null, null);
		}

		public static SchedulerBinding chunk(String key, String levelId, int chunkX, int chunkZ) {
			return new SchedulerBinding(SchedulerType.CHUNK, key, levelId, chunkX, chunkZ, null, null);
		}

		public static SchedulerBinding player(String key, UUID playerId) {
			return entity(key, playerId);
		}

		public static SchedulerBinding entity(String key, UUID entityId) {
			if (entityId == null) {
				throw new IllegalArgumentException("Entity scheduler must include an entity id.");
			}
			return new SchedulerBinding(SchedulerType.EVENT, key, null, 0, 0, EventType.ENTITY, entityId.toString());
		}

		public static SchedulerBinding block(String key, String levelId, long blockPosLong) {
			return new SchedulerBinding(SchedulerType.EVENT, key, levelId, 0, 0, EventType.BLOCK, Long.toString(blockPosLong));
		}

		public static SchedulerBinding blockEntity(String key, String levelId, long blockPosLong) {
			return new SchedulerBinding(SchedulerType.EVENT, key, levelId, 0, 0, EventType.BLOCK_ENTITY, Long.toString(blockPosLong));
		}

		private void validate() {
			if (key.isEmpty()) {
				throw new IllegalArgumentException("Scheduler binding key must not be blank.");
			}
			if (type == SchedulerType.CHUNK && levelId == null) {
				throw new IllegalArgumentException("Chunk scheduler must include a level id.");
			}
			if (type == SchedulerType.EVENT) {
				if (eventType == null || eventId == null || eventId.isBlank()) {
					throw new IllegalArgumentException("Event scheduler must include an event type and id.");
				}
				if ((eventType == EventType.BLOCK || eventType == EventType.BLOCK_ENTITY) && levelId == null) {
					throw new IllegalArgumentException("Block-based event scheduler must include a level id.");
				}
			}
		}

		private SchedulerBinding normalized() {
			return new SchedulerBinding(type, key, levelId, chunkX, chunkZ, eventType, eventId);
		}

		private boolean sameTarget(SchedulerBinding other) {
			return other != null
				&& type == other.type
				&& key.equals(other.key)
				&& Objects.equals(levelId, other.levelId)
				&& chunkX == other.chunkX
				&& chunkZ == other.chunkZ
				&& eventType == other.eventType
				&& Objects.equals(eventId, other.eventId);
		}

		public SchedulerType getType() {
			return type;
		}

		public String getKey() {
			return key;
		}

		public String getLevelId() {
			return levelId;
		}

		public int getChunkX() {
			return chunkX;
		}

		public int getChunkZ() {
			return chunkZ;
		}

		public EventType getEventType() {
			return eventType;
		}

		public String getEventId() {
			return eventId;
		}

		public UUID getEntityUuid() {
			return eventType == EventType.ENTITY ? parseUuid(eventId) : null;
		}

		public Long getBlockPosLong() {
			return eventType == EventType.BLOCK || eventType == EventType.BLOCK_ENTITY ? parseLong(eventId) : null;
		}

		private JsonObject toJson() {
			JsonObject root = new JsonObject();
			root.addProperty("type", type.id);
			root.addProperty("key", key);
			if (levelId == null) {
				root.add("level-id", JsonNull.INSTANCE);
			} else {
				root.addProperty("level-id", levelId);
			}
			if (type == SchedulerType.CHUNK) {
				root.addProperty("chunk-x", chunkX);
				root.addProperty("chunk-z", chunkZ);
			}
			if (type == SchedulerType.EVENT) {
				root.addProperty("event-type", eventType.id);
				root.addProperty("event-id", eventId);
			}
			return root;
		}

		private static SchedulerBinding fromJson(JsonObject source) {
			if (source == null) {
				return null;
			}

			SchedulerType type = SchedulerType.fromId(getString(source, "type", ""));
			String key = getString(source, "key", "");
			String levelId = getNullableString(source, "level-id");
			try {
				if (type == SchedulerType.GLOBAL) {
					return global(key);
				}
				if (type == SchedulerType.CHUNK) {
					return chunk(key, levelId, getInt(source, "chunk-x", 0), getInt(source, "chunk-z", 0));
				}
				if (type == SchedulerType.EVENT) {
					EventType eventType = EventType.fromId(getString(source, "event-type", ""));
					String eventId = getString(source, "event-id", "");
					if (eventType == EventType.ENTITY) {
						UUID entityId = parseUuid(eventId);
						return entityId == null ? null : entity(key, entityId);
					}
					if (eventType == EventType.BLOCK) {
						Long blockPos = parseLong(eventId);
						return blockPos == null ? null : block(key, levelId, blockPos);
					}
					if (eventType == EventType.BLOCK_ENTITY) {
						Long blockPos = parseLong(eventId);
						return blockPos == null ? null : blockEntity(key, levelId, blockPos);
					}
				}
			} catch (IllegalArgumentException exception) {
				return null;
			}
			return null;
		}
	}

	private static final class SchedulerEntry {
		private final String schedulerId;
		private final SchedulerBinding binding;
		private final PriorityQueue<ScheduledTask> tasks = new PriorityQueue<>(TASK_COMPARATOR);
		private int expirationDays;
		private long lastTouchedDay;
		private long lastRunnableGameplayTick;
		private long nextRequestId = 1L;

		private SchedulerEntry(String schedulerId, SchedulerBinding binding, int expirationDays, long lastTouchedDay) {
			this.schedulerId = schedulerId;
			this.binding = binding;
			this.expirationDays = normalizeExpirationDays(expirationDays);
			this.lastTouchedDay = Math.max(0L, lastTouchedDay);
			this.lastRunnableGameplayTick = Math.max(0L, MadokuTicks.getGameplayTicks());
		}

		private void setExpirationDays(int expirationDays) {
			int normalized = normalizeExpirationDays(expirationDays);
			if (this.expirationDays != normalized) {
				this.expirationDays = normalized;
				markDirty();
			}
		}

		private void touch(long currentDay) {
			long normalizedDay = Math.max(0L, currentDay);
			if (lastTouchedDay != normalizedDay) {
				lastTouchedDay = normalizedDay;
				markDirty();
			}
		}

		private void markRunnable(long gameplayTick) {
			lastRunnableGameplayTick = Math.max(0L, gameplayTick);
		}

		private boolean isExpiredForStaleness(long currentDay) {
			return expirationDays > 0
				&& currentDay >= lastTouchedDay
				&& currentDay - lastTouchedDay >= expirationDays;
		}

		private boolean isExpiredForInactivity(long nowTick) {
			return nowTick >= lastRunnableGameplayTick
				&& nowTick - lastRunnableGameplayTick >= INACTIVE_EXPIRATION_TICKS;
		}

		private JsonObject toJson() {
			JsonObject root = new JsonObject();
			root.addProperty("scheduler-id", schedulerId);
			JsonObject general = new JsonObject();
			general.addProperty(FIELD_EXPIRATION, expirationDays);
			general.addProperty(FIELD_LAST_TOUCHED_DAY, Math.max(0L, lastTouchedDay));
			root.add(GROUP_GENERAL, general);
			root.add("binding", binding.toJson());
			root.addProperty("next-request-id", Math.max(1L, nextRequestId));
			JsonArray tasksArray = new JsonArray();
			List<ScheduledTask> snapshot = new ArrayList<>(tasks);
			snapshot.sort(TASK_COMPARATOR);
			for (ScheduledTask task : snapshot) {
				tasksArray.add(task.toJson());
			}
			root.add("tasks", tasksArray);
			return root;
		}

		private static SchedulerEntry fromJson(JsonElement element) {
			if (element == null || !element.isJsonObject()) {
				return null;
			}
			JsonObject source = element.getAsJsonObject();
			String schedulerId = getString(source, "scheduler-id", "");
			SchedulerBinding binding = SchedulerBinding.fromJson(getObject(source, "binding"));
			JsonObject general = getObject(source, GROUP_GENERAL);
			if (schedulerId.isBlank() || binding == null) {
				return null;
			}

			int expirationDays = normalizeExpirationDays(getInt(general, FIELD_EXPIRATION, DEFAULT_EXPIRATION_DAYS));
			long lastTouchedDay = Math.max(0L, getLong(general, FIELD_LAST_TOUCHED_DAY, resolveCurrentSchedulerDay(null)));
			SchedulerEntry entry = new SchedulerEntry(schedulerId, binding, expirationDays, lastTouchedDay);
			entry.nextRequestId = Math.max(1L, getLong(source, "next-request-id", 1L));
			JsonArray tasksArray = getArray(source, "tasks");
			if (tasksArray != null) {
				for (JsonElement taskElement : tasksArray) {
					ScheduledTask task = ScheduledTask.fromJson(taskElement);
					if (task == null) {
						continue;
					}
					entry.tasks.add(task);
					entry.nextRequestId = Math.max(entry.nextRequestId, task.requestId + 1L);
				}
			}
			return entry;
		}
	}

	private static final class ScheduledTask {
		private final long requestId;
		private final long enqueuedTick;
		private final long dueTick;
		private final TickDomain domain;
		private final String taskType;
		private final JsonObject payload;

		private ScheduledTask(
			long requestId,
			long enqueuedTick,
			long dueTick,
			TickDomain domain,
			String taskType,
			JsonObject payload
		) {
			this.requestId = requestId;
			this.enqueuedTick = enqueuedTick;
			this.dueTick = dueTick;
			this.domain = domain;
			this.taskType = taskType;
			this.payload = payload == null ? new JsonObject() : payload.deepCopy();
		}

		private JsonObject toJson() {
			JsonObject root = new JsonObject();
			root.addProperty("request-id", requestId);
			root.addProperty("enqueued-tick", enqueuedTick);
			root.addProperty("due-tick", dueTick);
			root.addProperty("domain", domain.id());
			root.addProperty("task-type", taskType);
			root.add("payload", payload.deepCopy());
			return root;
		}

		private static ScheduledTask fromJson(JsonElement element) {
			if (element == null || !element.isJsonObject()) {
				return null;
			}
			JsonObject source = element.getAsJsonObject();
			TickDomain domain = TickDomain.fromId(getString(source, "domain", ""));
			String taskType = normalizeKey(getString(source, "task-type", ""));
			if (domain == null || taskType.isBlank()) {
				return null;
			}
			JsonElement payloadElement = source.get("payload");
			JsonObject payload = payloadElement != null && payloadElement.isJsonObject()
				? payloadElement.getAsJsonObject().deepCopy()
				: new JsonObject();
			return new ScheduledTask(
				Math.max(1L, getLong(source, "request-id", 1L)),
				Math.max(0L, getLong(source, "enqueued-tick", 0L)),
				Math.max(0L, getLong(source, "due-tick", 0L)),
				domain,
				taskType,
				payload
			);
		}
	}
}


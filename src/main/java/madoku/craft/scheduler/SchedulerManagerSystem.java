package madoku.craft.scheduler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import madoku.craft.chunk.ChunkManagerSystem;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.time.MadokuTime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

public final class SchedulerManagerSystem {
	private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerManagerSystem.class);
	private static final String DATA_FOLDER_NAME = "madoku-craft-schedulers";
	private static final String LEGACY_DATA_FOLDER_NAME = "madoku-craft-scheduler";
	private static final String META_FILE_NAME = "scheduler-manager";
	private static final String FIELD_AUTO_SAVE = "autoSave";
	private static final String FIELD_RESET_TIME = "resetTime";
	private static final long DEFAULT_AUTO_SAVE_MINUTES = 5L;
	private static final long VANILLA_DAY_TICKS = 24000L;
	private static final Comparator<ScheduledTask> TASK_COMPARATOR =
		Comparator.comparingLong((ScheduledTask task) -> task.dueTick)
			.thenComparingLong(task -> task.requestId);

	private static final Map<String, SchedulerEntry> SCHEDULERS = new LinkedHashMap<>();
	private static final Map<String, String> SCHEDULER_IDS_BY_OWNER = new HashMap<>();
	private static final Map<String, TaskHandler> TASK_HANDLERS = new HashMap<>();
	private static final Set<String> DIRTY_SCHEDULER_IDS = new HashSet<>();
	private static final Map<String, SchedulerTier> REMOVED_SCHEDULERS = new LinkedHashMap<>();

	private static long lastAutosaveBucket = Long.MIN_VALUE;
	private static boolean dirty;
	private static volatile MinecraftServer activeServer;
	private static volatile JsonObject metadataGeneral = createMetadataGeneral();

	private SchedulerManagerSystem() {
	}

	public static void reset() {
		SCHEDULERS.clear();
		SCHEDULER_IDS_BY_OWNER.clear();
		DIRTY_SCHEDULER_IDS.clear();
		REMOVED_SCHEDULERS.clear();
		lastAutosaveBucket = Long.MIN_VALUE;
		dirty = false;
		activeServer = null;
		metadataGeneral = createMetadataGeneral();
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		reset();
		activeServer = server;
		ManagedDocument metadata = loadManagedDocument(resolveMetadataFile(server));
		metadataGeneral = normalizeMetadataGeneral(metadata.general);
		JsonObject metadataMain = normalizeMetadataMain(metadata.main);
		writeManagedDocument(resolveMetadataFile(server), metadataGeneral, metadataMain);

		MadokuTicks.setGameplayTicks(getLong(metadataMain, "gameplay_ticks", 0L));
		long currentDay = resolveCurrentDay(server);
		GlobalSchedulerSystem.load(server, getArray(metadataMain, SchedulerTier.GLOBAL.metadataField()), currentDay);
		WorldSchedulerSystem.load(server, getArray(metadataMain, SchedulerTier.WORLD.metadataField()), currentDay);
		SchedulerSystem.load(server, getArray(metadataMain, SchedulerTier.LOCAL.metadataField()), currentDay);

		DIRTY_SCHEDULER_IDS.clear();
		REMOVED_SCHEDULERS.clear();
		dirty = false;
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), getAutoSaveIntervalTicks());
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		activeServer = server;
		flushPersistedData(server, true);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		activeServer = server;
		long currentBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), getAutoSaveIntervalTicks());
		if (currentBucket == lastAutosaveBucket) {
			return;
		}
		if (!dirty && DIRTY_SCHEDULER_IDS.isEmpty() && REMOVED_SCHEDULERS.isEmpty()) {
			lastAutosaveBucket = currentBucket;
			return;
		}

		flushPersistedData(server, false);
	}

	public static void onClockTick(MinecraftServer server) {
		if (server == null || !MadokuTime.isEnabled()) {
			return;
		}

		activeServer = server;
		long nowTick = MadokuTicks.getGameplayTicks();
		long currentDay = resolveCurrentDay(server);
		GlobalSchedulerSystem.processDue(server, nowTick, currentDay);
	}

	public static void onServerTick(MinecraftServer server) {
		if (server == null || MadokuTime.isEnabled()) {
			return;
		}

		activeServer = server;
		long nowTick = MadokuTicks.getGameplayTicks();
		long currentDay = resolveCurrentDay(server);
		GlobalSchedulerSystem.processDue(server, nowTick, currentDay);
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
		markDirty(entry);
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

	public static String createScheduler(SchedulerOwner owner) {
		SchedulerOwner normalizedOwner = normalizeOwner(owner);
		long currentDay = resolveCurrentDay(activeServer);
		SchedulerTier tier = routeTier(normalizedOwner);
		String schedulerId = UUID.randomUUID().toString();
		SchedulerEntry entry = new SchedulerEntry(
			schedulerId,
			normalizedOwner,
			tier,
			currentDay,
			0L
		);
		SCHEDULERS.put(schedulerId, entry);
		indexOwner(entry);
		markDirty(entry);
		return schedulerId;
	}

	public static String createOrGetScheduler(SchedulerOwner owner) {
		SchedulerOwner normalizedOwner = normalizeOwner(owner);
		String ownerKey = ownerKey(normalizedOwner);
		String existingId = SCHEDULER_IDS_BY_OWNER.get(ownerKey);
		if (existingId != null) {
			SchedulerEntry existing = SCHEDULERS.get(existingId);
			if (existing != null && sameOwner(existing.owner, normalizedOwner)) {
				return existing.schedulerId;
			}
			SCHEDULER_IDS_BY_OWNER.remove(ownerKey, existingId);
		}
		return createScheduler(normalizedOwner);
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

		long currentDay = resolveCurrentDay(activeServer);
		if (isExpired(entry, currentDay)) {
			removeScheduler(entry);
			return EnqueueStatus.SCHEDULER_EXPIRED;
		}

		long dueTick = Math.max(0L, MadokuTicks.getGameplayTicks() + Math.max(0L, delayTicks));
		entry.tasks.add(new ScheduledTask(
			entry.nextRequestId++,
			MadokuTicks.getGameplayTicks(),
			dueTick,
			Objects.requireNonNullElse(domain, TickDomain.GAMEPLAY),
			normalizedTaskType,
			payload == null ? new JsonObject() : payload.deepCopy()
		));
		markDirty(entry);
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

	static void loadTier(MinecraftServer server, JsonArray ids, SchedulerTier tier, long currentDay) {
		if (ids == null) {
			return;
		}

		for (String schedulerId : loadIds(ids)) {
			Path file = resolveSchedulerFile(server, tier, schedulerId);
			if (!Files.isRegularFile(file)) {
				continue;
			}

			ManagedDocument document = loadManagedDocument(file);
			long resetTime = Math.max(0L, getLong(document.general, FIELD_RESET_TIME, 0L));
			SchedulerEntry entry = SchedulerEntry.fromJson(document.main, tier, resetTime);
			if (entry == null) {
				continue;
			}
			if (isExpired(entry, currentDay)) {
				REMOVED_SCHEDULERS.put(entry.schedulerId, entry.tier);
				dirty = true;
				continue;
			}
			SCHEDULERS.put(entry.schedulerId, entry);
			indexOwner(entry);
		}
	}

	static void processTier(MinecraftServer server, SchedulerTier tier, String levelId, long nowTick, long currentDay) {
		List<SchedulerEntry> snapshot = new ArrayList<>(SCHEDULERS.values());
		for (SchedulerEntry entry : snapshot) {
			if (entry.tier != tier) {
				continue;
			}
			if (levelId != null && !levelId.equals(entry.owner.levelId)) {
				continue;
			}
			if (isExpired(entry, currentDay)) {
				removeScheduler(entry);
				continue;
			}
			if (entry.tasks.isEmpty()) {
				removeScheduler(entry);
				continue;
			}
			if (!isOwnerValid(server, entry.owner)) {
				continue;
			}
			processDueTasks(server, entry, nowTick);
			if (entry.tasks.isEmpty()) {
				removeScheduler(entry);
			}
		}
	}

	private static void processDueTasks(MinecraftServer server, SchedulerEntry entry, long nowTick) {
		boolean changed = false;
		while (true) {
			ScheduledTask next = entry.tasks.peek();
			if (next == null || next.dueTick > nowTick) {
				break;
			}

			entry.tasks.poll();
			changed = true;
			TaskHandler handler = TASK_HANDLERS.get(next.taskType);
			if (handler == null) {
				LOGGER.warn(
					"Scheduler task type '{}' has no handler: scheduler={} owner={}",
					next.taskType,
					entry.schedulerId,
					describeOwner(entry.owner)
				);
				continue;
			}

			try {
				handler.execute(
					server,
					new TaskContext(entry.schedulerId, next.requestId, nowTick, entry.owner, next.domain),
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
			markDirty(entry);
		}
	}

	private static void flushPersistedData(MinecraftServer server, boolean writeAllSchedulers) {
		writeManagedDocument(resolveMetadataFile(server), metadataGeneral, createMetadataMain());

		if (writeAllSchedulers) {
			for (SchedulerEntry entry : SCHEDULERS.values()) {
				saveScheduler(entry, server);
			}
		} else {
			for (String schedulerId : new ArrayList<>(DIRTY_SCHEDULER_IDS)) {
				SchedulerEntry entry = SCHEDULERS.get(schedulerId);
				if (entry != null) {
					saveScheduler(entry, server);
				}
			}
		}

		for (Map.Entry<String, SchedulerTier> removed : new ArrayList<>(REMOVED_SCHEDULERS.entrySet())) {
			deleteSchedulerFile(server, removed.getValue(), removed.getKey());
		}

		DIRTY_SCHEDULER_IDS.clear();
		REMOVED_SCHEDULERS.clear();
		dirty = false;
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), getAutoSaveIntervalTicks());
	}

	private static void saveScheduler(SchedulerEntry entry, MinecraftServer server) {
		if (entry == null || server == null) {
			return;
		}
		writeManagedDocument(
			resolveSchedulerFile(server, entry.tier, entry.schedulerId),
			createSchedulerGeneral(entry.resetTime),
			entry.toJson()
		);
	}

	private static void deleteSchedulerFile(MinecraftServer server, SchedulerTier tier, String schedulerId) {
		Path file = resolveSchedulerFile(server, tier, schedulerId);
		try {
			Files.deleteIfExists(file);
		} catch (IOException exception) {
			LOGGER.error("Failed to delete scheduler file {}", file, exception);
		}
	}

	private static ManagedDocument loadManagedDocument(Path file) {
		try {
			JsonStaticSystem.ManagedStaticDocument document = JsonStaticSystem.readManagedDocument(file);
			return new ManagedDocument(document.general(), document.main());
		} catch (IOException exception) {
			LOGGER.error("Failed to read scheduler document {}", file, exception);
			return new ManagedDocument(new JsonObject(), new JsonObject());
		}
	}

	private static void writeManagedDocument(Path file, JsonObject general, JsonObject main) {
		try {
			JsonStaticSystem.writeManagedDocument(file, main, general);
		} catch (IOException exception) {
			LOGGER.error("Failed to write scheduler document {}", file, exception);
		}
	}

	private static JsonObject normalizeMetadataGeneral(JsonObject source) {
		JsonObject general = source == null ? new JsonObject() : source.deepCopy();
		long autoSave = Math.max(1L, getLong(general, FIELD_AUTO_SAVE, DEFAULT_AUTO_SAVE_MINUTES));
		general.addProperty(FIELD_AUTO_SAVE, autoSave);
		return general;
	}

	private static JsonObject normalizeMetadataMain(JsonObject source) {
		JsonObject main = new JsonObject();
		main.addProperty("gameplay_ticks", Math.max(0L, getLong(source, "gameplay_ticks", 0L)));
		main.add(SchedulerTier.GLOBAL.metadataField(), copyArray(source, SchedulerTier.GLOBAL.metadataField()));
		main.add(SchedulerTier.WORLD.metadataField(), copyArray(source, SchedulerTier.WORLD.metadataField()));
		main.add(SchedulerTier.LOCAL.metadataField(), copyArray(source, SchedulerTier.LOCAL.metadataField()));
		return main;
	}

	private static JsonObject createMetadataMain() {
		JsonObject main = new JsonObject();
		main.addProperty("gameplay_ticks", Math.max(0L, MadokuTicks.getGameplayTicks()));
		main.add(SchedulerTier.GLOBAL.metadataField(), collectIds(SchedulerTier.GLOBAL));
		main.add(SchedulerTier.WORLD.metadataField(), collectIds(SchedulerTier.WORLD));
		main.add(SchedulerTier.LOCAL.metadataField(), collectIds(SchedulerTier.LOCAL));
		return main;
	}

	private static JsonObject createMetadataGeneral() {
		JsonObject general = new JsonObject();
		general.addProperty(FIELD_AUTO_SAVE, DEFAULT_AUTO_SAVE_MINUTES);
		return general;
	}

	private static JsonObject createSchedulerGeneral(long resetTime) {
		JsonObject general = new JsonObject();
		general.addProperty(FIELD_RESET_TIME, Math.max(0L, resetTime));
		return general;
	}

	private static JsonArray collectIds(SchedulerTier tier) {
		JsonArray ids = new JsonArray();
		for (SchedulerEntry entry : SCHEDULERS.values()) {
			if (entry.tier == tier) {
				ids.add(entry.schedulerId);
			}
		}
		return ids;
	}

	private static JsonArray copyArray(JsonObject object, String key) {
		JsonElement element = object == null ? null : object.get(key);
		return element != null && element.isJsonArray() ? element.getAsJsonArray().deepCopy() : new JsonArray();
	}

	private static List<String> loadIds(JsonArray ids) {
		List<String> values = new ArrayList<>();
		for (JsonElement element : ids) {
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				continue;
			}
			String schedulerId = element.getAsString();
			if (schedulerId == null || schedulerId.isBlank()) {
				continue;
			}
			values.add(schedulerId.trim());
		}
		return values;
	}

	private static void markDirty(SchedulerEntry entry) {
		if (entry == null) {
			return;
		}
		DIRTY_SCHEDULER_IDS.add(entry.schedulerId);
		REMOVED_SCHEDULERS.remove(entry.schedulerId);
		dirty = true;
	}

	private static void removeScheduler(SchedulerEntry entry) {
		if (entry == null) {
			return;
		}
		SCHEDULERS.remove(entry.schedulerId);
		String ownerKey = ownerKey(entry.owner);
		if (!ownerKey.isEmpty()) {
			SCHEDULER_IDS_BY_OWNER.remove(ownerKey, entry.schedulerId);
		}
		DIRTY_SCHEDULER_IDS.remove(entry.schedulerId);
		REMOVED_SCHEDULERS.put(entry.schedulerId, entry.tier);
		dirty = true;
	}

	private static void indexOwner(SchedulerEntry entry) {
		String ownerKey = ownerKey(entry.owner);
		if (!ownerKey.isEmpty()) {
			SCHEDULER_IDS_BY_OWNER.put(ownerKey, entry.schedulerId);
		}
	}

	private static boolean isExpired(SchedulerEntry entry, long currentDay) {
		if (entry == null || entry.resetTime <= 0L) {
			return false;
		}
		return currentDay - entry.createdDay >= entry.resetTime;
	}

	private static long getAutoSaveIntervalTicks() {
		long minutes = Math.max(1L, getLong(metadataGeneral, FIELD_AUTO_SAVE, DEFAULT_AUTO_SAVE_MINUTES));
		try {
			return Math.multiplyExact(minutes, MadokuTicks.SECONDS_PER_MINUTE * MadokuTicks.TICKS_PER_SECOND);
		} catch (ArithmeticException exception) {
			return DEFAULT_AUTO_SAVE_MINUTES * MadokuTicks.SECONDS_PER_MINUTE * MadokuTicks.TICKS_PER_SECOND;
		}
	}

	private static long resolveCurrentDay(MinecraftServer server) {
		ServerLevel overworld = server == null ? null : server.overworld();
		if (overworld == null) {
			return 0L;
		}
		if (MadokuTime.isEnabled()) {
			return Math.max(0L, MadokuTime.getDay(MadokuTime.getCurrentAbsoluteDayTime(overworld)));
		}
		return Math.max(0L, Math.floorDiv(overworld.getOverworldClockTime(), VANILLA_DAY_TICKS));
	}

	private static SchedulerTier routeTier(SchedulerOwner owner) {
		if (owner == null) {
			throw new IllegalArgumentException("Scheduler owner must not be null.");
		}
		if ("blockentity".equals(owner.kind) || "chunk".equals(owner.kind)) {
			return SchedulerTier.LOCAL;
		}
		if (owner.levelId != null && !owner.levelId.isBlank()) {
			return SchedulerTier.WORLD;
		}
		return SchedulerTier.GLOBAL;
	}

	private static SchedulerOwner normalizeOwner(SchedulerOwner owner) {
		if (owner == null) {
			throw new IllegalArgumentException("Scheduler owner must not be null.");
		}
		return owner.normalized();
	}

	private static boolean sameOwner(SchedulerOwner left, SchedulerOwner right) {
		return left != null
			&& right != null
			&& left.kind.equals(right.kind)
			&& left.ownerId.equals(right.ownerId)
			&& Objects.equals(left.levelId, right.levelId);
	}

	private static boolean isOwnerValid(MinecraftServer server, SchedulerOwner owner) {
		if (server == null || owner == null) {
			return false;
		}

		return switch (owner.kind) {
			case "global" -> true;
			case "player" -> playerExists(server, owner);
			case "blockentity" -> blockEntityExists(server, owner);
			case "chunk" -> chunkExists(server, owner);
			default -> owner.levelId != null && !owner.levelId.isBlank() && resolveLevel(server, owner.levelId) != null;
		};
	}

	private static boolean playerExists(MinecraftServer server, SchedulerOwner owner) {
		UUID playerId = parseUuid(owner.ownerId);
		return playerId != null && server.getPlayerList().getPlayer(playerId) != null;
	}

	private static boolean blockEntityExists(MinecraftServer server, SchedulerOwner owner) {
		ServerLevel level = resolveLevel(server, owner.levelId);
		if (level == null) {
			return false;
		}
		Long packedPos = parseLong(owner.ownerId);
		if (packedPos == null) {
			return false;
		}
		BlockPos blockPos = BlockPos.of(packedPos);
		int chunkX = blockPos.getX() >> 4;
		int chunkZ = blockPos.getZ() >> 4;
		if (!ChunkManagerSystem.isChunkLoaded(level, chunkX, chunkZ)) {
			return false;
		}
		return level.getBlockEntity(blockPos) != null;
	}

	private static boolean chunkExists(MinecraftServer server, SchedulerOwner owner) {
		ServerLevel level = resolveLevel(server, owner.levelId);
		if (level == null) {
			return false;
		}
		int separator = owner.ownerId.indexOf(',');
		if (separator <= 0 || separator >= owner.ownerId.length() - 1) {
			return false;
		}
		Integer chunkX = parseInt(owner.ownerId.substring(0, separator).trim());
		Integer chunkZ = parseInt(owner.ownerId.substring(separator + 1).trim());
		return chunkX != null && chunkZ != null && ChunkManagerSystem.isChunkLoaded(level, chunkX, chunkZ);
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

	private static String ownerKey(SchedulerOwner owner) {
		if (owner == null) {
			return "";
		}
		if (owner.kind.isBlank() || owner.ownerId.isBlank()) {
			return "";
		}
		String levelId = owner.levelId == null ? "" : owner.levelId;
		return owner.kind + '\u0000' + owner.ownerId + '\u0000' + levelId;
	}

	private static Path resolveMetadataFile(MinecraftServer server) {
		return resolveSchedulerRoot(server).resolve(withJsonExtension(META_FILE_NAME));
	}

	private static Path resolveSchedulerFile(MinecraftServer server, SchedulerTier tier, String schedulerId) {
		return resolveSchedulerRoot(server)
			.resolve(tier.directoryName())
			.resolve(withJsonExtension(schedulerId));
	}

	private static Path resolveSchedulerRoot(MinecraftServer server) {
		Path worldRoot = JsonManagerSystem.getWorldRootDirectory(server);
		Path root = worldRoot.resolve(DATA_FOLDER_NAME);
		Path legacyRoot = worldRoot.resolve(LEGACY_DATA_FOLDER_NAME);
		if (!Files.exists(root) && Files.isDirectory(legacyRoot)) {
			try {
				Files.move(legacyRoot, root);
			} catch (IOException exception) {
				LOGGER.warn("Failed to migrate legacy scheduler root {} to {}", legacyRoot, root, exception);
				root = legacyRoot;
			}
		}
		try {
			Files.createDirectories(root);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to create scheduler root directory " + root, exception);
		}
		return root;
	}

	private static String withJsonExtension(String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Scheduler file name must not be blank.");
		}
		return normalized.endsWith(".json") ? normalized : normalized + ".json";
	}

	private static JsonArray getArray(JsonObject object, String key) {
		JsonElement element = object == null ? null : object.get(key);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
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

	private static Integer parseInt(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private static Long parseLong(String value) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException exception) {
			return null;
		}
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

	private static String normalizeKey(String value) {
		return value == null ? "" : value.trim().toLowerCase();
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

	private static String describeOwner(SchedulerOwner owner) {
		if (owner == null) {
			return "unknown";
		}
		return owner.levelId == null
			? owner.kind + ":" + owner.ownerId
			: owner.kind + ":" + owner.ownerId + "@" + owner.levelId;
	}

	@FunctionalInterface
	public interface TaskHandler {
		void execute(MinecraftServer server, TaskContext context, JsonObject payload);
	}

	public enum EnqueueStatus {
		ACCEPTED,
		SCHEDULER_NOT_FOUND,
		SCHEDULER_CLOSED,
		SCHEDULER_EXPIRED,
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

	public static final class TaskContext {
		private final String schedulerId;
		private final long requestId;
		private final long nowTick;
		private final SchedulerOwner owner;
		private final TickDomain domain;

		private TaskContext(String schedulerId, long requestId, long nowTick, SchedulerOwner owner, TickDomain domain) {
			this.schedulerId = schedulerId;
			this.requestId = requestId;
			this.nowTick = nowTick;
			this.owner = owner;
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

		public SchedulerOwner getOwner() {
			return owner;
		}

		public TickDomain getDomain() {
			return domain;
		}
	}

	public static final class SchedulerOwner {
		private final String kind;
		private final String ownerId;
		private final String levelId;

		private SchedulerOwner(String kind, String ownerId, String levelId) {
			this.kind = normalizeKey(kind);
			this.ownerId = ownerId == null ? "" : ownerId.trim();
			this.levelId = normalizeLevelId(levelId);
			if (this.kind.isEmpty() || this.ownerId.isEmpty()) {
				throw new IllegalArgumentException("Scheduler owner must include kind and owner id.");
			}
		}

		public static SchedulerOwner of(String kind, String ownerId, String levelId) {
			return new SchedulerOwner(kind, ownerId, levelId);
		}

		public static SchedulerOwner global(String ownerId) {
			return new SchedulerOwner("global", ownerId, null);
		}

		SchedulerOwner normalized() {
			return new SchedulerOwner(kind, ownerId, levelId);
		}

		public String getKind() {
			return kind;
		}

		public String getOwnerId() {
			return ownerId;
		}

		public String getLevelId() {
			return levelId;
		}

		private JsonObject toJson() {
			JsonObject root = new JsonObject();
			root.addProperty("kind", kind);
			root.addProperty("owner_id", ownerId);
			if (levelId == null) {
				root.add("level_id", JsonNull.INSTANCE);
			} else {
				root.addProperty("level_id", levelId);
			}
			return root;
		}

		private static SchedulerOwner fromJson(JsonObject source) {
			if (source == null) {
				return null;
			}
			try {
				return of(
					getString(source, "kind", ""),
					getString(source, "owner_id", ""),
					getNullableString(source, "level_id")
				);
			} catch (IllegalArgumentException exception) {
				return null;
			}
		}
	}

	enum SchedulerTier {
		GLOBAL("global_scheduler_ids", "global-schedulers"),
		WORLD("world_scheduler_ids", "world-schedulers"),
		LOCAL("scheduler_ids", "schedulers");

		private final String metadataField;
		private final String directoryName;

		SchedulerTier(String metadataField, String directoryName) {
			this.metadataField = metadataField;
			this.directoryName = directoryName;
		}

		String metadataField() {
			return metadataField;
		}

		String directoryName() {
			return directoryName;
		}
	}

	private static final class ManagedDocument {
		private final JsonObject general;
		private final JsonObject main;

		private ManagedDocument(JsonObject general, JsonObject main) {
			this.general = general == null ? new JsonObject() : general.deepCopy();
			this.main = main == null ? new JsonObject() : main.deepCopy();
		}
	}

	static final class SchedulerEntry {
		private final String schedulerId;
		private final SchedulerOwner owner;
		private final SchedulerTier tier;
		private final PriorityQueue<ScheduledTask> tasks = new PriorityQueue<>(TASK_COMPARATOR);
		private final long createdDay;
		private long nextRequestId = 1L;
		private long resetTime;

		private SchedulerEntry(
			String schedulerId,
			SchedulerOwner owner,
			SchedulerTier tier,
			long createdDay,
			long resetTime
		) {
			this.schedulerId = schedulerId;
			this.owner = owner;
			this.tier = tier;
			this.createdDay = Math.max(0L, createdDay);
			this.resetTime = Math.max(0L, resetTime);
		}

		private JsonObject toJson() {
			JsonObject root = new JsonObject();
			root.addProperty("scheduler_id", schedulerId);
			root.add("owner", owner.toJson());
			root.addProperty("created_day", createdDay);
			root.addProperty("next_request_id", Math.max(1L, nextRequestId));
			JsonArray requests = new JsonArray();
			List<ScheduledTask> snapshot = new ArrayList<>(tasks);
			snapshot.sort(TASK_COMPARATOR);
			for (ScheduledTask task : snapshot) {
				requests.add(task.toJson());
			}
			root.add("requests", requests);
			return root;
		}

		private static SchedulerEntry fromJson(JsonObject source, SchedulerTier tier, long resetTime) {
			if (source == null) {
				return null;
			}
			String schedulerId = getString(source, "scheduler_id", "");
			SchedulerOwner owner = SchedulerOwner.fromJson(source.getAsJsonObject("owner"));
			if (schedulerId.isBlank() || owner == null) {
				return null;
			}
			SchedulerEntry entry = new SchedulerEntry(
				schedulerId.trim(),
				owner,
				tier,
				Math.max(0L, getLong(source, "created_day", 0L)),
				resetTime
			);
			entry.nextRequestId = Math.max(1L, getLong(source, "next_request_id", 1L));
			JsonArray requests = getArray(source, "requests");
			if (requests != null) {
				for (JsonElement element : requests) {
					ScheduledTask task = ScheduledTask.fromJson(element);
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
			root.addProperty("request_id", requestId);
			root.addProperty("enqueued_tick", enqueuedTick);
			root.addProperty("due_tick", dueTick);
			root.addProperty("domain", domain.id());
			root.addProperty("task_type", taskType);
			root.add("payload", payload.deepCopy());
			return root;
		}

		private static ScheduledTask fromJson(JsonElement element) {
			if (element == null || !element.isJsonObject()) {
				return null;
			}
			JsonObject source = element.getAsJsonObject();
			TickDomain domain = TickDomain.fromId(getString(source, "domain", ""));
			String taskType = normalizeKey(getString(source, "task_type", ""));
			if (domain == null || taskType.isBlank()) {
				return null;
			}
			JsonElement payloadElement = source.get("payload");
			JsonObject payload = payloadElement != null && payloadElement.isJsonObject()
				? payloadElement.getAsJsonObject().deepCopy()
				: new JsonObject();
			return new ScheduledTask(
				Math.max(1L, getLong(source, "request_id", 1L)),
				Math.max(0L, getLong(source, "enqueued_tick", 0L)),
				Math.max(0L, getLong(source, "due_tick", 0L)),
				domain,
				taskType,
				payload
			);
		}
	}
}

package madoku.craft.java.core.scheduler;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.UUID;

public final class SchedulerAPIManager {
	private static final SchedulerProvider UNAVAILABLE_PROVIDER = new SchedulerProvider() { };
	private static volatile SchedulerProvider provider = UNAVAILABLE_PROVIDER;

	private SchedulerAPIManager() {
	}

	public static void registerProvider(SchedulerProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Scheduler provider must not be null.");
		provider = candidate;
	}

	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }

	public static void initialize() {
		provider.initialize();
	}

	public static void reset() {
		provider.reset();
	}

	public static void loadPersistedData(MinecraftServer server) {
		provider.loadPersistedData(server);
	}

	public static void savePersistedData(MinecraftServer server) {
		provider.savePersistedData(server);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		provider.autosavePersistedData(server);
	}

	public static void onClockTick(MinecraftServer server) {
		provider.onClockTick(server);
	}

	public static void onServerTick(MinecraftServer server) {
		provider.onServerTick(server);
	}

	public static long resolveAdaptiveDelayTicks(
		MinecraftServer server,
		String schedulerOwnerId,
		long minimumIntervalTicks,
		long maximumIntervalTicks
	) {
		return provider.resolveAdaptiveDelayTicks(server, schedulerOwnerId, minimumIntervalTicks, maximumIntervalTicks);
	}

	public static void clearAdaptiveDelayState(String schedulerOwnerId) {
		provider.clearAdaptiveDelayState(schedulerOwnerId);
	}

	public static void clearQueuedRequests(String schedulerId) {
		provider.clearQueuedRequests(schedulerId);
	}

	public static boolean hasQueuedTask(String schedulerId, String taskType) {
		return provider.hasQueuedTask(schedulerId, taskType);
	}

	public static String createOrGetScheduler(SchedulerBinding binding) {
		return createOrGetScheduler(binding, provider.defaultExpirationDays());
	}

	public static String createOrGetScheduler(SchedulerBinding binding, int expirationDays) {
		return provider.createOrGetScheduler(binding, expirationDays);
	}

	public static EnqueueStatus enqueue(
		String schedulerId,
		long delayTicks,
		String taskType,
		JsonObject payload,
		TickDomain domain
	) {
		return provider.enqueue(schedulerId, delayTicks, taskType, payload, domain);
	}

	public static void registerTaskHandler(String taskType, TaskHandler handler) {
		provider.registerTaskHandler(taskType, handler);
	}

	public static void unregisterTaskHandler(String taskType) {
		provider.unregisterTaskHandler(taskType);
	}

	public static String normalizeLevelIdentifier(String levelId) {
		return provider.normalizeLevelIdentifier(levelId);
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

		String id() {
			return id;
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

		String id() {
			return id;
		}
	}

	public static final class TaskContext {
		private final String schedulerId;
		private final long requestId;
		private final long nowTick;
		private final SchedulerBinding binding;
		private final TickDomain domain;

		TaskContext(String schedulerId, long requestId, long nowTick, SchedulerBinding binding, TickDomain domain) {
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

		SchedulerBinding normalized() {
			return new SchedulerBinding(type, key, levelId, chunkX, chunkZ, eventType, eventId);
		}

		boolean sameTarget(SchedulerBinding other) {
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

		JsonObject toJson() {
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

		static SchedulerBinding fromJson(JsonObject source) {
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

}




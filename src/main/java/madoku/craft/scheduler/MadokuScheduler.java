package madoku.craft.scheduler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.data.MadokuData;
import madoku.craft.debug.MadokuDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

public final class MadokuScheduler {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuScheduler.class);

	private static final String DATA_FOLDER_NAME = "madoku-craft-scheduler";
	private static final String META_FILE_NAME = "madoku-data";
	private static final String SCHEDULERS_FOLDER_NAME = "madoku-schedulers";
	private static final long MINECRAFT_DAY_TICKS = 24000L;
	private static final long IDLE_TIMEOUT_TICKS = 60L * 20L;
	private static final long WEEK_TIMEOUT_TICKS = 7L * MINECRAFT_DAY_TICKS;
	private static final long MONTH_TIMEOUT_TICKS = 30L * MINECRAFT_DAY_TICKS;
	private static final long AUTOSAVE_INTERVAL_TICKS = 60L * 20L;
	private static final long INACTIVE_SCAN_INTERVAL_TICKS = 10L;
	private static final int MAX_QUEUE_SIZE_PER_SCHEDULER = 2048;
	private static final int MAX_TASK_RETRY_ATTEMPTS = 3;
	private static final long RETRY_BACKOFF_BASE_TICKS = 20L;
	private static final long RETRY_BACKOFF_MAX_TICKS = 60L * 20L;
	private static final Comparator<ScheduledRequest> REQUEST_COMPARATOR =
		Comparator.comparingLong((ScheduledRequest request) -> request.dueTick)
			.thenComparingLong(request -> request.requestId);

	private static final Map<String, SchedulerState> SCHEDULERS = new LinkedHashMap<>();
	private static final Map<String, SchedulerState> PENDING_SCHEDULERS = new LinkedHashMap<>();
	private static final Map<String, String> SCHEDULER_IDS_BY_OWNER = new HashMap<>();
	private static final Map<String, TaskHandler> TASK_HANDLERS = new HashMap<>();
	private static final Map<String, OwnerExistenceResolver> OWNER_RESOLVERS = new HashMap<>();
	private static final Set<String> DIRTY_SCHEDULER_IDS = new HashSet<>();
	private static final Set<String> REMOVED_SCHEDULER_IDS = new HashSet<>();

	private static boolean dirty = false;
	private static long lastAutosaveBucket = Long.MIN_VALUE;
	private static boolean ticking = false;

	static {
		registerOwnerResolver("global", (server, owner) -> true);
		registerOwnerResolver("player", MadokuScheduler::playerExists);
		registerOwnerResolver("entity", MadokuScheduler::entityExists);
		registerOwnerResolver("mob", MadokuScheduler::entityExists);
		registerOwnerResolver("item", MadokuScheduler::entityExists);
		registerOwnerResolver("blockentity", MadokuScheduler::blockEntityExists);
		registerOwnerResolver("chunk", MadokuScheduler::chunkExists);
	}

	private MadokuScheduler() {
	}

	public static void reset() {
		SCHEDULERS.clear();
		PENDING_SCHEDULERS.clear();
		SCHEDULER_IDS_BY_OWNER.clear();
		DIRTY_SCHEDULER_IDS.clear();
		REMOVED_SCHEDULER_IDS.clear();
		ticking = false;
		dirty = false;
		lastAutosaveBucket = Long.MIN_VALUE;
	}

	public static void clearQueuedRequests(String schedulerId) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return;
		}

		SchedulerState scheduler = SCHEDULERS.get(schedulerId);
		if (scheduler == null) {
			scheduler = PENDING_SCHEDULERS.get(schedulerId);
		}
		if (scheduler == null) {
			return;
		}

		if (scheduler.gameplayRequests.isEmpty()) {
			return;
		}

		scheduler.gameplayRequests.clear();
		scheduler.lastActivityTick = MadokuTicks.getGameplayTicks();
		scheduler.nextScanTick = scheduler.lastActivityTick;
		scheduler.nextOwnerCheckTick = scheduler.lastActivityTick;
		markSchedulerDirty(scheduler.schedulerId);
	}

	public static String createScheduler(SchedulerOwner owner) {
		if (owner == null) {
			throw new IllegalArgumentException("Scheduler owner must not be null.");
		}
		if (!OWNER_RESOLVERS.containsKey(owner.kind)) {
			throw new IllegalArgumentException(
				"Unknown scheduler owner kind '" + owner.kind + "'. Register an owner resolver before creating a scheduler."
			);
		}

		long now = MadokuTicks.getGameplayTicks();
		String schedulerId = UUID.randomUUID().toString();
		SchedulerState scheduler = new SchedulerState(
			schedulerId,
			owner.normalized(),
			now,
			now + MINECRAFT_DAY_TICKS,
			now + WEEK_TIMEOUT_TICKS
		);
		if (ticking) {
			PENDING_SCHEDULERS.put(schedulerId, scheduler);
		} else {
			SCHEDULERS.put(schedulerId, scheduler);
		}
		indexSchedulerOwner(scheduler);
		markSchedulerDirty(schedulerId);
		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.SCHEDULER, "scheduler.created")) {
			MadokuDebug.event("scheduler.created", MadokuDebug.Domain.SCHEDULER)
				.side(MadokuDebug.Side.SERVER)
				.tick(now)
				.subject("scheduler:" + schedulerId)
				.field("owner", owner.kind + ":" + owner.ownerId)
				.field("accept_until_tick", scheduler.acceptUntilTick)
				.field("expire_at_tick", scheduler.expireAtTick)
				.log();
		}
		return schedulerId;
	}

	public static String createOrGetScheduler(SchedulerOwner owner) {
		if (owner == null) {
			throw new IllegalArgumentException("Scheduler owner must not be null.");
		}
		SchedulerOwner normalizedOwner = owner.normalized();
		long nowGameplayTick = MadokuTicks.getGameplayTicks();
		SchedulerState existing = findSchedulerByOwner(normalizedOwner, nowGameplayTick);
		if (existing != null) {
			return existing.schedulerId;
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
		SchedulerState scheduler = findScheduler(schedulerId);
		TickDomain resolvedDomain = Objects.requireNonNull(domain, "Tick domain must not be null.");
		long nowGameplayTick = MadokuTicks.getGameplayTicks();
		if (scheduler == null) {
			return EnqueueStatus.SCHEDULER_NOT_FOUND;
		}

		if (nowGameplayTick >= scheduler.expireAtTick) {
			scheduler.accepting = false;
			markSchedulerDirty(scheduler.schedulerId);
			return EnqueueStatus.SCHEDULER_EXPIRED;
		}
		if (!scheduler.accepting || nowGameplayTick > scheduler.acceptUntilTick) {
			scheduler.accepting = false;
			markSchedulerDirty(scheduler.schedulerId);
			return EnqueueStatus.SCHEDULER_CLOSED;
		}

		String normalizedTaskType = normalizeKey(taskType);
		if (normalizedTaskType.isEmpty()) {
			return EnqueueStatus.INVALID_TASK_TYPE;
		}

		int existingQueueSize = queueSize(scheduler);
		if (existingQueueSize >= MAX_QUEUE_SIZE_PER_SCHEDULER) {
			return EnqueueStatus.QUEUE_FULL;
		}

		long nowDomainTick = currentTickForDomain(resolvedDomain);
		long safeDelay = Math.max(0L, delayTicks);
		long dueTick = safeAdd(nowDomainTick, safeDelay);
		ScheduledRequest request = new ScheduledRequest(
			scheduler.nextRequestId++,
			nowDomainTick,
			dueTick,
			resolvedDomain,
			normalizedTaskType,
			payload == null ? new JsonObject() : payload.deepCopy(),
			0
		);
		queueForDomain(scheduler, resolvedDomain).add(request);
		scheduler.lastActivityTick = nowGameplayTick;
		scheduler.nextScanTick = nowGameplayTick;
		scheduler.nextOwnerCheckTick = nowGameplayTick;
		markSchedulerDirty(scheduler.schedulerId);
		return EnqueueStatus.ACCEPTED;
	}

	public static void tick(MinecraftServer server, boolean gameplaySignal) {
		if (server == null) {
			return;
		}
		if (!gameplaySignal) {
			return;
		}

		ticking = true;
		long nowGameplayTick = MadokuTicks.getGameplayTicks();
		try {
			Iterator<Map.Entry<String, SchedulerState>> iterator = SCHEDULERS.entrySet().iterator();
			while (iterator.hasNext()) {
				SchedulerState scheduler = iterator.next().getValue();

				long pausedExpireAtTick = safeAdd(scheduler.createdAtTick, MONTH_TIMEOUT_TICKS);
				long effectiveExpireAtTick = scheduler.paused ? pausedExpireAtTick : scheduler.expireAtTick;
					if (nowGameplayTick >= effectiveExpireAtTick) {
						iterator.remove();
						markSchedulerRemoved(scheduler.schedulerId, scheduler.owner);
						logSchedulerExpired(nowGameplayTick, scheduler, "lifecycle_timeout");
						continue;
					}

				if (!shouldScanSchedulerThisTick(
						scheduler,
						nowGameplayTick,
						gameplaySignal
					)) {
						continue;
					}

				if (scheduler.accepting && nowGameplayTick > scheduler.acceptUntilTick) {
					scheduler.accepting = false;
					markSchedulerDirty(scheduler.schedulerId);
				}

				if (queueSize(scheduler) == 0) {
					long idleTicks = nowGameplayTick - scheduler.lastActivityTick;
					boolean idleExpired = idleTicks >= IDLE_TIMEOUT_TICKS;
						if (!scheduler.accepting || idleExpired) {
							iterator.remove();
							markSchedulerRemoved(scheduler.schedulerId, scheduler.owner);
							logSchedulerExpired(nowGameplayTick, scheduler, idleExpired ? "idle_timeout" : "closed_no_tasks");
						}
						continue;
				}

					ScheduledRequest next = peekDueRequest(
						scheduler,
						nowGameplayTick,
						gameplaySignal
					);
				if (next == null) {
					continue;
				}

				if (!ownerExists(server, scheduler.owner)) {
					boolean transientUnavailable = isTransientOwnerUnavailable(server, scheduler.owner);
					boolean deadNow = !transientUnavailable;
					if (scheduler.dead != deadNow) {
						scheduler.dead = deadNow;
						markSchedulerDirty(scheduler.schedulerId);
					}
						if (!scheduler.paused) {
							scheduler.paused = true;
							markSchedulerDirty(scheduler.schedulerId);
						}
						if (transientUnavailable) {
							scheduler.nextOwnerCheckTick = safeAdd(nowGameplayTick, INACTIVE_SCAN_INTERVAL_TICKS);
							scheduler.nextScanTick = scheduler.nextOwnerCheckTick;
						}
						continue;
					}

					if (scheduler.dead) {
						scheduler.dead = false;
						markSchedulerDirty(scheduler.schedulerId);
					}
					if (scheduler.paused) {
						scheduler.paused = false;
						markSchedulerDirty(scheduler.schedulerId);
					}
					scheduler.nextOwnerCheckTick = nowGameplayTick;
					ExecutionResult result = executeTask(
						server,
						scheduler,
						next,
						nowGameplayTick
					);
					switch (result) {
						case SUCCESS -> {
							removeScheduledRequest(scheduler, next);
							scheduler.lastActivityTick = nowGameplayTick;
							markSchedulerDirty(scheduler.schedulerId);
						}
							case RETRY -> {
								removeScheduledRequest(scheduler, next);
								long nowDomainTick = nowGameplayTick;
								if (next.scheduleRetry(nowDomainTick)) {
									queueForDomain(scheduler, next.domain).add(next);
								} else {
								LOGGER.warn(
									"Dropping scheduler task after {} failed attempts: scheduler={} request_id={} task_type={}",
									next.failureCount,
									scheduler.schedulerId,
									next.requestId,
									next.taskType
								);
							}
							scheduler.lastActivityTick = nowGameplayTick;
							markSchedulerDirty(scheduler.schedulerId);
						}
					}
				}
			} finally {
			ticking = false;
			if (!PENDING_SCHEDULERS.isEmpty()) {
				SCHEDULERS.putAll(PENDING_SCHEDULERS);
				for (String schedulerId : PENDING_SCHEDULERS.keySet()) {
					markSchedulerDirty(schedulerId);
				}
				PENDING_SCHEDULERS.clear();
			}
		}
	}

	private static void logSchedulerExpired(long gameplayTick, SchedulerState scheduler, String reason) {
		if (scheduler == null) {
			return;
		}
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.SCHEDULER, "scheduler.expired")) {
			return;
		}
		MadokuDebug.event("scheduler.expired", MadokuDebug.Domain.SCHEDULER)
			.side(MadokuDebug.Side.SERVER)
			.tick(gameplayTick)
			.subject("scheduler:" + scheduler.schedulerId)
			.field("reason", reason)
			.field("owner", scheduler.owner.kind + ":" + scheduler.owner.ownerId)
			.field("queue_size", queueSize(scheduler))
			.log();
	}

	private static SchedulerState findScheduler(String schedulerId) {
		SchedulerState scheduler = SCHEDULERS.get(schedulerId);
		if (scheduler != null) {
			return scheduler;
		}
		if (!ticking) {
			return null;
		}
		return PENDING_SCHEDULERS.get(schedulerId);
	}

	private static SchedulerState findSchedulerByOwner(SchedulerOwner owner, long nowGameplayTick) {
		String ownerKey = ownerKey(owner);
		if (!ownerKey.isEmpty()) {
			String schedulerId = SCHEDULER_IDS_BY_OWNER.get(ownerKey);
			if (schedulerId != null) {
				SchedulerState indexed = findScheduler(schedulerId);
				if (indexed != null && sameOwner(indexed.owner, owner) && canAcceptEnqueue(indexed, nowGameplayTick)) {
					return indexed;
				}
				SCHEDULER_IDS_BY_OWNER.remove(ownerKey, schedulerId);
			}
		}

		for (SchedulerState scheduler : SCHEDULERS.values()) {
			if (sameOwner(scheduler.owner, owner) && canAcceptEnqueue(scheduler, nowGameplayTick)) {
				indexSchedulerOwner(scheduler);
				return scheduler;
			}
		}
		for (SchedulerState scheduler : PENDING_SCHEDULERS.values()) {
			if (sameOwner(scheduler.owner, owner) && canAcceptEnqueue(scheduler, nowGameplayTick)) {
				indexSchedulerOwner(scheduler);
				return scheduler;
			}
		}
		return null;
	}

	private static boolean canAcceptEnqueue(SchedulerState scheduler, long nowGameplayTick) {
		if (scheduler == null) {
			return false;
		}
		if (!scheduler.accepting) {
			return false;
		}
		if (nowGameplayTick > scheduler.acceptUntilTick) {
			return false;
		}
		return nowGameplayTick < scheduler.expireAtTick;
	}

	private static boolean sameOwner(SchedulerOwner left, SchedulerOwner right) {
		if (left == right) {
			return true;
		}
		if (left == null || right == null) {
			return false;
		}
		return left.kind.equals(right.kind)
			&& left.ownerId.equals(right.ownerId)
			&& Objects.equals(left.levelId, right.levelId);
	}

	private static boolean shouldScanSchedulerThisTick(
		SchedulerState scheduler,
		long nowGameplayTick,
		boolean gameplaySignal
	) {
		boolean ownerCheckReady = nowGameplayTick >= scheduler.nextOwnerCheckTick;
		ScheduledRequest dueRequest = peekDueRequest(
			scheduler,
			nowGameplayTick,
			gameplaySignal
		);
		boolean activeNow = dueRequest != null
			&& !scheduler.dead
			&& ownerCheckReady;
		if (activeNow) {
			scheduler.nextScanTick = nowGameplayTick;
			return true;
		}

		if (gameplaySignal && nowGameplayTick >= scheduler.nextScanTick) {
			scheduler.nextScanTick = safeAdd(nowGameplayTick, INACTIVE_SCAN_INTERVAL_TICKS);
			return true;
		}
		return false;
	}

	private static ScheduledRequest peekDueRequest(
		SchedulerState scheduler,
		long nowGameplayTick,
		boolean gameplaySignal
	) {
		ScheduledRequest gameplayHead = gameplaySignal ? scheduler.gameplayRequests.peek() : null;
		return gameplayHead != null && gameplayHead.dueTick <= nowGameplayTick ? gameplayHead : null;
	}

	private static int queueSize(SchedulerState scheduler) {
		return scheduler.gameplayRequests.size();
	}

	private static void removeScheduledRequest(SchedulerState scheduler, ScheduledRequest request) {
		if (scheduler == null || request == null) {
			return;
		}
		PriorityQueue<ScheduledRequest> queue = scheduler.gameplayRequests;
		if (queue.peek() == request) {
			queue.poll();
			return;
		}
		queue.remove(request);
	}

	private static PriorityQueue<ScheduledRequest> queueForDomain(SchedulerState scheduler, TickDomain domain) {
		return scheduler.gameplayRequests;
	}

	private static long currentTickForDomain(TickDomain domain) {
		return MadokuTicks.getGameplayTicks();
	}

	public static void loadPersistedData(MinecraftServer server) {
		reset();
		MadokuData.createWorldData(server, DATA_FOLDER_NAME, META_FILE_NAME, createDefaultMetadata());

		JsonObject metadata = MadokuData.loadWorldData(server, DATA_FOLDER_NAME, META_FILE_NAME);
		if (metadata != null) {
			long persistedGameplayTick = getLong(metadata, "gameplay_ticks", 0L);
			MadokuTicks.setGameplayTicks(persistedGameplayTick);
		}
		loadSchedulersFromMetadata(server, metadata);

		DIRTY_SCHEDULER_IDS.clear();
		REMOVED_SCHEDULER_IDS.clear();
		dirty = false;
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), AUTOSAVE_INTERVAL_TICKS);
	}

	public static void savePersistedData(MinecraftServer server) {
		flushPersistedData(server, true);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		long currentBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), AUTOSAVE_INTERVAL_TICKS);
		if (currentBucket == lastAutosaveBucket) {
			return;
		}

		boolean hasChanges =
			dirty
				|| !DIRTY_SCHEDULER_IDS.isEmpty()
				|| !REMOVED_SCHEDULER_IDS.isEmpty();
		boolean shouldPersistClock = !SCHEDULERS.isEmpty();
		if (!hasChanges && !shouldPersistClock) {
			lastAutosaveBucket = currentBucket;
			return;
		}

		flushPersistedData(server, false);
	}

	private static void flushPersistedData(MinecraftServer server, boolean writeAllSchedulers) {
		MadokuData.saveWorldData(server, DATA_FOLDER_NAME, META_FILE_NAME, toMetadataJson());

		if (writeAllSchedulers) {
			for (SchedulerState scheduler : SCHEDULERS.values()) {
				MadokuData.saveWorldData(
					server,
					DATA_FOLDER_NAME,
					schedulerFileName(scheduler.schedulerId),
					scheduler.toJson()
				);
			}
		} else if (!DIRTY_SCHEDULER_IDS.isEmpty()) {
			List<String> dirtyIds = new ArrayList<>(DIRTY_SCHEDULER_IDS);
			for (String schedulerId : dirtyIds) {
				SchedulerState scheduler = SCHEDULERS.get(schedulerId);
				if (scheduler == null) {
					continue;
				}
				MadokuData.saveWorldData(
					server,
					DATA_FOLDER_NAME,
					schedulerFileName(scheduler.schedulerId),
					scheduler.toJson()
				);
			}
		}

		if (!REMOVED_SCHEDULER_IDS.isEmpty()) {
			List<String> removedIds = new ArrayList<>(REMOVED_SCHEDULER_IDS);
			for (String schedulerId : removedIds) {
				MadokuData.deleteWorldData(server, DATA_FOLDER_NAME, schedulerFileName(schedulerId));
			}
		}

		DIRTY_SCHEDULER_IDS.clear();
		REMOVED_SCHEDULER_IDS.clear();
		dirty = false;
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), AUTOSAVE_INTERVAL_TICKS);
	}

	private static void loadSchedulersFromMetadata(MinecraftServer server, JsonObject metadata) {
		if (metadata == null) {
			return;
		}

		JsonElement idsElement = metadata.get("scheduler_ids");
		if (idsElement == null || !idsElement.isJsonArray()) {
			return;
		}

		for (String schedulerId : loadSchedulerIdsFromMetadata(idsElement.getAsJsonArray())) {
			JsonObject source = MadokuData.loadWorldData(server, DATA_FOLDER_NAME, schedulerFileName(schedulerId));
			if (source == null) {
				continue;
			}

				SchedulerState scheduler = SchedulerState.fromJson(source);
				if (scheduler == null) {
					LOGGER.warn("Skipping invalid scheduler file for id={}", schedulerId);
					continue;
				}
				if (!OWNER_RESOLVERS.containsKey(scheduler.owner.kind)) {
					LOGGER.warn(
						"Skipping scheduler with unknown owner kind during load: scheduler={} kind={}",
						scheduler.schedulerId,
						scheduler.owner.kind
					);
					continue;
				}
				SCHEDULERS.put(scheduler.schedulerId, scheduler);
				indexSchedulerOwner(scheduler);
			}
		}

	private static List<String> loadSchedulerIdsFromMetadata(JsonArray schedulerIds) {
		List<String> ids = new ArrayList<>();
		for (JsonElement element : schedulerIds) {
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				continue;
			}
			String schedulerId = element.getAsString();
			if (schedulerId == null || schedulerId.isBlank()) {
				continue;
			}
			ids.add(schedulerId.trim());
		}
		return ids;
	}

	public static void registerTaskHandler(String taskType, TaskHandler handler) {
		String key = normalizeKey(taskType);
		if (key.isEmpty()) {
			throw new IllegalArgumentException("Task type must not be blank.");
		}
		if (handler == null) {
			throw new IllegalArgumentException("Task handler must not be null.");
		}
		TASK_HANDLERS.put(key, handler);
	}

	public static void unregisterTaskHandler(String taskType) {
		String key = normalizeKey(taskType);
		if (!key.isEmpty()) {
			TASK_HANDLERS.remove(key);
		}
	}

	public static void registerOwnerResolver(String ownerKind, OwnerExistenceResolver resolver) {
		String key = normalizeKey(ownerKind);
		if (key.isEmpty()) {
			throw new IllegalArgumentException("Owner kind must not be blank.");
		}
		if (resolver == null) {
			throw new IllegalArgumentException("Owner resolver must not be null.");
		}
		OWNER_RESOLVERS.put(key, resolver);
	}

	public static final class SchedulerOwner {
		private final String kind;
		private final String ownerId;
		private final String levelId;

		private SchedulerOwner(String kind, String ownerId, String levelId) {
			this.kind = kind;
			this.ownerId = ownerId;
			this.levelId = levelId;
		}

		public static SchedulerOwner global(String ownerId) {
			return of("global", ownerId, null);
		}

		public static SchedulerOwner of(String kind, String ownerId, String levelId) {
			String normalizedKind = normalizeKey(kind);
			String normalizedOwnerId = normalizeOwnerId(ownerId);
			String normalizedLevelId = normalizeLevelId(levelId);
			return new SchedulerOwner(normalizedKind, normalizedOwnerId, normalizedLevelId);
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

		private SchedulerOwner normalized() {
			return of(kind, ownerId, levelId);
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

		public static TickDomain fromId(String value) {
			String normalized = normalizeKey(value);
			return switch (normalized) {
				case "gameplay", "time" -> GAMEPLAY;
				default -> null;
			};
		}
	}

	@FunctionalInterface
	public interface OwnerExistenceResolver {
		boolean exists(MinecraftServer server, SchedulerOwner owner);
	}

	public static final class TaskContext {
		private final String schedulerId;
		private final long requestId;
		private final long nowTick;
		private final SchedulerOwner owner;
		private final TickDomain domain;

		private TaskContext(
			String schedulerId,
			long requestId,
			long nowTick,
			SchedulerOwner owner,
			TickDomain domain
		) {
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

	private static JsonObject toMetadataJson() {
		JsonObject root = new JsonObject();
		root.addProperty("gameplay_ticks", MadokuTicks.getGameplayTicks());
		JsonArray schedulerIds = new JsonArray();
		for (SchedulerState scheduler : SCHEDULERS.values()) {
			schedulerIds.add(scheduler.schedulerId);
		}
		root.add("scheduler_ids", schedulerIds);
		return root;
	}

	private static JsonObject createDefaultMetadata() {
		JsonObject root = new JsonObject();
		root.addProperty("gameplay_ticks", 0L);
		root.add("scheduler_ids", new JsonArray());
		return root;
	}

	private static String schedulerFileName(String schedulerId) {
		return SCHEDULERS_FOLDER_NAME + "/" + schedulerId;
	}

	private static void markSchedulerDirty(String schedulerId) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return;
		}
		DIRTY_SCHEDULER_IDS.add(schedulerId);
		REMOVED_SCHEDULER_IDS.remove(schedulerId);
		dirty = true;
	}

	private static void markSchedulerRemoved(String schedulerId, SchedulerOwner owner) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return;
		}
		if (owner != null) {
			SCHEDULER_IDS_BY_OWNER.remove(ownerKey(owner), schedulerId);
		}
		DIRTY_SCHEDULER_IDS.remove(schedulerId);
		REMOVED_SCHEDULER_IDS.add(schedulerId);
		dirty = true;
	}

	private static void indexSchedulerOwner(SchedulerState scheduler) {
		if (scheduler == null || scheduler.owner == null || scheduler.schedulerId == null || scheduler.schedulerId.isBlank()) {
			return;
		}
		String key = ownerKey(scheduler.owner);
		if (!key.isEmpty()) {
			SCHEDULER_IDS_BY_OWNER.put(key, scheduler.schedulerId);
		}
	}

	private static String ownerKey(SchedulerOwner owner) {
		if (owner == null) {
			return "";
		}
		return ownerKey(owner.kind, owner.ownerId, owner.levelId);
	}

	private static String ownerKey(String kind, String ownerId, String levelId) {
		String safeKind = kind == null ? "" : kind.trim();
		String safeOwnerId = ownerId == null ? "" : ownerId.trim();
		String safeLevelId = levelId == null ? "" : levelId.trim();
		if (safeKind.isEmpty() || safeOwnerId.isEmpty()) {
			return "";
		}
		return safeKind + '\u0000' + safeOwnerId + '\u0000' + safeLevelId;
	}

	private static boolean ownerExists(MinecraftServer server, SchedulerOwner owner) {
		OwnerExistenceResolver resolver = OWNER_RESOLVERS.get(owner.kind);
		if (resolver == null) {
			return false;
		}
		try {
			return resolver.exists(server, owner);
		} catch (RuntimeException exception) {
			LOGGER.warn("Owner resolver failed for kind={} owner_id={}", owner.kind, owner.ownerId, exception);
			return false;
		}
	}

	private static boolean isTransientOwnerUnavailable(MinecraftServer server, SchedulerOwner owner) {
		if (owner == null) {
			return false;
		}

		if ("player".equals(owner.kind)) {
			// Offline players are expected; scheduler should pause rather than mark dead.
			return true;
		}

		if ("blockentity".equals(owner.kind)) {
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
			return !level.getChunkSource().hasChunk(chunkX, chunkZ);
		}

		if ("chunk".equals(owner.kind)) {
			ServerLevel level = resolveLevel(server, owner.levelId);
			if (level == null) {
				return false;
			}

			int separator = owner.ownerId.indexOf(',');
			if (separator <= 0 || separator >= owner.ownerId.length() - 1) {
				return false;
			}

			String xRaw = owner.ownerId.substring(0, separator).trim();
			String zRaw = owner.ownerId.substring(separator + 1).trim();
			Integer chunkX = parseInt(xRaw);
			Integer chunkZ = parseInt(zRaw);
			if (chunkX == null || chunkZ == null) {
				return false;
			}
			return !level.getChunkSource().hasChunk(chunkX, chunkZ);
		}

		return false;
	}

	private static ExecutionResult executeTask(
		MinecraftServer server,
		SchedulerState scheduler,
		ScheduledRequest request,
		long nowGameplayTick
	) {
		TaskHandler handler = TASK_HANDLERS.get(request.taskType);
		if (handler == null) {
			LOGGER.warn(
				"Scheduler task type '{}' has no handler yet; will retry. scheduler={} request_id={}",
				request.taskType,
				scheduler.schedulerId,
				request.requestId
			);
			return ExecutionResult.RETRY;
		}

			try {
				long nowDomainTick = nowGameplayTick;
				handler.execute(
					server,
					new TaskContext(
					scheduler.schedulerId,
					request.requestId,
					nowDomainTick,
					scheduler.owner,
					request.domain
				),
				request.payload.deepCopy()
			);
			return ExecutionResult.SUCCESS;
		} catch (RuntimeException exception) {
			LOGGER.error(
				"Scheduler task failed and will retry: scheduler={} request_id={} task_type={}",
				scheduler.schedulerId,
				request.requestId,
				request.taskType,
				exception
			);
			return ExecutionResult.RETRY;
		}
	}

	private static boolean playerExists(MinecraftServer server, SchedulerOwner owner) {
		UUID uuid = parseUuid(owner.ownerId);
		return uuid != null && server.getPlayerList().getPlayer(uuid) != null;
	}

	private static boolean entityExists(MinecraftServer server, SchedulerOwner owner) {
		UUID uuid = parseUuid(owner.ownerId);
		if (uuid == null) {
			return false;
		}

		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(uuid) != null) {
				return true;
			}
		}
		return false;
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
		if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
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

		String xRaw = owner.ownerId.substring(0, separator).trim();
		String zRaw = owner.ownerId.substring(separator + 1).trim();
		Integer chunkX = parseInt(xRaw);
		Integer chunkZ = parseInt(zRaw);
		if (chunkX == null || chunkZ == null) {
			return false;
		}
		return level.getChunkSource().hasChunk(chunkX, chunkZ);
	}

	private static ServerLevel resolveLevel(MinecraftServer server, String levelId) {
		if (levelId == null || levelId.isBlank()) {
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

	private static String normalizeKey(String value) {
		return value == null ? "" : value.trim().toLowerCase();
	}

	private static String normalizeOwnerId(String ownerId) {
		String normalized = ownerId == null ? "" : ownerId.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Owner ID must not be blank.");
		}
		return normalized;
	}

	public static String normalizeLevelIdentifier(String levelId) {
		return normalizeLevelId(levelId);
	}

	private static String normalizeLevelId(String levelId) {
		if (levelId == null) {
			return null;
		}
		String normalized = extractIdentifierFromLevelKey(levelId.trim());
		return normalized.isEmpty() ? null : normalized;
	}

	private static String extractIdentifierFromLevelKey(String value) {
		if (value == null) {
			return "";
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return "";
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

	private static long getLong(JsonObject object, String key, long fallback) {
		if (object == null) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsLong();
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static int getInt(JsonObject object, String key, int fallback) {
		long value = getLong(object, key, fallback);
		if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
			return fallback;
		}
		return (int) value;
	}

	private static long safeAdd(long base, long delta) {
		if (delta <= 0L) {
			return base;
		}
		if (Long.MAX_VALUE - base < delta) {
			return Long.MAX_VALUE;
		}
		return base + delta;
	}

	private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
		if (object == null) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return element.getAsBoolean();
	}

	private static String getString(JsonObject object, String key, String fallback) {
		if (object == null) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}
		String value = element.getAsString();
		return value == null ? fallback : value;
	}

	private static String getNullableString(JsonObject object, String key) {
		if (object == null) {
			return null;
		}
		JsonElement element = object.get(key);
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return null;
		}
		String value = element.getAsString();
		if (value == null || value.isBlank()) {
			return null;
		}
		return value;
	}

	private static final class SchedulerState {
		private final String schedulerId;
		private final SchedulerOwner owner;
		private final PriorityQueue<ScheduledRequest> gameplayRequests;
		private long nextRequestId;
		private final long createdAtTick;
		private final long acceptUntilTick;
		private final long expireAtTick;
		private long lastActivityTick;
		private long nextScanTick;
		private long nextOwnerCheckTick;
		private boolean accepting;
		private boolean dead;
		private boolean paused;

		private SchedulerState(
			String schedulerId,
			SchedulerOwner owner,
			long createdAtTick,
			long acceptUntilTick,
			long expireAtTick
		) {
			this.schedulerId = schedulerId;
			this.owner = owner;
			this.createdAtTick = createdAtTick;
			this.acceptUntilTick = acceptUntilTick;
			this.expireAtTick = expireAtTick;
			this.lastActivityTick = createdAtTick;
			this.nextScanTick = createdAtTick;
			this.nextOwnerCheckTick = createdAtTick;
			this.accepting = true;
			this.dead = false;
			this.paused = false;
			this.nextRequestId = 1L;
			this.gameplayRequests = new PriorityQueue<>(REQUEST_COMPARATOR);
		}

		private JsonObject toJson() {
			JsonObject root = new JsonObject();
			root.addProperty("scheduler_id", schedulerId);
			root.add("owner", owner.toJson());
			root.addProperty("next_request_id", nextRequestId);
			root.addProperty("created_at_tick", createdAtTick);
			root.addProperty("accept_until_tick", acceptUntilTick);
			root.addProperty("expire_at_tick", expireAtTick);
			root.addProperty("last_activity_tick", lastActivityTick);
			root.addProperty("accepting", accepting);
			root.addProperty("dead", dead);
			root.addProperty("paused", paused);
			JsonArray queue = new JsonArray();
			List<ScheduledRequest> allRequests = new ArrayList<>(gameplayRequests.size());
			allRequests.addAll(gameplayRequests);
			allRequests.sort(Comparator.comparingLong((ScheduledRequest request) -> request.requestId));
			for (ScheduledRequest request : allRequests) {
				queue.add(request.toJson());
			}
			root.add("requests", queue);
			return root;
		}

		private static SchedulerState fromJson(JsonElement element) {
			if (element == null || !element.isJsonObject()) {
				return null;
			}

			JsonObject source = element.getAsJsonObject();
			String schedulerId = getString(source, "scheduler_id", "").trim();
			if (schedulerId.isEmpty()) {
				return null;
			}

			JsonElement ownerElement = source.get("owner");
			if (ownerElement == null || !ownerElement.isJsonObject()) {
				return null;
			}

			SchedulerOwner owner = SchedulerOwner.fromJson(ownerElement.getAsJsonObject());
			if (owner == null) {
				return null;
			}

			long createdAtTick = Math.max(0L, getLong(source, "created_at_tick", 0L));
			long acceptUntilTick = Math.max(createdAtTick, getLong(source, "accept_until_tick", createdAtTick + MINECRAFT_DAY_TICKS));
			long expireAtTick = Math.max(acceptUntilTick, getLong(source, "expire_at_tick", createdAtTick + WEEK_TIMEOUT_TICKS));

			SchedulerState scheduler = new SchedulerState(
				schedulerId,
				owner,
				createdAtTick,
				acceptUntilTick,
				expireAtTick
			);
			scheduler.nextRequestId = Math.max(1L, getLong(source, "next_request_id", 1L));
			scheduler.lastActivityTick = Math.max(createdAtTick, getLong(source, "last_activity_tick", createdAtTick));
			scheduler.nextScanTick = createdAtTick;
			scheduler.nextOwnerCheckTick = createdAtTick;
			scheduler.accepting = getBoolean(source, "accepting", true);
			scheduler.dead = getBoolean(source, "dead", false);
			scheduler.paused = getBoolean(source, "paused", false);

			JsonElement queueElement = source.get("requests");
				if (queueElement != null && queueElement.isJsonArray()) {
					for (JsonElement requestElement : queueElement.getAsJsonArray()) {
						ScheduledRequest request = ScheduledRequest.fromJson(requestElement);
						if (request == null) {
							continue;
						}
						scheduler.gameplayRequests.add(request);
						scheduler.nextRequestId = Math.max(scheduler.nextRequestId, request.requestId + 1L);
					}
				}

			return scheduler;
		}
	}

	private static final class ScheduledRequest {
		private final long requestId;
		private final long enqueuedTick;
		private long dueTick;
		private final TickDomain domain;
		private final String taskType;
		private final JsonObject payload;
		private int failureCount;

		private ScheduledRequest(
			long requestId,
			long enqueuedTick,
			long dueTick,
			TickDomain domain,
			String taskType,
			JsonObject payload,
			int failureCount
		) {
			this.requestId = requestId;
			this.enqueuedTick = enqueuedTick;
			this.dueTick = dueTick;
			this.domain = Objects.requireNonNull(domain, "Tick domain must not be null.");
			this.taskType = taskType;
			this.payload = payload;
			this.failureCount = Math.max(0, failureCount);
		}

		private JsonObject toJson() {
			JsonObject root = new JsonObject();
			root.addProperty("request_id", requestId);
			root.addProperty("enqueued_tick", enqueuedTick);
			root.addProperty("due_tick", dueTick);
			root.addProperty("domain", domain.id());
			root.addProperty("task_type", taskType);
			root.addProperty("failure_count", failureCount);
			root.add("payload", payload == null ? new JsonObject() : payload.deepCopy());
			return root;
		}

		private boolean scheduleRetry(long now) {
			failureCount++;
			if (failureCount > MAX_TASK_RETRY_ATTEMPTS) {
				return false;
			}

			int shift = Math.min(30, failureCount - 1);
			long multiplier = 1L << shift;
			long backoff = RETRY_BACKOFF_BASE_TICKS * multiplier;
			if (backoff > RETRY_BACKOFF_MAX_TICKS) {
				backoff = RETRY_BACKOFF_MAX_TICKS;
			}
			dueTick = safeAdd(now, backoff);
			return true;
		}

		private static ScheduledRequest fromJson(JsonElement element) {
			if (element == null || !element.isJsonObject()) {
				return null;
			}

			JsonObject source = element.getAsJsonObject();
			long requestId = Math.max(1L, getLong(source, "request_id", 1L));
			long enqueuedTick = Math.max(0L, getLong(source, "enqueued_tick", 0L));
			long dueTick = Math.max(0L, getLong(source, "due_tick", enqueuedTick));
			TickDomain domain = TickDomain.fromId(getString(source, "domain", ""));
			if (domain == null) {
				return null;
			}
			String taskType = normalizeKey(getString(source, "task_type", ""));
			if (taskType.isEmpty()) {
				return null;
			}
			int failureCount = Math.max(0, getInt(source, "failure_count", 0));

			JsonObject payload = new JsonObject();
			JsonElement payloadElement = source.get("payload");
			if (payloadElement != null && payloadElement.isJsonObject()) {
				payload = payloadElement.getAsJsonObject().deepCopy();
			}

			return new ScheduledRequest(requestId, enqueuedTick, dueTick, domain, taskType, payload, failureCount);
		}
	}

	private enum ExecutionResult {
		SUCCESS,
		RETRY
	}
}

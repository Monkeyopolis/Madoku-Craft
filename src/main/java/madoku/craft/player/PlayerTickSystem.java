package madoku.craft.player;

import com.google.gson.JsonObject;
import madoku.craft.scheduler.SchedulerManagerSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlayerTickSystem {
	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerTickSystem.class);
	private static final String TASK_TYPE_PLAYER_TICK = "player_tick_dispatch";
	private static final String PLAYER_TICK_SCHEDULER_KEY = "player_tick_dispatch";
	private static final long PLAYER_TICK_DELAY = 1L;

	private static final Map<String, ListenerEntry> LISTENERS = new LinkedHashMap<>();
	private static volatile String schedulerId = "";
	private static volatile boolean tickQueued;

	private PlayerTickSystem() {
	}

	public static void initialize() {
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_PLAYER_TICK, PlayerTickSystem::runPlayerTickTask);
	}

	public static void reset() {
		schedulerId = "";
		tickQueued = false;
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server == null) {
			return;
		}
		ensureQueued(server, PLAYER_TICK_DELAY);
	}

	public static void registerListener(String id, int order, Listener listener) {
		String normalizedId = id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
		if (normalizedId.isEmpty() || listener == null) {
			throw new IllegalArgumentException("Player tick listener id and handler must not be blank.");
		}
		synchronized (LISTENERS) {
			LISTENERS.put(normalizedId, new ListenerEntry(normalizedId, order, listener));
		}
	}

	private static void runPlayerTickTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		tickQueued = false;
		if (server == null || context == null) {
			return;
		}

		schedulerId = context.getSchedulerId();
		long gameplayTick = context.getNowTick();
		List<ListenerEntry> listeners = snapshotListeners();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player == null) {
				continue;
			}
			for (ListenerEntry listener : listeners) {
				try {
					listener.handler().onPlayerTick(server, player, gameplayTick);
				} catch (RuntimeException exception) {
					LOGGER.error("Player tick listener '{}' failed for player {}", listener.id(), player.getUUID(), exception);
				}
			}
		}

		ensureQueued(server, PLAYER_TICK_DELAY);
	}

	private static List<ListenerEntry> snapshotListeners() {
		List<ListenerEntry> snapshot;
		synchronized (LISTENERS) {
			snapshot = new ArrayList<>(LISTENERS.values());
		}
		snapshot.sort(Comparator.comparingInt(ListenerEntry::order).thenComparing(ListenerEntry::id));
		return snapshot;
	}

	private static void ensureQueued(MinecraftServer server, long delayTicks) {
		if (server == null || tickQueued) {
			return;
		}

		String currentSchedulerId = ensureScheduler();
		if (SchedulerManagerSystem.hasQueuedTask(currentSchedulerId, TASK_TYPE_PLAYER_TICK)) {
			tickQueued = true;
			return;
		}
		if (enqueue(currentSchedulerId, delayTicks)) {
			tickQueued = true;
			return;
		}

		schedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(PLAYER_TICK_SCHEDULER_KEY)
		);
		if (enqueue(schedulerId, delayTicks)) {
			tickQueued = true;
			return;
		}

		LOGGER.error("Failed to enqueue player tick dispatch task.");
	}

	private static String ensureScheduler() {
		String current = schedulerId;
		if (current != null && !current.isBlank()) {
			return current;
		}
		schedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(PLAYER_TICK_SCHEDULER_KEY)
		);
		return schedulerId;
	}

	private static boolean enqueue(String targetSchedulerId, long delayTicks) {
		if (targetSchedulerId == null || targetSchedulerId.isBlank()) {
			return false;
		}
		SchedulerManagerSystem.EnqueueStatus status = SchedulerManagerSystem.enqueue(
			targetSchedulerId,
			Math.max(0L, delayTicks),
			TASK_TYPE_PLAYER_TICK,
			new JsonObject(),
			SchedulerManagerSystem.TickDomain.GAMEPLAY
		);
		return status == SchedulerManagerSystem.EnqueueStatus.ACCEPTED
			|| status == SchedulerManagerSystem.EnqueueStatus.QUEUE_FULL;
	}

	@FunctionalInterface
	public interface Listener {
		void onPlayerTick(MinecraftServer server, ServerPlayer player, long gameplayTick);
	}

	private record ListenerEntry(String id, int order, Listener handler) {
	}
}

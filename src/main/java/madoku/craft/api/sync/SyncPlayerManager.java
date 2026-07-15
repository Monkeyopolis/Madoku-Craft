package madoku.craft.api.sync;

import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.scheduler.MadokuSchedulerManager;
import madoku.craft.api.time.MadokuTimeManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/** Owns player-targeted synchronization transport and adaptive dirty flush timing. */
public final class SyncPlayerManager {
	private static final String DEBUG_MAIN_SYSTEM = "api";
	private static final String DEBUG_SUB_SYSTEM = "sync-manager";
	private static final String DEBUG_GROUP = "sync-player-manager";
	private static final String ADAPTIVE_OWNER_ID = "api.sync.player";
	private static final long MIN_DIRTY_FLUSH_INTERVAL_TICKS = 1L;
	private static final long MAX_DIRTY_FLUSH_INTERVAL_TICKS = 5L;
	private static long nextDirtyFlushTick = Long.MIN_VALUE;

	private SyncPlayerManager() {
	}

	public static void initialize() {
		nextDirtyFlushTick = Long.MIN_VALUE;
		emitDebug("initialize", builder -> builder
			.field("minimum-interval-ticks", MIN_DIRTY_FLUSH_INTERVAL_TICKS)
			.field("maximum-interval-ticks", MAX_DIRTY_FLUSH_INTERVAL_TICKS));
	}

	public static void reset() {
		nextDirtyFlushTick = Long.MIN_VALUE;
		MadokuSchedulerManager.clearAdaptiveDelayState(ADAPTIVE_OWNER_ID);
		emitDebug("reset", null);
	}

	public static void onServerStarted(MinecraftServer server) {
		nextDirtyFlushTick = Long.MIN_VALUE;
		emitDebug("server-started", builder -> builder.field("players", playerCount(server)));
	}

	public static void onServerStopping(MinecraftServer server) {
		emitDebug("server-stopping", builder -> builder.field("players", playerCount(server)));
	}

	/** Returns whether a dirty player-state queue should be flushed now. */
	public static boolean shouldFlushDirtySyncs(MinecraftServer server) {
		if (server == null) {
			return false;
		}

		long nowTick = Math.max(0L, MadokuTimeManager.getGameplayTicks());
		if (nextDirtyFlushTick != Long.MIN_VALUE && nowTick < nextDirtyFlushTick) {
			return false;
		}

		long interval = MadokuSchedulerManager.resolveAdaptiveDelayTicks(
			server,
			ADAPTIVE_OWNER_ID,
			MIN_DIRTY_FLUSH_INTERVAL_TICKS,
			MAX_DIRTY_FLUSH_INTERVAL_TICKS
		);
		nextDirtyFlushTick = nowTick + Math.max(1L, interval);
		return true;
	}

	public static boolean canSend(ServerPlayer player, CustomPacketPayload payload) {
		return SyncGlobalManager.canSend(player, payload);
	}

	public static boolean send(ServerPlayer player, CustomPacketPayload payload) {
		return SyncGlobalManager.send(player, payload);
	}

	private static int playerCount(MinecraftServer server) {
		return server == null ? 0 : server.getPlayerList().getPlayers().size();
	}

	private static void emitDebug(String entry, Consumer<MadokuDebugManager.EventBuilder> customizer) {
		if (!MadokuDebugManager.shouldEmit(DEBUG_MAIN_SYSTEM, DEBUG_SUB_SYSTEM, DEBUG_GROUP, entry)) {
			return;
		}
		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(
			"sync.player",
			DEBUG_MAIN_SYSTEM,
			DEBUG_SUB_SYSTEM,
			DEBUG_GROUP,
			entry
		).side(MadokuDebugManager.Side.SERVER).tick(MadokuTimeManager.getGameplayTicks()).subject(entry);
		if (customizer != null) {
			customizer.accept(builder);
		}
		builder.log();
	}
}

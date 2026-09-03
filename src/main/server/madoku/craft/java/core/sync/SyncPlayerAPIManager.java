package madoku.craft.java.core.sync;

import madoku.craft.java.core.scheduler.SchedulerAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;


/** Owns player-targeted synchronization transport and adaptive dirty flush timing. */
public final class SyncPlayerAPIManager {
	private static final String ADAPTIVE_OWNER_ID = "api.sync.player";
	private static final long MIN_DIRTY_FLUSH_INTERVAL_TICKS = 1L;
	private static final long MAX_DIRTY_FLUSH_INTERVAL_TICKS = 5L;
	private static long nextDirtyFlushTick = Long.MIN_VALUE;

	private SyncPlayerAPIManager() {
	}

	public static void initialize() {
		nextDirtyFlushTick = Long.MIN_VALUE;
	}

	public static void reset() {
		nextDirtyFlushTick = Long.MIN_VALUE;
		SchedulerAPIManager.clearAdaptiveDelayState(ADAPTIVE_OWNER_ID);
	}

	public static void onServerStarted(MinecraftServer server) {
		nextDirtyFlushTick = Long.MIN_VALUE;
	}

	public static void onServerStopping(MinecraftServer server) {
	}

	/** Returns whether a dirty player-state queue should be flushed now. */
	public static boolean shouldFlushDirtySyncs(MinecraftServer server) {
		if (server == null) {
			return false;
		}

		long nowTick = Math.max(0L, TimeAPIManager.getGameplayTicks());
		if (nextDirtyFlushTick != Long.MIN_VALUE && nowTick < nextDirtyFlushTick) {
			return false;
		}

		long interval = SchedulerAPIManager.resolveAdaptiveDelayTicks(
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


}


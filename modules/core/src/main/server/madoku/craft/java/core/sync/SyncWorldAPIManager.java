package madoku.craft.java.core.sync;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import madoku.craft.java.core.runtime.AdaptiveIntervalAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;


/** Owns world-scoped synchronization transport and adaptive periodic passes. */
public final class SyncWorldAPIManager {
	private static final String ADAPTIVE_OWNER_ID = "api.sync.world";
	private static final long MIN_PERIODIC_INTERVAL_TICKS = 1L;
	private static final long MAX_PERIODIC_INTERVAL_TICKS = 5L;
	private static long nextPeriodicSyncTick = Long.MIN_VALUE;

	private SyncWorldAPIManager() {
	}

	public static void initialize() {
		nextPeriodicSyncTick = Long.MIN_VALUE;
	}

	public static void reset() {
		nextPeriodicSyncTick = Long.MIN_VALUE;
		AdaptiveIntervalAPIManager.clearSystem(ADAPTIVE_OWNER_ID);
	}

	public static void onServerStarted(MinecraftServer server) {
		nextPeriodicSyncTick = Long.MIN_VALUE;
	}

	public static void onServerStopping(MinecraftServer server) {
	}

	public static boolean shouldRunPeriodicSync(MinecraftServer server) {
		if (server == null) {
			return false;
		}

		long nowTick = Math.max(0L, TimeAPIManager.getGameplayTicks());
		if (nextPeriodicSyncTick != Long.MIN_VALUE && nowTick < nextPeriodicSyncTick) {
			return false;
		}

		long interval = AdaptiveIntervalAPIManager.resolve(
			ADAPTIVE_OWNER_ID,
			server,
			MIN_PERIODIC_INTERVAL_TICKS,
			MAX_PERIODIC_INTERVAL_TICKS
		);
		nextPeriodicSyncTick = nowTick + Math.max(1L, interval);
		return true;
	}

	public static boolean canSend(ServerPlayer player, CustomPacketPayload payload) {
		return player != null
			&& payload != null
			&& ServerPlayNetworking.canSend(player, payload.type());
	}

	public static boolean send(ServerPlayer player, CustomPacketPayload payload) {
		if (!canSend(player, payload)) {
			return false;
		}
		ServerPlayNetworking.send(player, payload);
		return true;
	}

	public static int broadcast(MinecraftServer server, CustomPacketPayload payload) {
		if (server == null || payload == null) {
			return 0;
		}

		int sent = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (send(player, payload)) {
				sent++;
			}
		}
		return sent;
	}


}

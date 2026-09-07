package madoku.craft.java.core.sync;

import madoku.craft.java.core.season.PlayerClimatePayloadAPIManager;
import madoku.craft.java.core.season.SeasonPayloadAPIManager;
import madoku.craft.java.core.time.TimePayloadAPIManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Owns Core payload registration and shared transport primitives. */
public final class SyncGlobalManager {
	private static volatile boolean initialized;

	private SyncGlobalManager() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		registerPayloadTypes();
		initialized = true;
	}

	public static void initializeClient() {
		if (initialized) {
			return;
		}
		registerPayloadTypes();
		initialized = true;
	}

	public static void reset() {
	}

	public static void onServerStarted(MinecraftServer server) {
	}

	public static void onServerStopping(MinecraftServer server) {
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

	private static void registerPayloadTypes() {
		registerClientbound(SeasonPayloadAPIManager.TYPE, SeasonPayloadAPIManager.CODEC);
		registerClientbound(PlayerClimatePayloadAPIManager.TYPE, PlayerClimatePayloadAPIManager.CODEC);
		registerClientbound(TimePayloadAPIManager.TYPE, TimePayloadAPIManager.CODEC);
		registerClientbound(SyncPayloadAPIManager.TYPE, SyncPayloadAPIManager.CODEC);
	}

	private static <T extends CustomPacketPayload> void registerClientbound(
		CustomPacketPayload.Type<T> type,
		StreamCodec<RegistryFriendlyByteBuf, T> codec
	) {
		PayloadTypeRegistry.clientboundPlay().register(type, codec);
	}

}

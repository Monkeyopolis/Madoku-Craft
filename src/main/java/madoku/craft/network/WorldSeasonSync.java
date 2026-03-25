package madoku.craft.network;

import madoku.craft.season.MadokuSeason;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class WorldSeasonSync {
	private static boolean initialized = false;
	private static String lastBroadcastSeason = "";

	private WorldSeasonSync() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		PayloadTypeRegistry.clientboundPlay().register(WorldSeasonPayload.TYPE, WorldSeasonPayload.CODEC);
			ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
				WorldSeasonPayload payload = currentPayload(server);
				if (payload == null || !ServerPlayNetworking.canSend(handler, WorldSeasonPayload.TYPE)) {
					return;
				}
				sender.sendPacket(payload);
			});
		initialized = true;
	}

	public static void reset() {
		lastBroadcastSeason = "";
	}

	public static void broadcastNow(MinecraftServer server) {
		broadcast(server, true);
	}

	public static void broadcastIfChanged(MinecraftServer server) {
		broadcast(server, false);
	}

	private static void broadcast(MinecraftServer server, boolean force) {
		if (server == null) {
			return;
		}

		if (!MadokuSeason.isEnabled()) {
			broadcastSeasonCleared(server, force);
			return;
		}

		WorldSeasonPayload payload = currentPayload(server);
		if (payload == null) {
			return;
		}

		String season = payload.season();
		if (!force && season.equals(lastBroadcastSeason)) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, WorldSeasonPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}

		lastBroadcastSeason = season;
	}

	private static void broadcastSeasonCleared(MinecraftServer server, boolean force) {
		if (server == null) {
			return;
		}

		if (!force && lastBroadcastSeason.isEmpty()) {
			return;
		}

		WorldSeasonPayload payload = new WorldSeasonPayload("");
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, WorldSeasonPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}

		lastBroadcastSeason = "";
	}

	private static WorldSeasonPayload currentPayload(MinecraftServer server) {
		if (!MadokuSeason.isEnabled()) {
			return null;
		}
		ServerLevel world = server == null ? null : server.overworld();
		String season = MadokuSeason.getCurrentSeasonId(world);
		if (season == null || season.isBlank()) {
			return null;
		}
		return new WorldSeasonPayload(season);
	}
}

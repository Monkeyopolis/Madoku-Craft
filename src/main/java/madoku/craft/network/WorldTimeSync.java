package madoku.craft.network;

import madoku.craft.time.MadokuTime;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class WorldTimeSync {
	private static boolean initialized = false;
	private static long lastBroadcastDay = -1L;
	private static long lastBroadcastTotalMinutes = -1L;

	private WorldTimeSync() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		PayloadTypeRegistry.clientboundPlay().register(WorldTimePayload.TYPE, WorldTimePayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			WorldTimePayload payload = currentPayload(server);
			if (payload == null || !ServerPlayNetworking.canSend(handler, WorldTimePayload.TYPE)) {
				return;
			}
			sender.sendPacket(payload);
		});
		initialized = true;
	}

	public static void reset() {
		lastBroadcastDay = -1L;
		lastBroadcastTotalMinutes = -1L;
	}

	public static void broadcastNow(MinecraftServer server) {
		broadcast(server, true);
	}

	public static void broadcastIfChanged(MinecraftServer server) {
		broadcast(server, false);
	}

	private static void broadcast(MinecraftServer server, boolean force) {
		WorldTimePayload payload = currentPayload(server);
		if (payload == null) {
			return;
		}

		WorldTimeSnapshot snapshot = new WorldTimeSnapshot(payload.day(), payload.hour(), payload.minute(), payload.hour() * 60L + payload.minute());
		if (!force && snapshot.day() == lastBroadcastDay && snapshot.totalMinutes() == lastBroadcastTotalMinutes) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, WorldTimePayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}

		lastBroadcastDay = snapshot.day();
		lastBroadcastTotalMinutes = snapshot.totalMinutes();
	}

	private static WorldTimePayload currentPayload(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return null;
		}

		WorldTimeSnapshot snapshot = fromDayTime(overworld.getOverworldClockTime());
		return new WorldTimePayload(snapshot.day(), snapshot.hour(), snapshot.minute());
	}

	private static WorldTimeSnapshot fromDayTime(long dayTime) {
		long day = MadokuTime.getDay(dayTime);
		int totalMinutes = MadokuTime.getTotalMinutes(dayTime);
		int hour = totalMinutes / 60;
		int minute = totalMinutes % 60;
		return new WorldTimeSnapshot(day, hour, minute, totalMinutes);
	}

	private record WorldTimeSnapshot(long day, int hour, int minute, long totalMinutes) {
	}
}

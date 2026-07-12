package madoku.craft.network;

import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.metadata.MadokuMetaDataManager;
import madoku.craft.api.season.MadokuSeasonManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

public final class WorldSeasonSync {
	private static boolean initialized = false;
	private static String lastBroadcastSeason = "";
	private static double lastBroadcastTemperatureOffset;
	private static double lastBroadcastHumidityOffset;

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
		emitDebug("initialize", builder -> builder.field("payload", WorldSeasonPayload.TYPE.id()));
	}

	public static void reset() {
		lastBroadcastSeason = "";
		lastBroadcastTemperatureOffset = 0.0;
		lastBroadcastHumidityOffset = 0.0;
		emitDebug("reset", builder -> builder.field("season", ""));
	}

	public static void broadcastNow(MinecraftServer server) {
		int sent = broadcast(server, true);
		emitDebug("broadcast-now", builder -> builder
			.field("season", lastBroadcastSeason)
			.field("players-sent", sent)
			.field("force", true));
	}

	public static void broadcastIfChanged(MinecraftServer server) {
		int sent = broadcast(server, false);
		if (sent > 0) {
			emitDebug("broadcast-changed", builder -> builder
				.field("season", lastBroadcastSeason)
				.field("temperature-offset", lastBroadcastTemperatureOffset)
				.field("humidity-offset", lastBroadcastHumidityOffset)
				.field("players-sent", sent)
				.field("force", false));
		}
	}

	private static int broadcast(MinecraftServer server, boolean force) {
		if (server == null) {
			return 0;
		}

		if (!MadokuSeasonManager.isEnabled()) {
			return broadcastSeasonCleared(server, force);
		}

		WorldSeasonPayload payload = currentPayload(server);
		if (payload == null) {
			return 0;
		}

		String season = payload.season();
		if (!force && season.equals(lastBroadcastSeason)
			&& Double.compare(payload.temperatureOffset(), lastBroadcastTemperatureOffset) == 0
			&& Double.compare(payload.humidityOffset(), lastBroadcastHumidityOffset) == 0) {
			return 0;
		}

		int sent = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, WorldSeasonPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
				sent++;
			}
		}

		lastBroadcastSeason = season;
		lastBroadcastTemperatureOffset = payload.temperatureOffset();
		lastBroadcastHumidityOffset = payload.humidityOffset();
		return sent;
	}

	private static int broadcastSeasonCleared(MinecraftServer server, boolean force) {
		if (server == null) {
			return 0;
		}

		if (!force && lastBroadcastSeason.isEmpty()) {
			return 0;
		}

		WorldSeasonPayload payload = new WorldSeasonPayload("", 0.0, 0.0);
		int sent = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, WorldSeasonPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
				sent++;
			}
		}

		lastBroadcastSeason = "";
		lastBroadcastTemperatureOffset = 0.0;
		lastBroadcastHumidityOffset = 0.0;
		return sent;
	}

	private static WorldSeasonPayload currentPayload(MinecraftServer server) {
		if (!MadokuSeasonManager.isEnabled()) {
			return null;
		}
		ServerLevel world = server == null ? null : server.overworld();
		String season = MadokuSeasonManager.getCurrentSeasonId(world);
		if (season == null || season.isBlank()) {
			return null;
		}
		return new WorldSeasonPayload(
			season,
			madoku.craft.api.season.SeasonEnvironmentTransitionManager.getTemperatureOffset(),
			madoku.craft.api.season.SeasonEnvironmentTransitionManager.getHumidityOffset());
	}

	private static void emitDebug(String subject, Consumer<MadokuDebugManager.EventBuilder> customizer) {
		String entry = MadokuDebugManager.resolveCallerMethodName(1);
		if (!MadokuDebugManager.shouldEmit(MadokuMetaDataManager.SEASON.mainSystem(), "season-sync", entry)) {
			return;
		}
		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(
			"season.sync",
			MadokuMetaDataManager.SEASON.mainSystem(),
			"season-sync",
			entry
		).side(MadokuDebugManager.Side.SERVER).subject(subject);
		if (customizer != null) customizer.accept(builder);
		builder.log();
	}
}


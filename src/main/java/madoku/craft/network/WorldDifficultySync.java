package madoku.craft.network;

import madoku.craft.api.chunk.MadokuChunkManager;
import net.minecraft.core.BlockPos;
import madoku.craft.difficulty.system.MadokuRegionalDifficultyManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WorldDifficultySync {
	private static boolean initialized = false;
	private static final Map<UUID, PlayerDifficultyState> lastStateByPlayer = new HashMap<>();

	private WorldDifficultySync() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		PayloadTypeRegistry.clientboundPlay().register(WorldDifficultyPayload.TYPE, WorldDifficultyPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			syncSinglePlayer(server, handler.player, true)
		);
		initialized = true;
	}

	public static void reset() {
		lastStateByPlayer.clear();
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

		Set<UUID> activePlayers = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			activePlayers.add(player.getUUID());
			syncSinglePlayer(server, player, force);
		}
		lastStateByPlayer.keySet().removeIf(uuid -> !activePlayers.contains(uuid));
	}

	private static void syncSinglePlayer(MinecraftServer server, ServerPlayer player, boolean force) {
		if (server == null || player == null) {
			return;
		}

		UUID playerId = player.getUUID();
		PlayerDifficultyState previous = lastStateByPlayer.get(playerId);
			PlayerDifficultyState currentKey = captureStateKey(player);
		if (currentKey == null) {
			return;
		}

		if (!force && previous != null && previous.sameKey(currentKey)) {
			return;
		}

		int difficultyLevel = MadokuRegionalDifficultyManager.resolveHudDifficultyLevel(player);
		PlayerDifficultyState resolvedState = currentKey.withDifficultyLevel(difficultyLevel);
		boolean shouldSend = force || previous == null || previous.difficultyLevel() != difficultyLevel;

		if (shouldSend && ServerPlayNetworking.canSend(player, WorldDifficultyPayload.TYPE)) {
			ServerPlayNetworking.send(player, new WorldDifficultyPayload(difficultyLevel));
		}
		lastStateByPlayer.put(playerId, resolvedState);
	}

	private static PlayerDifficultyState captureStateKey(ServerPlayer player) {
		if (player == null) {
			return null;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		BlockPos pos = player.blockPosition();
		int chunkX = pos.getX() >> 4;
		int chunkZ = pos.getZ() >> 4;
		if (!MadokuChunkManager.isChunkLoaded(level, chunkX, chunkZ)) {
			return null;
		}
		int timeAdjustment = MadokuRegionalDifficultyManager.resolveCurrentTimeAdjustment(level);
		String levelId = level.dimension().identifier().toString();
		long chunkPos = packChunk(chunkX, chunkZ);
		return new PlayerDifficultyState(levelId, chunkPos, timeAdjustment, 1);
	}

	private static long packChunk(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
	}

	private record PlayerDifficultyState(
		String levelId,
		long chunkPos,
		int timeAdjustment,
		int difficultyLevel
	) {
		private boolean sameKey(PlayerDifficultyState other) {
			if (other == null) {
				return false;
			}
			return chunkPos == other.chunkPos
				&& timeAdjustment == other.timeAdjustment
				&& levelId.equals(other.levelId);
		}

		private PlayerDifficultyState withDifficultyLevel(int level) {
			return new PlayerDifficultyState(levelId, chunkPos, timeAdjustment, Math.max(1, level));
		}
	}
}




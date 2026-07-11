package madoku.craft.api.data;

import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Native per-chunk state for blocks placed by players. */
public final class MadokuChunkDataManager {
	private static final String DATA_SYSTEM_ID = "player-placed-blocks";
	private static final String FIELD_PACKED_POSITIONS = "packed-positions";

	private static final PlayerPlacedBlockData DATA = new PlayerPlacedBlockData();
	private static final Set<ChunkRefKey> PERSISTED_CHUNK_KEYS = new LinkedHashSet<>();
	private static volatile boolean dirty;
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;
	private static volatile boolean eventHandlersRegistered;

	private MadokuChunkDataManager() { }

	public static void initialize() {
		if (!eventHandlersRegistered) {
			PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
				if (world instanceof ServerLevel level) {
					removePlayerPlacedBlock(level, pos);
				}
			});
			eventHandlersRegistered = true;
		}
	}

	public static void reset() {
		DATA.clear();
		PERSISTED_CHUNK_KEYS.clear();
		dirty = false;
		lastAutosaveBucket = Long.MIN_VALUE;
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) return;

		Map<DataWorldChunkManager.ChunkDataKey, JsonObject> persistedData =
			DataWorldChunkManager.getAllChunkSystemData(DATA_SYSTEM_ID);
		DATA.loadPersistedChunkData(persistedData);
		PERSISTED_CHUNK_KEYS.clear();
		PERSISTED_CHUNK_KEYS.addAll(DATA.collectCurrentChunkKeys());
		dirty = false;
		lastAutosaveBucket = Math.floorDiv(
			madoku.craft.api.time.MadokuTimeManager.getGameplayTicks(),
			DataWorldChunkManager.getAutoSaveIntervalTicks());
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) return;
		long bucket = Math.floorDiv(
			madoku.craft.api.time.MadokuTimeManager.getGameplayTicks(),
			DataWorldChunkManager.getAutoSaveIntervalTicks());
		if (bucket == lastAutosaveBucket) return;
		lastAutosaveBucket = bucket;
		if (dirty) savePersistedData(server);
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) return;
		Set<ChunkRefKey> currentChunkKeys = DATA.collectCurrentChunkKeys();
		Set<ChunkRefKey> dirtyChunkKeys = DATA.collectDirtyChunkKeys();
		Set<ChunkRefKey> staleChunkKeys = new LinkedHashSet<>(PERSISTED_CHUNK_KEYS);
		staleChunkKeys.removeAll(currentChunkKeys);
		for (ChunkRefKey chunkKey : dirtyChunkKeys) {
			if (!currentChunkKeys.contains(chunkKey)) continue;
			JsonObject chunkData = DATA.createChunkPersistedData(chunkKey);
			if (chunkData == null) continue;
			DataWorldChunkManager.setChunkSystemData(
				new DataWorldChunkManager.ChunkDataKey(chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ()),
				DATA_SYSTEM_ID,
				chunkData);
		}
		for (ChunkRefKey chunkKey : staleChunkKeys) {
			DataWorldChunkManager.removeChunkSystemData(
				new DataWorldChunkManager.ChunkDataKey(chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ()),
				DATA_SYSTEM_ID);
		}
		PERSISTED_CHUNK_KEYS.clear();
		PERSISTED_CHUNK_KEYS.addAll(currentChunkKeys);
		DATA.clearDirtyChunkKeys();
		dirty = false;
	}

	public static void recordPlayerPlacedBlock(ServerLevel level, BlockPos pos) {
		if (level != null && pos != null && DATA.record(level, pos)) dirty = true;
	}

	public static boolean isPlayerPlacedBlock(ServerLevel level, BlockPos pos) {
		return level != null && pos != null && DATA.contains(level, pos);
	}

	public static void removePlayerPlacedBlock(ServerLevel level, BlockPos pos) {
		if (level != null && pos != null && DATA.remove(level, pos)) dirty = true;
	}

	private static String levelId(ServerLevel level) {
		return DataWorldChunkManager.dimensionId(level);
	}

	private static int packLocalBlockPos(BlockPos pos) {
		int localX = pos.getX() & 15;
		int localZ = pos.getZ() & 15;
		int localY = pos.getY() & 0xFFFF;
		return (localY << 8) | (localX << 4) | localZ;
	}

	private static String getString(JsonObject object, String key, String fallback) {
		try { return object != null && object.has(key) ? object.get(key).getAsString() : fallback; }
		catch (RuntimeException exception) { return fallback; }
	}

	private static final class PlayerPlacedBlockData {
		private final Map<ChunkRefKey, Set<Integer>> positionsByChunk = new HashMap<>();
		private final Set<ChunkRefKey> dirtyChunkKeys = new LinkedHashSet<>();

		private void clear() {
			positionsByChunk.clear();
			dirtyChunkKeys.clear();
		}

		private boolean record(ServerLevel level, BlockPos pos) {
			String levelId = levelId(level);
			if (levelId.isBlank()) return false;
			ChunkRefKey chunkKey = new ChunkRefKey(levelId, pos.getX() >> 4, pos.getZ() >> 4);
			boolean added = positionsByChunk.computeIfAbsent(chunkKey, ignored -> new HashSet<>()).add(packLocalBlockPos(pos));
			if (added) dirtyChunkKeys.add(chunkKey);
			return added;
		}

		private boolean contains(ServerLevel level, BlockPos pos) {
			ChunkRefKey chunkKey = new ChunkRefKey(levelId(level), pos.getX() >> 4, pos.getZ() >> 4);
			Set<Integer> positions = positionsByChunk.get(chunkKey);
			return positions != null && positions.contains(packLocalBlockPos(pos));
		}

		private boolean remove(ServerLevel level, BlockPos pos) {
			ChunkRefKey chunkKey = new ChunkRefKey(levelId(level), pos.getX() >> 4, pos.getZ() >> 4);
			Set<Integer> positions = positionsByChunk.get(chunkKey);
			if (positions == null || !positions.remove(packLocalBlockPos(pos))) return false;
			if (positions.isEmpty()) positionsByChunk.remove(chunkKey);
			dirtyChunkKeys.add(chunkKey);
			return true;
		}

		private Set<ChunkRefKey> collectCurrentChunkKeys() { return new LinkedHashSet<>(positionsByChunk.keySet()); }
		private Set<ChunkRefKey> collectDirtyChunkKeys() { return new LinkedHashSet<>(dirtyChunkKeys); }
		private void clearDirtyChunkKeys() { dirtyChunkKeys.clear(); }

		private JsonObject createChunkPersistedData(ChunkRefKey chunkKey) {
			Set<Integer> positions = positionsByChunk.get(chunkKey);
			if (positions == null || positions.isEmpty()) return null;
			return JSONFormatManager.object().put(FIELD_PACKED_POSITIONS, encode(positions)).build();
		}

		private boolean applyChunkPersistedData(JsonObject source, ChunkRefKey chunkKey) {
			Set<Integer> positions = decode(source);
			if (chunkKey == null || positions.isEmpty()) return false;
			positionsByChunk.put(chunkKey, positions);
			return true;
		}

		private String encode(Set<Integer> positions) {
			List<Integer> sorted = new ArrayList<>(positions);
			sorted.sort(Comparator.naturalOrder());
			byte[] packed = new byte[sorted.size() * 3];
			int offset = 0;
			for (Integer position : sorted) {
				if (position == null) continue;
				int value = position & 0xFFFFFF;
				packed[offset++] = (byte) (value >>> 16);
				packed[offset++] = (byte) (value >>> 8);
				packed[offset++] = (byte) value;
			}
			return Base64.getEncoder().encodeToString(packed);
		}

		private Set<Integer> decode(JsonObject source) {
			Set<Integer> positions = new HashSet<>();
			String encoded = getString(source, FIELD_PACKED_POSITIONS, "");
			if (encoded.isBlank()) return positions;
			try {
				byte[] packed = Base64.getDecoder().decode(encoded);
				for (int index = 0; index + 2 < packed.length; index += 3) {
					positions.add(((packed[index] & 0xFF) << 16)
						| ((packed[index + 1] & 0xFF) << 8)
						| (packed[index + 2] & 0xFF));
				}
			} catch (IllegalArgumentException ignored) { }
			return positions;
		}

		private void loadPersistedChunkData(Map<DataWorldChunkManager.ChunkDataKey, JsonObject> persistedData) {
			clear();
			if (persistedData == null) return;
			for (Map.Entry<DataWorldChunkManager.ChunkDataKey, JsonObject> entry : persistedData.entrySet()) {
				DataWorldChunkManager.ChunkDataKey dataKey = entry.getKey();
				if (dataKey == null || dataKey.dimensionId().isBlank()) continue;
				applyChunkPersistedData(entry.getValue(), new ChunkRefKey(dataKey.dimensionId(), dataKey.chunkX(), dataKey.chunkZ()));
			}
		}
	}

	private record ChunkRefKey(String levelId, int chunkX, int chunkZ) { }
}

package madoku.craft.core.data;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import madoku.craft.core.chunk.MadokuChunkManager;
import madoku.craft.core.json.JSONFormatManager;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Native per-chunk state for blocks placed by players. */
public final class MadokuChunkDataManager {
	private static final String DATA_SYSTEM_ID = "player-placed-blocks";
	private static final String FIELD_PACKED_POSITIONS = "packed-positions";

	private static final PlayerPlacedBlockData DATA = new PlayerPlacedBlockData();
	private static final Set<ChunkRefKey> LOADED_CHUNKS = new LinkedHashSet<>();
	private static final Set<PendingRemoval> PENDING_REMOVALS = new LinkedHashSet<>();
	private static volatile boolean dirty;
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;
	private static volatile boolean eventHandlersRegistered;
	private static final MadokuChunkManager.ChunkLifecycleListener CHUNK_LISTENER = new MadokuChunkManager.ChunkLifecycleListener() {
		@Override
		public void onChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
			// Player-placed data is loaded on first query.
		}

		@Override
		public void onChunkUnloaded(ServerLevel level, int chunkX, int chunkZ) {
			releaseChunk(level, chunkX, chunkZ);
		}
	};

	private MadokuChunkDataManager() { }

	public static void initialize() {
		MadokuChunkManager.registerChunkLifecycleListener(CHUNK_LISTENER);
		if (!eventHandlersRegistered) {
			PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
				if (world instanceof ServerLevel level) {
					PENDING_REMOVALS.add(new PendingRemoval(level, pos == null ? null : pos.immutable()));
				}
			});
			ServerTickEvents.END_SERVER_TICK.register(server -> flushPendingRemovals());
			eventHandlersRegistered = true;
		}
	}

	public static void reset() {
		DATA.clear();
		LOADED_CHUNKS.clear();
		PENDING_REMOVALS.clear();
		dirty = false;
		lastAutosaveBucket = Long.MIN_VALUE;
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) return;

		DATA.clear();
		LOADED_CHUNKS.clear();
		PENDING_REMOVALS.clear();
		dirty = false;
		lastAutosaveBucket = Math.floorDiv(
			madoku.craft.core.time.MadokuTimeManager.getGameplayTicks(),
			DataWorldChunkManager.getAutoSaveIntervalTicks());
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) return;
		long bucket = Math.floorDiv(
			madoku.craft.core.time.MadokuTimeManager.getGameplayTicks(),
			DataWorldChunkManager.getAutoSaveIntervalTicks());
		if (bucket == lastAutosaveBucket) return;
		lastAutosaveBucket = bucket;
		if (dirty) savePersistedData(server);
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) return;
		Set<ChunkRefKey> dirtyChunkKeys = DATA.collectDirtyChunkKeys();
		for (ChunkRefKey chunkKey : dirtyChunkKeys) {
			JsonObject chunkData = DATA.createChunkPersistedData(chunkKey);
			DataWorldChunkManager.ChunkDataKey dataKey = new DataWorldChunkManager.ChunkDataKey(
				chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ());
			if (chunkData == null) {
				DataWorldChunkManager.removeChunkSystemData(dataKey, DATA_SYSTEM_ID);
			} else {
				DataWorldChunkManager.setChunkSystemData(dataKey, DATA_SYSTEM_ID, chunkData);
			}
		}
		DATA.clearDirtyChunkKeys();
		dirty = false;
	}

	public static void recordPlayerPlacedBlock(ServerLevel level, BlockPos pos) {
		ensureChunkLoaded(level, pos);
		if (level != null && pos != null && DATA.record(level, pos)) dirty = true;
	}

	public static boolean isPlayerPlacedBlock(ServerLevel level, BlockPos pos) {
		ensureChunkLoaded(level, pos);
		return level != null && pos != null && DATA.contains(level, pos);
	}

	public static void removePlayerPlacedBlock(ServerLevel level, BlockPos pos) {
		ensureChunkLoaded(level, pos);
		if (level != null && pos != null && DATA.remove(level, pos)) dirty = true;
	}

	private static void flushPendingRemovals() {
		if (PENDING_REMOVALS.isEmpty()) {
			return;
		}
		Set<PendingRemoval> pending = new LinkedHashSet<>(PENDING_REMOVALS);
		PENDING_REMOVALS.removeAll(pending);
		for (PendingRemoval removal : pending) {
			if (removal != null && removal.level() != null && removal.pos() != null) {
				removePlayerPlacedBlock(removal.level(), removal.pos());
			}
		}
	}

	private static void ensureChunkLoaded(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) return;
		ChunkRefKey chunkKey = new ChunkRefKey(levelId(level), pos.getX() >> 4, pos.getZ() >> 4);
		if (!LOADED_CHUNKS.add(chunkKey)) return;
		JsonObject data = DataWorldChunkManager.getChunkSystemData(
			level,
			chunkKey.chunkX(),
			chunkKey.chunkZ(),
			DATA_SYSTEM_ID);
		DATA.applyChunkPersistedData(data, chunkKey);
	}

	private static void releaseChunk(ServerLevel level, int chunkX, int chunkZ) {
		if (level == null) return;
		ChunkRefKey chunkKey = new ChunkRefKey(levelId(level), chunkX, chunkZ);
		if (DATA.isDirty(chunkKey)) {
			JsonObject chunkData = DATA.createChunkPersistedData(chunkKey);
			DataWorldChunkManager.ChunkDataKey dataKey = new DataWorldChunkManager.ChunkDataKey(
				chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ());
			if (chunkData == null) {
				DataWorldChunkManager.removeChunkSystemData(dataKey, DATA_SYSTEM_ID);
			} else {
				DataWorldChunkManager.setChunkSystemData(dataKey, DATA_SYSTEM_ID, chunkData);
			}
			DATA.clearDirtyChunkKey(chunkKey);
			dirty = DATA.hasDirtyChunkKeys();
		}
		DATA.evictChunk(chunkKey);
		LOADED_CHUNKS.remove(chunkKey);
		DataWorldChunkManager.releaseChunk(level, chunkX, chunkZ);
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
		private final Map<ChunkRefKey, IntOpenHashSet> positionsByChunk = new HashMap<>();
		private final Set<ChunkRefKey> dirtyChunkKeys = new LinkedHashSet<>();

		private void clear() {
			positionsByChunk.clear();
			dirtyChunkKeys.clear();
		}

		private boolean record(ServerLevel level, BlockPos pos) {
			String levelId = levelId(level);
			if (levelId.isBlank()) return false;
			ChunkRefKey chunkKey = new ChunkRefKey(levelId, pos.getX() >> 4, pos.getZ() >> 4);
			boolean added = positionsByChunk.computeIfAbsent(chunkKey, ignored -> new IntOpenHashSet()).add(packLocalBlockPos(pos));
			if (added) dirtyChunkKeys.add(chunkKey);
			return added;
		}

		private boolean contains(ServerLevel level, BlockPos pos) {
			ChunkRefKey chunkKey = new ChunkRefKey(levelId(level), pos.getX() >> 4, pos.getZ() >> 4);
			IntOpenHashSet positions = positionsByChunk.get(chunkKey);
			return positions != null && positions.contains(packLocalBlockPos(pos));
		}

		private boolean remove(ServerLevel level, BlockPos pos) {
			ChunkRefKey chunkKey = new ChunkRefKey(levelId(level), pos.getX() >> 4, pos.getZ() >> 4);
			IntOpenHashSet positions = positionsByChunk.get(chunkKey);
			if (positions == null || !positions.remove(packLocalBlockPos(pos))) return false;
			if (positions.isEmpty()) positionsByChunk.remove(chunkKey);
			dirtyChunkKeys.add(chunkKey);
			return true;
		}

		private Set<ChunkRefKey> collectDirtyChunkKeys() { return new LinkedHashSet<>(dirtyChunkKeys); }
		private void clearDirtyChunkKeys() { dirtyChunkKeys.clear(); }
		private boolean isDirty(ChunkRefKey chunkKey) { return dirtyChunkKeys.contains(chunkKey); }
		private void clearDirtyChunkKey(ChunkRefKey chunkKey) { dirtyChunkKeys.remove(chunkKey); }
		private boolean hasDirtyChunkKeys() { return !dirtyChunkKeys.isEmpty(); }
		private void evictChunk(ChunkRefKey chunkKey) { positionsByChunk.remove(chunkKey); }

		private JsonObject createChunkPersistedData(ChunkRefKey chunkKey) {
			IntOpenHashSet positions = positionsByChunk.get(chunkKey);
			if (positions == null || positions.isEmpty()) return null;
			return JSONFormatManager.object().put(FIELD_PACKED_POSITIONS, encode(positions)).build();
		}

		private String encode(IntOpenHashSet positions) {
			int[] sorted = positions.toIntArray();
			Arrays.sort(sorted);
			byte[] packed = new byte[sorted.length * 3];
			int offset = 0;
			for (int position : sorted) {
				int value = position & 0xFFFFFF;
				packed[offset++] = (byte) (value >>> 16);
				packed[offset++] = (byte) (value >>> 8);
				packed[offset++] = (byte) value;
			}
			return Base64.getEncoder().encodeToString(packed);
		}

		private IntOpenHashSet decode(JsonObject source) {
			IntOpenHashSet positions = new IntOpenHashSet();
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

		private void applyChunkPersistedData(JsonObject source, ChunkRefKey chunkKey) {
			if (chunkKey == null || source == null || source.isEmpty()) return;
			IntOpenHashSet positions = decode(source);
			if (!positions.isEmpty()) positionsByChunk.put(chunkKey, positions);
		}
	}

	private record ChunkRefKey(String levelId, int chunkX, int chunkZ) { }
	private record PendingRemoval(ServerLevel level, BlockPos pos) { }
}

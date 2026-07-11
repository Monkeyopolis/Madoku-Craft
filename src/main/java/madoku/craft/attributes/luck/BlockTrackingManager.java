package madoku.craft.attributes.luck;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.data.DataWorldChunkManager;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.json.JSONFormatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Base64;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockTrackingManager {
	private static final String DATA_SYSTEM_ID = "luck";
	private static final String FIELD_PACKED_POSITIONS = "packed-positions";
	private static final String FIELD_TRACKED_SINCE_GAMEPLAY_TICK = "tracked-since-gameplay-tick";
	private static final long PLACED_BLOCK_RETENTION_DAYS = 336L;

	private static final BlockTrackingData DATA = new BlockTrackingData();
	private static final Set<ChunkRefKey> PERSISTED_CHUNK_KEYS = new LinkedHashSet<>();
	private static volatile boolean dirty = false;
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;

	private BlockTrackingManager() {
	}

	public static void initialize() {
	}

	public static void reset() {
		DATA.clear();
		PERSISTED_CHUNK_KEYS.clear();
		dirty = false;
		lastAutosaveBucket = Long.MIN_VALUE;
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		DATA.loadPersistedChunkData(DataWorldChunkManager.getAllChunkSystemData(DATA_SYSTEM_ID));
		PERSISTED_CHUNK_KEYS.clear();
		PERSISTED_CHUNK_KEYS.addAll(DATA.collectCurrentChunkKeys());

		if (dirty) {
			savePersistedData(server);
		}

		long autoSaveIntervalTicks = DataWorldChunkManager.getAutoSaveIntervalTicks();
		lastAutosaveBucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), autoSaveIntervalTicks);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		long autoSaveIntervalTicks = DataWorldChunkManager.getAutoSaveIntervalTicks();
		long bucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), autoSaveIntervalTicks);
		if (bucket == lastAutosaveBucket) {
			return;
		}

		lastAutosaveBucket = bucket;
		if (dirty) {
			savePersistedData(server);
		}
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		Set<ChunkRefKey> currentChunkKeys = DATA.collectCurrentChunkKeys();
		Set<ChunkRefKey> dirtyChunkKeys = DATA.collectDirtyChunkKeys();
		Set<ChunkRefKey> staleChunkKeys = new LinkedHashSet<>(PERSISTED_CHUNK_KEYS);
		staleChunkKeys.removeAll(currentChunkKeys);
		int writtenChunkFiles = 0;
		for (ChunkRefKey chunkKey : dirtyChunkKeys) {
			if (!currentChunkKeys.contains(chunkKey)) {
				continue;
			}
			JsonObject chunkData = DATA.createChunkPersistedData(chunkKey);
			if (chunkData == null) {
				continue;
			}
			DataWorldChunkManager.setChunkSystemData(
				new DataWorldChunkManager.ChunkDataKey(chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ()),
				DATA_SYSTEM_ID,
				chunkData
			);
			writtenChunkFiles++;
		}
		for (ChunkRefKey chunkKey : staleChunkKeys) {
			DataWorldChunkManager.removeChunkSystemData(
				new DataWorldChunkManager.ChunkDataKey(chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ()),
				DATA_SYSTEM_ID
			);
		}

		PERSISTED_CHUNK_KEYS.clear();
		PERSISTED_CHUNK_KEYS.addAll(currentChunkKeys);
		DATA.clearDirtyChunkKeys();
		DataWorldChunkManager.savePersistedData(server);
		dirty = false;
		emitLuckDebug(
			"luck.place_save",
			null,
			null,
			"global",
			Map.of(
				"chunk_files", Integer.toString(writtenChunkFiles),
				"persisted_chunks", Integer.toString(currentChunkKeys.size()),
				"deleted_chunks", Integer.toString(staleChunkKeys.size())
			)
		);
	}

	public static void recordPlacedBlock(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return;
		}

		if (DATA.recordPlacedBlock(level, pos)) {
			dirty = true;
		}
	}

	public static boolean isPlayerPlacedBlock(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return false;
		}

		return DATA.isPlayerPlacedBlock(level, pos);
	}

	public static boolean consumePlacedBlock(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return false;
		}

		boolean consumed = DATA.consumePlacedBlock(level, pos);
		if (consumed) {
			dirty = true;
		}
		return consumed;
	}

	private static String levelId(ServerLevel world) {
		String dimensionId = DataWorldChunkManager.dimensionId(world);
		return dimensionId.isBlank() ? "" : dimensionId;
	}

	private static long resolvePlacedBlockRetentionTicks() {
		long dayTicks = Math.max(1L, MadokuTimeManager.getGameplayTicksPerDay());
		try {
			return Math.max(1L, Math.multiplyExact(PLACED_BLOCK_RETENTION_DAYS, dayTicks));
		} catch (ArithmeticException exception) {
			return Long.MAX_VALUE;
		}
	}

	private static int packLocalBlockPos(BlockPos pos) {
		int localX = pos.getX() & 15;
		int localZ = pos.getZ() & 15;
		int localY = pos.getY() & 0xFFFF;
		return (localY << 8) | (localX << 4) | localZ;
	}

	private static long getLong(JsonObject object, String key, long fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsLong();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static String getString(JsonObject object, String key, String fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}
		try {
			String value = element.getAsString();
			return value == null ? fallback : value;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static void emitLuckDebug(String metricId, ServerLevel world, BlockPos pos, String subject, Map<String, String> fields) {
		MadokuLuckManager.emitLuckDebug(metricId, world, pos, subject, fields);
	}

	private static final class BlockTrackingData {
		private final Map<ChunkRefKey, Set<Integer>> placedBlocksByChunk = new HashMap<>();
		private final Set<ChunkRefKey> dirtyChunkKeys = new LinkedHashSet<>();
		private long trackedSinceGameplayTick = -1L;

		private void clear() {
			placedBlocksByChunk.clear();
			dirtyChunkKeys.clear();
			trackedSinceGameplayTick = -1L;
		}

		private boolean recordPlacedBlock(ServerLevel level, BlockPos pos) {
			clearExpiredPlacedBlocks(level);

			String levelId = levelId(level);
			if (levelId.isBlank()) {
				return false;
			}

			ChunkRefKey chunkKey = new ChunkRefKey(levelId, pos.getX() >> 4, pos.getZ() >> 4);
			if (!hasTrackedBlocks()) {
				trackedSinceGameplayTick = MadokuTimeManager.getGameplayTicks();
			}

			Set<Integer> positions = placedBlocksByChunk.computeIfAbsent(chunkKey, ignored -> new HashSet<>());
			boolean added = positions.add(packLocalBlockPos(pos));
			if (!added) {
				return false;
			}

			markChunkDirty(chunkKey);
			dirty = true;
			emitLuckDebug(
				"luck.place_recorded",
				level,
				pos,
				"block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ(),
				Map.of(
					"tracked_count", Integer.toString(totalTrackedBlocks()),
					"tracked_since_gameplay_tick", Long.toString(Math.max(0L, trackedSinceGameplayTick))
				)
			);
			return true;
		}

		private boolean isPlayerPlacedBlock(ServerLevel level, BlockPos pos) {
			clearExpiredPlacedBlocks(level);

			ChunkRefKey chunkKey = new ChunkRefKey(levelId(level), pos.getX() >> 4, pos.getZ() >> 4);
			Set<Integer> positions = placedBlocksByChunk.get(chunkKey);
			boolean tracked = positions != null && positions.contains(packLocalBlockPos(pos));
			emitLuckDebug(
				"luck.place_lookup",
				level,
				pos,
				"block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ(),
				Map.of(
					"tracked", Boolean.toString(tracked),
					"tracked_count", Integer.toString(totalTrackedBlocks())
				)
			);
			return tracked;
		}

		private boolean consumePlacedBlock(ServerLevel level, BlockPos pos) {
			clearExpiredPlacedBlocks(level);

			ChunkRefKey chunkKey = new ChunkRefKey(levelId(level), pos.getX() >> 4, pos.getZ() >> 4);
			Set<Integer> positions = placedBlocksByChunk.get(chunkKey);
			if (positions == null || !positions.remove(packLocalBlockPos(pos))) {
				return false;
			}

			if (positions.isEmpty()) {
				placedBlocksByChunk.remove(chunkKey);
			}
			if (!hasTrackedBlocks()) {
				trackedSinceGameplayTick = -1L;
			}

			markChunkDirty(chunkKey);
			dirty = true;
			emitLuckDebug(
				"luck.place_consumed",
				level,
				pos,
				"block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ(),
				Map.of("remaining_count", Integer.toString(positions.size()))
			);
			return true;
		}

		private boolean clearExpiredPlacedBlocks(ServerLevel level) {
			if (!hasTrackedBlocks()) {
				if (trackedSinceGameplayTick != -1L) {
					trackedSinceGameplayTick = -1L;
				}
				return false;
			}

			if (trackedSinceGameplayTick < 0L) {
				return false;
			}

			long expiresAfterTicks = resolvePlacedBlockRetentionTicks();
			long nowGameplayTick = MadokuTimeManager.getGameplayTicks();
			if (nowGameplayTick - trackedSinceGameplayTick < expiresAfterTicks) {
				return false;
			}

			int expiredChunks = placedBlocksByChunk.size();
			int expiredBlocks = totalTrackedBlocks();
			dirtyChunkKeys.addAll(placedBlocksByChunk.keySet());
			placedBlocksByChunk.clear();
			trackedSinceGameplayTick = -1L;
			dirty = true;
			emitLuckDebug(
				"luck.place_list_expired",
				level,
				null,
				"global",
				Map.of(
					"expired_chunks", Integer.toString(expiredChunks),
					"expired_blocks", Integer.toString(expiredBlocks),
					"expiry_ticks", Long.toString(expiresAfterTicks)
				)
			);
			return true;
		}

		private boolean hasTrackedBlocks() {
			return totalTrackedBlocks() > 0;
		}

		private int totalTrackedBlocks() {
			int count = 0;
			for (Set<Integer> positions : placedBlocksByChunk.values()) {
				if (positions != null) {
					count += positions.size();
				}
			}
			return count;
		}

		private Set<ChunkRefKey> collectCurrentChunkKeys() {
			return new LinkedHashSet<>(placedBlocksByChunk.keySet());
		}

		private Set<ChunkRefKey> collectDirtyChunkKeys() {
			return new LinkedHashSet<>(dirtyChunkKeys);
		}

		private void clearDirtyChunkKeys() {
			dirtyChunkKeys.clear();
		}

		private void markChunkDirty(ChunkRefKey chunkKey) {
			if (chunkKey != null) {
				dirtyChunkKeys.add(chunkKey);
			}
		}

		private JsonObject createChunkPersistedData(ChunkRefKey chunkKey) {
			if (chunkKey == null) {
				return null;
			}

			Set<Integer> positions = placedBlocksByChunk.get(chunkKey);
			if (positions == null || positions.isEmpty()) {
				return null;
			}

			return JSONFormatManager.object()
				.put(FIELD_TRACKED_SINCE_GAMEPLAY_TICK, trackedSinceGameplayTick)
				.put(FIELD_PACKED_POSITIONS, encodePackedPositions(positions))
				.build();
		}

		private boolean applyChunkPersistedData(JsonObject source, ChunkRefKey expectedChunkKey) {
			if (source == null || !source.isJsonObject()) {
				return false;
			}

			ChunkRefKey chunkKey = expectedChunkKey;
			if (chunkKey == null) {
				return false;
			}

			Set<Integer> positions = decodePackedPositions(source);
			if (positions.isEmpty()) {
				return false;
			}
			placedBlocksByChunk.put(chunkKey, positions);

			long chunkTrackedSince = getLong(source, FIELD_TRACKED_SINCE_GAMEPLAY_TICK, trackedSinceGameplayTick);
			if (trackedSinceGameplayTick < 0L || (chunkTrackedSince >= 0L && chunkTrackedSince < trackedSinceGameplayTick)) {
				trackedSinceGameplayTick = chunkTrackedSince;
				markChunkDirty(chunkKey);
			}
			return true;
		}

		private String encodePackedPositions(Set<Integer> positions) {
			if (positions == null || positions.isEmpty()) {
				return "";
			}

			List<Integer> sortedPositions = new ArrayList<>(positions);
			sortedPositions.sort(Comparator.naturalOrder());
			byte[] packed = new byte[sortedPositions.size() * 3];
			int offset = 0;
			for (Integer encodedPosition : sortedPositions) {
				if (encodedPosition == null) {
					continue;
				}
				int value = encodedPosition & 0xFFFFFF;
				packed[offset++] = (byte) ((value >>> 16) & 0xFF);
				packed[offset++] = (byte) ((value >>> 8) & 0xFF);
				packed[offset++] = (byte) (value & 0xFF);
			}
			if (offset != packed.length) {
				byte[] trimmed = new byte[offset];
				System.arraycopy(packed, 0, trimmed, 0, offset);
				packed = trimmed;
			}
			return Base64.getEncoder().encodeToString(packed);
		}

		private Set<Integer> decodePackedPositions(JsonObject source) {
			Set<Integer> positions = new HashSet<>();
			if (source == null) {
				return positions;
			}

			String packedPositions = getString(source, FIELD_PACKED_POSITIONS, "");
			if (!packedPositions.isBlank()) {
				try {
					byte[] packed = Base64.getDecoder().decode(packedPositions);
					for (int index = 0; index + 2 < packed.length; index += 3) {
						int value = ((packed[index] & 0xFF) << 16)
							| ((packed[index + 1] & 0xFF) << 8)
							| (packed[index + 2] & 0xFF);
						positions.add(value);
					}
				} catch (IllegalArgumentException ignored) {
				}
			}
			return positions;
		}

		private void loadPersistedChunkData(Map<DataWorldChunkManager.ChunkDataKey, JsonObject> persistedData) {
			clear();
			if (persistedData == null) {
				return;
			}
			for (Map.Entry<DataWorldChunkManager.ChunkDataKey, JsonObject> entry : persistedData.entrySet()) {
				DataWorldChunkManager.ChunkDataKey dataKey = entry.getKey();
				if (dataKey == null || dataKey.dimensionId().isBlank()) {
					continue;
				}
				ChunkRefKey chunkKey = new ChunkRefKey(dataKey.dimensionId(), dataKey.chunkX(), dataKey.chunkZ());
				JsonObject source = entry.getValue() == null ? new JsonObject() : entry.getValue().deepCopy();
				applyChunkPersistedData(source, chunkKey);
			}
			if (!hasTrackedBlocks()) {
				trackedSinceGameplayTick = -1L;
			}
		}

	}

	private record ChunkRefKey(String levelId, int chunkX, int chunkZ) {
	}
}

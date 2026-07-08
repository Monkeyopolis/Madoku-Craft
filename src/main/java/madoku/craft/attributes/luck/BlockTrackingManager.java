package madoku.craft.attributes.luck;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.config.JsonFormatBuilder;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.data.DataManagerSystem;
import madoku.craft.scheduler.SchedulerManagerSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockTrackingManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(BlockTrackingManager.class);
	private static final String DATA_FOLDER_NAME = "madoku-craft-attributes";
	private static final String DATA_FILE_NAME = "madoku-luck";
	private static final String CHUNK_DATA_FOLDER_NAME = "luck-chunks";
	private static final String FIELD_CHUNKS = "chunks";
	private static final String FIELD_CHUNK_X = "chunk-x";
	private static final String FIELD_CHUNK_Z = "chunk-z";
	private static final String FIELD_PACKED_POSITIONS = "packed-positions";
	private static final String FIELD_LEVEL_ID = "level-id";
	private static final String FIELD_TRACKED_SINCE_GAMEPLAY_TICK = "tracked-since-gameplay-tick";
	private static final long PLACED_BLOCK_RETENTION_DAYS = 336L;

	private static final BlockTrackingData DATA = new BlockTrackingData();
	private static final Set<ChunkRefKey> PERSISTED_CHUNK_KEYS = new LinkedHashSet<>();
	private static volatile long PERSISTED_TRACKED_SINCE_GAMEPLAY_TICK = -1L;
	private static volatile boolean dirty = false;
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;

	private BlockTrackingManager() {
	}

	public static void initialize() {
	}

	public static void reset() {
		DATA.clear();
		PERSISTED_CHUNK_KEYS.clear();
		PERSISTED_TRACKED_SINCE_GAMEPLAY_TICK = -1L;
		dirty = false;
		lastAutosaveBucket = Long.MIN_VALUE;
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		JsonObject indexData = DataManagerSystem.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultIndexData());
		DATA.loadPersistedIndexData(server, indexData);
		PERSISTED_CHUNK_KEYS.clear();
		PERSISTED_CHUNK_KEYS.addAll(DATA.collectCurrentChunkKeys());
		PERSISTED_TRACKED_SINCE_GAMEPLAY_TICK = DATA.getTrackedSinceGameplayTick();

		if (dirty) {
			savePersistedData(server);
		}

		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		lastAutosaveBucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), autoSaveIntervalTicks);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
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
		long currentTrackedSince = DATA.getTrackedSinceGameplayTick();
		boolean indexChanged = !currentChunkKeys.equals(PERSISTED_CHUNK_KEYS)
			|| currentTrackedSince != PERSISTED_TRACKED_SINCE_GAMEPLAY_TICK;

		int writtenChunkFiles = 0;
		for (ChunkRefKey chunkKey : dirtyChunkKeys) {
			if (!currentChunkKeys.contains(chunkKey)) {
				continue;
			}
			JsonObject chunkData = DATA.createChunkPersistedData(chunkKey);
			if (chunkData == null) {
				continue;
			}
			writeChunkPersistedData(server, chunkKey, chunkData);
			writtenChunkFiles++;
		}
		for (ChunkRefKey chunkKey : staleChunkKeys) {
			deleteChunkPersistedData(server, chunkKey);
		}

		PERSISTED_CHUNK_KEYS.clear();
		PERSISTED_CHUNK_KEYS.addAll(currentChunkKeys);
		PERSISTED_TRACKED_SINCE_GAMEPLAY_TICK = currentTrackedSince;
		DATA.clearDirtyChunkKeys();
		if (indexChanged) {
			DataManagerSystem.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, DATA.createIndexPersistedData());
		}
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

	private static JsonObject createDefaultIndexData() {
		return JsonFormatBuilder.object()
			.put(FIELD_TRACKED_SINCE_GAMEPLAY_TICK, -1L)
			.put(FIELD_CHUNKS, new JsonArray())
			.build();
	}

	private static String levelId(ServerLevel world) {
		if (world == null) {
			return "";
		}
		return SchedulerManagerSystem.normalizeLevelIdentifier(world.dimension().toString());
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

	private static int getInt(JsonObject object, String key, int fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsInt();
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

	private static Path resolveWorldRootDirectory(MinecraftServer server) {
		return JsonManagerSystem.getOrCreateWorldSystemDirectory(server, DATA_FOLDER_NAME);
	}

	private static Path resolveChunkPersistedDataPath(MinecraftServer server, ChunkRefKey chunkKey) {
		Path chunkRoot = resolveWorldRootDirectory(server).resolve(CHUNK_DATA_FOLDER_NAME);
		Path levelDirectory = chunkRoot.resolve(normalizePathPart(chunkKey.levelId(), "level id"));
		return levelDirectory.resolve(chunkPersistedDataFileName(chunkKey));
	}

	private static Path resolveChunkPersistedDataFile(MinecraftServer server, ChunkRefKey chunkKey) {
		Path file = resolveChunkPersistedDataPath(server, chunkKey);
		try {
			Files.createDirectories(file.getParent());
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to create luck chunk directory: " + file.getParent(), exception);
		}
		return file;
	}

	private static void writeChunkPersistedData(MinecraftServer server, ChunkRefKey chunkKey, JsonObject data) {
		if (server == null || chunkKey == null || data == null) {
			return;
		}

		Path file = resolveChunkPersistedDataFile(server, chunkKey);
		try {
			JsonStaticSystem.writeManagedDocument(file, data, new JsonObject());
		} catch (IOException exception) {
			LOGGER.error("Failed to write luck chunk data at {}.", file, exception);
		}
	}

	private static void deleteChunkPersistedData(MinecraftServer server, ChunkRefKey chunkKey) {
		if (server == null || chunkKey == null) {
			return;
		}

		Path file = resolveChunkPersistedDataPath(server, chunkKey);
		try {
			Files.deleteIfExists(file);
		} catch (IOException exception) {
			LOGGER.error("Failed to delete luck chunk data at {}.", file, exception);
		}
	}

	private static void emitLuckDebug(String metricId, ServerLevel world, BlockPos pos, String subject, Map<String, String> fields) {
		MadokuLuckManager.emitLuckDebug(metricId, world, pos, subject, fields);
	}

	private static String chunkPersistedDataFileName(ChunkRefKey chunkKey) {
		return "chunk_" + chunkKey.chunkX() + "_" + chunkKey.chunkZ() + ".json";
	}

	private static String normalizePathPart(String value, String label) {
		String normalized = value == null ? "" : value.trim();
		StringBuilder builder = new StringBuilder(normalized.length() + 8);
		char previous = 0;
		for (int index = 0; index < normalized.length(); index++) {
			char current = normalized.charAt(index);
			if (Character.isUpperCase(current) && index > 0 && (Character.isLowerCase(previous) || Character.isDigit(previous))) {
				builder.append('-');
			}
			builder.append(Character.toLowerCase(current));
			previous = current;
		}
		normalized = builder.toString();
		normalized = normalized.replace(' ', '-').replace('_', '-').replace('\\', '-').replace('/', '-').replace(':', '-');
		while (normalized.contains("--")) {
			normalized = normalized.replace("--", "-");
		}
		while (normalized.startsWith("-")) {
			normalized = normalized.substring(1);
		}
		while (normalized.endsWith("-")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		if (normalized.isBlank()) {
			throw new IllegalArgumentException(label + " must not be blank.");
		}
		return normalized;
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

		private long getTrackedSinceGameplayTick() {
			return trackedSinceGameplayTick;
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

		private JsonObject createIndexPersistedData() {
			List<ChunkRefKey> chunkKeys = new ArrayList<>(placedBlocksByChunk.keySet());
			chunkKeys.sort(Comparator.comparing(ChunkRefKey::levelId).thenComparingInt(ChunkRefKey::chunkX).thenComparingInt(ChunkRefKey::chunkZ));

			return JsonFormatBuilder.object()
				.put(FIELD_TRACKED_SINCE_GAMEPLAY_TICK, trackedSinceGameplayTick)
				.put(FIELD_CHUNKS, buildChunkDescriptorArray(chunkKeys))
				.build();
		}

		private JsonObject createChunkPersistedData(ChunkRefKey chunkKey) {
			if (chunkKey == null) {
				return null;
			}

			Set<Integer> positions = placedBlocksByChunk.get(chunkKey);
			if (positions == null || positions.isEmpty()) {
				return null;
			}

			return JsonFormatBuilder.object()
				.put(FIELD_TRACKED_SINCE_GAMEPLAY_TICK, trackedSinceGameplayTick)
				.put(FIELD_PACKED_POSITIONS, encodePackedPositions(positions))
				.build();
		}

		private JsonArray buildChunkDescriptorArray(List<ChunkRefKey> chunkKeys) {
			JsonFormatBuilder.ArrayBuilder chunks = JsonFormatBuilder.array();
			if (chunkKeys == null) {
				return chunks.build();
			}

			for (ChunkRefKey chunkKey : chunkKeys) {
				if (chunkKey == null) {
					continue;
				}
				chunks.object(chunk -> chunk
					.put(FIELD_LEVEL_ID, chunkKey.levelId())
					.put(FIELD_CHUNK_X, chunkKey.chunkX())
					.put(FIELD_CHUNK_Z, chunkKey.chunkZ())
				);
			}
			return chunks.build();
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

		private void loadPersistedIndexData(MinecraftServer server, JsonObject source) {
			clear();
			if (source == null) {
				return;
			}

			trackedSinceGameplayTick = getLong(source, FIELD_TRACKED_SINCE_GAMEPLAY_TICK, -1L);

			JsonArray chunkDescriptors = source.has(FIELD_CHUNKS) && source.get(FIELD_CHUNKS).isJsonArray()
				? source.getAsJsonArray(FIELD_CHUNKS)
				: new JsonArray();
			for (JsonElement element : chunkDescriptors) {
				ChunkRefKey chunkKey = readChunkDescriptor(element);
				if (chunkKey == null) {
					continue;
				}
				loadChunkPersistedData(server, chunkKey);
			}

			if (!hasTrackedBlocks()) {
				trackedSinceGameplayTick = -1L;
			}
		}

		private boolean loadChunkPersistedData(MinecraftServer server, ChunkRefKey chunkKey) {
			if (server == null || chunkKey == null) {
				return false;
			}

			Path file = resolveChunkPersistedDataPath(server, chunkKey);
			if (!Files.isRegularFile(file)) {
				return false;
			}

			try {
				JsonObject source = JsonStaticSystem.readManagedDocument(file).main();
				return applyChunkPersistedData(source, chunkKey);
			} catch (IOException exception) {
				LOGGER.error("Failed to load luck chunk data from {}.", file, exception);
				return false;
			}
		}

		private ChunkRefKey readChunkDescriptor(JsonElement element) {
			if (!(element instanceof JsonObject source)) {
				return null;
			}
			String levelId = getString(source, FIELD_LEVEL_ID, "");
			int chunkX = getInt(source, FIELD_CHUNK_X, Integer.MIN_VALUE);
			int chunkZ = getInt(source, FIELD_CHUNK_Z, Integer.MIN_VALUE);
			if (levelId.isBlank() || chunkX == Integer.MIN_VALUE || chunkZ == Integer.MIN_VALUE) {
				return null;
			}
			return new ChunkRefKey(normalizePathPart(levelId, "level id"), chunkX, chunkZ);
		}
	}

	private record ChunkRefKey(String levelId, int chunkX, int chunkZ) {
	}
}

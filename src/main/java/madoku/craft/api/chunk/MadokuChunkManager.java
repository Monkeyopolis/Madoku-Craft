package madoku.craft.api.chunk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import madoku.craft.config.JsonFormatBuilder;
import madoku.craft.data.DataManagerSystem;
import madoku.craft.scheduler.SchedulerManagerSystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.LinkedHashMap;

public final class MadokuChunkManager {
	private static final String DATA_FOLDER_NAME = "madoku-craft-chunks";
	private static final String DATA_FILE_NAME = "madoku-chunks";
	private static final String FIELD_LEVELS = "levels";
	private static final String FIELD_LEVEL_ID = "level-id";
	private static final String FIELD_CHUNKS = "chunks";
	private static final String FIELD_CHUNK_X = "chunk-x";
	private static final String FIELD_CHUNK_Z = "chunk-z";
	private static final String FIELD_STATUS = "status";

	private static final Map<String, Map<Long, FullChunkStatus>> CHUNK_STATUSES_BY_LEVEL = new LinkedHashMap<>();
	private static final List<ChunkLifecycleListener> CHUNK_LIFECYCLE_LISTENERS = new CopyOnWriteArrayList<>();

	private MadokuChunkManager() {
	}

	public interface ChunkLifecycleListener {
		void onChunkLoaded(ServerLevel level, int chunkX, int chunkZ);

		void onChunkUnloaded(ServerLevel level, int chunkX, int chunkZ);
	}

	public interface ChunkProcessor {
		default boolean acceptsWorld(ServerLevel level) {
			return true;
		}

		default boolean requiresMotionColumns() {
			return true;
		}

		default boolean requiresSurfaceColumns() {
			return false;
		}

		default void beginLoadedChunkDiscovery(ServerLevel level, int chunkX, int chunkZ) {
		}

		default void finishLoadedChunkDiscovery(ServerLevel level, int chunkX, int chunkZ) {
		}

		void discoverLoadedChunk(ServerLevel level, int chunkX, int chunkZ, ChunkDiscoverySnapshot snapshot);

		void processTrackedChunk(ServerLevel level, int chunkX, int chunkZ);
	}

	public static final class ChunkDiscoverySnapshot {
		private String levelId;
		private int chunkX;
		private int chunkZ;
		private final List<ColumnSample> motionColumns;
		private final List<ColumnSample> surfaceColumns;
		private boolean hasMotionColumns;
		private boolean hasSurfaceColumns;
		private int activeColumnIndex;

		private ChunkDiscoverySnapshot(int capacity) {
			int safeCapacity = Math.max(1, capacity);
			List<ColumnSample> motion = new ArrayList<>(safeCapacity);
			List<ColumnSample> surface = new ArrayList<>(safeCapacity);
			for (int i = 0; i < safeCapacity; i++) {
				motion.add(new ColumnSample());
				surface.add(new ColumnSample());
			}
			this.motionColumns = motion;
			this.surfaceColumns = surface;
			this.levelId = "";
			this.activeColumnIndex = -1;
		}

		static ChunkDiscoverySnapshot reusable(int capacity) {
			return new ChunkDiscoverySnapshot(capacity);
		}

		private void begin(String levelId, int chunkX, int chunkZ, boolean needsMotionColumns, boolean needsSurfaceColumns) {
			this.levelId = levelId == null ? "" : levelId;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.hasMotionColumns = needsMotionColumns;
			this.hasSurfaceColumns = needsSurfaceColumns;
			this.activeColumnIndex = -1;
		}

		void beginColumn(String levelId, int chunkX, int chunkZ, int columnIndex, boolean needsMotionColumns, boolean needsSurfaceColumns) {
			begin(levelId, chunkX, chunkZ, needsMotionColumns, needsSurfaceColumns);
			this.activeColumnIndex = columnIndex;
		}

		public String levelId() {
			return levelId;
		}

		public int chunkX() {
			return chunkX;
		}

		public int chunkZ() {
			return chunkZ;
		}

		public List<ColumnSample> motionColumns() {
			if (!hasMotionColumns) {
				return List.of();
			}
			if (activeColumnIndex >= 0) {
				return List.of(motionColumnAt(activeColumnIndex));
			}
			return motionColumns;
		}

		public boolean hasMotionColumns() {
			return hasMotionColumns;
		}

		public boolean hasSurfaceColumns() {
			return hasSurfaceColumns;
		}

		public List<ColumnSample> surfaceColumns() {
			if (!hasSurfaceColumns) {
				return List.of();
			}
			if (activeColumnIndex >= 0) {
				return List.of(surfaceColumnAt(activeColumnIndex));
			}
			return surfaceColumns;
		}

		public int activeColumnIndex() {
			return activeColumnIndex;
		}

		ColumnSample motionColumnAt(int index) {
			return motionColumns.get(index);
		}

		ColumnSample surfaceColumnAt(int index) {
			return surfaceColumns.get(index);
		}
	}

	public static final class ColumnSample {
		private int worldX;
		private int worldZ;
		private final int[] yByDepth = new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		private final long[] posByDepth = new long[3];
		private final BlockState[] stateByDepth = new BlockState[3];

		void reset(int worldX, int worldZ) {
			this.worldX = worldX;
			this.worldZ = worldZ;
			Arrays.fill(yByDepth, Integer.MIN_VALUE);
			Arrays.fill(posByDepth, 0L);
			Arrays.fill(stateByDepth, null);
		}

		void setDepth(int depth, int y, long packedPos, BlockState state) {
			if (depth < 0 || depth >= yByDepth.length) {
				return;
			}
			yByDepth[depth] = y;
			posByDepth[depth] = packedPos;
			stateByDepth[depth] = state;
		}

		void copyFrom(ColumnSample source) {
			if (source == null) {
				reset(0, 0);
				return;
			}
			this.worldX = source.worldX;
			this.worldZ = source.worldZ;
			System.arraycopy(source.yByDepth, 0, this.yByDepth, 0, this.yByDepth.length);
			System.arraycopy(source.posByDepth, 0, this.posByDepth, 0, this.posByDepth.length);
			System.arraycopy(source.stateByDepth, 0, this.stateByDepth, 0, this.stateByDepth.length);
		}

		public int worldX() {
			return worldX;
		}

		public int worldZ() {
			return worldZ;
		}

		public boolean hasDepth(int depth) {
			return depth >= 0 && depth < yByDepth.length && yByDepth[depth] != Integer.MIN_VALUE;
		}

		public int yAtDepth(int depth) {
			return hasDepth(depth) ? yByDepth[depth] : Integer.MIN_VALUE;
		}

		public long posAtDepth(int depth) {
			return hasDepth(depth) ? posByDepth[depth] : 0L;
		}

		public BlockState stateAtDepth(int depth) {
			return hasDepth(depth) ? stateByDepth[depth] : null;
		}
	}

	public static void initialize() {
		ChunkConfigManager.initialize();
		ChunkDiscoveryManager.initialize();
	}

	public static void reset() {
		CHUNK_STATUSES_BY_LEVEL.clear();
		ChunkDiscoveryManager.reset();
		ChunkProcessorManager.reset();
	}

	public static void registerChunkLifecycleListener(ChunkLifecycleListener listener) {
		if (listener == null || CHUNK_LIFECYCLE_LISTENERS.contains(listener)) {
			return;
		}
		CHUNK_LIFECYCLE_LISTENERS.add(listener);
	}

	public static void registerChunkProcessor(String processorId, ChunkProcessor processor) {
		ChunkProcessorManager.registerChunkProcessor(processorId, processor);
	}

	public static void setChunkProcessorActive(String processorId, boolean active) {
		ChunkProcessorManager.setChunkProcessorActive(processorId, active);
	}

	public static void resetChunkProcessor(String processorId) {
		ChunkProcessorManager.resetChunkProcessor(processorId);
	}

	public static void runChunkProcessorProcessingStep(MinecraftServer server, String processorId) {
		ChunkProcessorManager.runChunkProcessorProcessingStep(server, processorId);
	}

	public static void trackChunkForProcessor(String processorId, ServerLevel level, int chunkX, int chunkZ) {
		ChunkProcessorManager.trackChunkForProcessor(processorId, level, chunkX, chunkZ);
	}

	public static void trackChunkForProcessor(String processorId, String levelId, int chunkX, int chunkZ) {
		ChunkProcessorManager.trackChunkForProcessor(processorId, levelId, chunkX, chunkZ);
	}

	public static void untrackChunkForProcessor(String processorId, ServerLevel level, int chunkX, int chunkZ) {
		ChunkProcessorManager.untrackChunkForProcessor(processorId, level, chunkX, chunkZ);
	}

	public static void untrackChunkForProcessor(String processorId, String levelId, int chunkX, int chunkZ) {
		ChunkProcessorManager.untrackChunkForProcessor(processorId, levelId, chunkX, chunkZ);
	}

	public static String normalizeLevelId(ServerLevel level) {
		return levelId(level);
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}
		DataManagerSystem.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		CHUNK_STATUSES_BY_LEVEL.clear();
		ChunkDiscoveryManager.loadPersistedData(server);
	}

	public static void onServerStarted(MinecraftServer server) {
		ChunkDiscoveryManager.onServerStarted(server);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		ChunkDiscoveryManager.autosavePersistedData(server);
	}

	public static void onServerStopping(MinecraftServer server) {
		ChunkDiscoveryManager.onServerStopping(server);
	}

	public static void savePersistedData(MinecraftServer server) {
		ChunkDiscoveryManager.savePersistedData(server);
	}

	public static boolean isChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
		return isChunkAccessible(level, chunkX, chunkZ);
	}

	public static boolean isChunkAccessible(ServerLevel level, int chunkX, int chunkZ) {
		FullChunkStatus status = getStoredChunkStatus(level, chunkX, chunkZ);
		if (status != null) {
			return status.isOrAfter(FullChunkStatus.FULL);
		}

		if (level == null) {
			return false;
		}

		boolean liveLoaded = level.getChunkSource().hasChunk(chunkX, chunkZ);
		if (liveLoaded) {
			putChunkStatus(levelId(level), packChunk(chunkX, chunkZ), FullChunkStatus.FULL);
		}
		return liveLoaded;
	}

	public static boolean isChunkBlockTicking(ServerLevel level, int chunkX, int chunkZ) {
		FullChunkStatus status = getStoredChunkStatus(level, chunkX, chunkZ);
		if (status != null) {
			return status.isOrAfter(FullChunkStatus.BLOCK_TICKING);
		}

		if (level == null || !isChunkAccessible(level, chunkX, chunkZ)) {
			return false;
		}

		boolean ticking = level.getChunkSource().isPositionTicking(packChunk(chunkX, chunkZ));
		if (ticking) {
			putChunkStatus(levelId(level), packChunk(chunkX, chunkZ), FullChunkStatus.BLOCK_TICKING);
		}
		return ticking;
	}

	public static boolean isChunkEntityTicking(ServerLevel level, int chunkX, int chunkZ) {
		FullChunkStatus status = getStoredChunkStatus(level, chunkX, chunkZ);
		if (status != null) {
			return status.isOrAfter(FullChunkStatus.ENTITY_TICKING);
		}

		if (level == null || !isChunkAccessible(level, chunkX, chunkZ)) {
			return false;
		}

		boolean ticking = level.areEntitiesActuallyLoadedAndTicking(ChunkPos.unpack(packChunk(chunkX, chunkZ)));
		if (ticking) {
			putChunkStatus(levelId(level), packChunk(chunkX, chunkZ), FullChunkStatus.ENTITY_TICKING);
		}
		return ticking;
	}

	public static FullChunkStatus getChunkStatus(ServerLevel level, int chunkX, int chunkZ) {
		return getStoredChunkStatus(level, chunkX, chunkZ);
	}

	static List<Long> getLoadedChunkPositions(ServerLevel level) {
		if (level == null) {
			return List.of();
		}

		Map<Long, FullChunkStatus> chunks = CHUNK_STATUSES_BY_LEVEL.get(levelId(level));
		if (chunks == null || chunks.isEmpty()) {
			return List.of();
		}

		return new ArrayList<>(chunks.keySet());
	}

	static void putChunkStatus(String levelId, long packedChunk, FullChunkStatus status) {
		if (levelId == null || levelId.isBlank() || status == null) {
			return;
		}
		Map<Long, FullChunkStatus> chunks = CHUNK_STATUSES_BY_LEVEL.computeIfAbsent(levelId, ignored -> new LinkedHashMap<>());
		FullChunkStatus existing = chunks.get(packedChunk);
		if (existing == status) {
			return;
		}
		chunks.put(packedChunk, status);
	}

	static void removeChunk(String levelId, long packedChunk) {
		if (levelId == null || levelId.isBlank()) {
			return;
		}

		Map<Long, FullChunkStatus> chunks = CHUNK_STATUSES_BY_LEVEL.get(levelId);
		if (chunks == null || chunks.remove(packedChunk) == null) {
			return;
		}
		if (chunks.isEmpty()) {
			CHUNK_STATUSES_BY_LEVEL.remove(levelId);
		}
	}

	static void notifyChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
		for (ChunkLifecycleListener listener : CHUNK_LIFECYCLE_LISTENERS) {
			listener.onChunkLoaded(level, chunkX, chunkZ);
		}
	}

	static void notifyChunkUnloaded(ServerLevel level, int chunkX, int chunkZ) {
		for (ChunkLifecycleListener listener : CHUNK_LIFECYCLE_LISTENERS) {
			listener.onChunkUnloaded(level, chunkX, chunkZ);
		}
	}

	static JsonObject toPersistedData() {
		JsonFormatBuilder.ArrayBuilder levels = JsonFormatBuilder.array();
		for (Map.Entry<String, Map<Long, FullChunkStatus>> levelEntry : CHUNK_STATUSES_BY_LEVEL.entrySet()) {
			String levelId = levelEntry.getKey();
			Map<Long, FullChunkStatus> chunks = levelEntry.getValue();
			if (levelId == null || levelId.isBlank() || chunks == null || chunks.isEmpty()) {
				continue;
			}

			List<Map.Entry<Long, FullChunkStatus>> orderedChunks = new ArrayList<>(chunks.entrySet());
			orderedChunks.sort(Comparator.comparingLong(Map.Entry::getKey));
			JsonFormatBuilder.ArrayBuilder chunkBuilder = JsonFormatBuilder.array();
			for (Map.Entry<Long, FullChunkStatus> chunkEntry : orderedChunks) {
				Long packedChunk = chunkEntry.getKey();
				FullChunkStatus status = chunkEntry.getValue();
				if (packedChunk == null || status == null) {
					continue;
				}

				chunkBuilder.object(chunk -> chunk
					.put(FIELD_CHUNK_X, unpackChunkX(packedChunk))
					.put(FIELD_CHUNK_Z, unpackChunkZ(packedChunk))
					.put(FIELD_STATUS, status.name().toLowerCase()));
			}
			JsonArray chunkArray = chunkBuilder.build();
			if (!chunkArray.isEmpty()) {
				levels.object(level -> level
					.put(FIELD_LEVEL_ID, levelId)
					.put(FIELD_CHUNKS, chunkArray));
			}
		}
		return JsonFormatBuilder.object()
			.put(FIELD_LEVELS, levels.build())
			.build();
	}

	static FullChunkStatus resolveChunkStatus(ServerLevel level, long packedChunk) {
		if (level == null) {
			return null;
		}

		int chunkX = unpackChunkX(packedChunk);
		int chunkZ = unpackChunkZ(packedChunk);
		if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
			return null;
		}
		if (level.areEntitiesActuallyLoadedAndTicking(ChunkPos.unpack(packedChunk))) {
			return FullChunkStatus.ENTITY_TICKING;
		}
		if (level.getChunkSource().isPositionTicking(packedChunk)) {
			return FullChunkStatus.BLOCK_TICKING;
		}
		return FullChunkStatus.FULL;
	}

	static String levelId(ServerLevel level) {
		if (level == null) {
			return "";
		}
		return SchedulerManagerSystem.normalizeLevelIdentifier(level.dimension().toString());
	}

	static ServerLevel resolveLevel(MinecraftServer server, String levelId) {
		if (server == null || levelId == null || levelId.isBlank()) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level != null && levelId.equals(levelId(level))) {
				return level;
			}
		}
		return null;
	}

	static long packChunk(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
	}

	static int unpackChunkX(long packedChunk) {
		return (int) (packedChunk >> 32);
	}

	static int unpackChunkZ(long packedChunk) {
		return (int) packedChunk;
	}

	static boolean isKnownLoadedChunk(String levelId, int chunkX, int chunkZ) {
		if (levelId == null || levelId.isBlank()) {
			return false;
		}
		Map<Long, FullChunkStatus> chunks = CHUNK_STATUSES_BY_LEVEL.get(levelId);
		if (chunks == null || chunks.isEmpty()) {
			return false;
		}
		FullChunkStatus status = chunks.get(packChunk(chunkX, chunkZ));
		return status != null && status.isOrAfter(FullChunkStatus.FULL);
	}

	private static FullChunkStatus getStoredChunkStatus(ServerLevel level, int chunkX, int chunkZ) {
		if (level == null) {
			return null;
		}
		Map<Long, FullChunkStatus> chunks = CHUNK_STATUSES_BY_LEVEL.get(levelId(level));
		if (chunks == null) {
			return null;
		}
		return chunks.get(packChunk(chunkX, chunkZ));
	}

	private static JsonObject createDefaultData() {
		return JsonFormatBuilder.object()
			.array(FIELD_LEVELS, levels -> {
			})
			.build();
	}

	static record ProcessorChunkKey(String levelId, int chunkX, int chunkZ) {
	}
}

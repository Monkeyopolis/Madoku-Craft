package madoku.craft.api.chunk;

import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.metadata.MadokuMetaDataManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import madoku.craft.api.scheduler.MadokuSchedulerManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

public final class MadokuChunkManager {
	private static final String DEBUG_MAIN_SYSTEM = "chunk";
	private static final String DEBUG_SUB_SYSTEM = "chunk-manager";

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
		MadokuMetaDataManager.registerMainSystem(MadokuMetaDataManager.CHUNK);
		MadokuDebugManager.bootstrapMainSystem(MadokuMetaDataManager.CHUNK);
		ChunkConfigManager.initialize();
		ChunkDiscoveryManager.initialize();
		emitChunkDebug("chunk.lifecycle", builder -> builder.subject("initialize"));
	}

	public static void reset() {
		final int previousStatusLevels = CHUNK_STATUSES_BY_LEVEL.size();
		final int previousListeners = CHUNK_LIFECYCLE_LISTENERS.size();
		CHUNK_STATUSES_BY_LEVEL.clear();
		ChunkDiscoveryManager.reset();
		ChunkProcessorManager.reset();
		emitChunkDebug("chunk.lifecycle", builder -> builder
			.subject("reset")
			.field("status-levels", previousStatusLevels)
			.field("listeners", previousListeners));
	}

	public static void registerChunkLifecycleListener(ChunkLifecycleListener listener) {
		if (listener == null || CHUNK_LIFECYCLE_LISTENERS.contains(listener)) {
			return;
		}
		CHUNK_LIFECYCLE_LISTENERS.add(listener);
		emitChunkDebug("chunk.lifecycle", builder -> builder
			.subject("register-listener")
			.field("listeners", CHUNK_LIFECYCLE_LISTENERS.size()));
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
		CHUNK_STATUSES_BY_LEVEL.clear();
		ChunkDiscoveryManager.loadPersistedData(server);
		AtomicInteger loadedLevels = new AtomicInteger();
		server.getAllLevels().forEach(level -> loadedLevels.incrementAndGet());
		emitChunkDebug("chunk.lifecycle", builder -> builder
			.subject("load-persisted-data")
			.field("levels", loadedLevels.get()));
	}

	public static void onServerStarted(MinecraftServer server) {
		emitChunkDebug("chunk.lifecycle", builder -> builder.subject("server-started"));
		ChunkDiscoveryManager.onServerStarted(server);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		emitChunkDebug("chunk.lifecycle", builder -> builder.subject("autosave"));
		ChunkDiscoveryManager.autosavePersistedData(server);
	}

	public static void onServerStopping(MinecraftServer server) {
		emitChunkDebug("chunk.lifecycle", builder -> builder.subject("server-stopping"));
		ChunkDiscoveryManager.onServerStopping(server);
	}

	public static void savePersistedData(MinecraftServer server) {
		emitChunkDebug("chunk.lifecycle", builder -> builder.subject("save-persisted-data"));
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
		if (status != null && status.isOrAfter(FullChunkStatus.BLOCK_TICKING)) {
			return true;
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
			if (status.isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
				return true;
			}
		}

		if (level == null || !isChunkAccessible(level, chunkX, chunkZ)) {
			return false;
		}

		boolean ticking = level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(packChunk(chunkX, chunkZ)));
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
		emitChunkDebug("chunk.chunk-load", builder -> builder
			.subject("loaded")
			.field("level-id", levelId(level))
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ));
		for (ChunkLifecycleListener listener : CHUNK_LIFECYCLE_LISTENERS) {
			listener.onChunkLoaded(level, chunkX, chunkZ);
		}
	}

	static void notifyChunkUnloaded(ServerLevel level, int chunkX, int chunkZ) {
		emitChunkDebug("chunk.chunk-load", builder -> builder
			.subject("unloaded")
			.field("level-id", levelId(level))
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ));
		for (ChunkLifecycleListener listener : CHUNK_LIFECYCLE_LISTENERS) {
			listener.onChunkUnloaded(level, chunkX, chunkZ);
		}
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
		if (level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(packedChunk))) {
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
		return MadokuSchedulerManager.normalizeLevelIdentifier(level.dimension().toString());
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
		return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
	}

	static int unpackChunkX(long packedChunk) {
		return (int) packedChunk;
	}

	static int unpackChunkZ(long packedChunk) {
		return (int) (packedChunk >> 32);
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

	private static void emitChunkDebug(String metricId, Consumer<MadokuDebugManager.EventBuilder> customizer) {
		String entry = MadokuDebugManager.resolveCallerMethodName(1);
		if (!MadokuDebugManager.shouldEmit(DEBUG_MAIN_SYSTEM, DEBUG_SUB_SYSTEM, entry)) {
			return;
		}
		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(metricId, DEBUG_MAIN_SYSTEM, DEBUG_SUB_SYSTEM, entry)
			.side(MadokuDebugManager.Side.SERVER);
		if (customizer != null) {
			customizer.accept(builder);
		}
		builder.log();
	}

	static record ProcessorChunkKey(String levelId, int chunkX, int chunkZ) {
	}
}

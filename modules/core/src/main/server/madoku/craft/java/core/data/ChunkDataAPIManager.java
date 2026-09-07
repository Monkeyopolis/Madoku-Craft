package madoku.craft.java.core.data;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Public contract for player-placed block chunk data. */
public final class ChunkDataAPIManager {
	private static final ChunkDataProvider UNAVAILABLE_PROVIDER = new ChunkDataProvider() { };
	private static volatile ChunkDataProvider provider = UNAVAILABLE_PROVIDER;

	private ChunkDataAPIManager() { }

	public static void registerProvider(ChunkDataProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Chunk data provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static void loadPersistedData(MinecraftServer server) { provider.loadPersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { provider.autosavePersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { provider.savePersistedData(server); }
	public static void recordPlayerPlacedBlock(ServerLevel level, BlockPos pos) { provider.recordPlayerPlacedBlock(level, pos); }
	public static boolean isPlayerPlacedBlock(ServerLevel level, BlockPos pos) { return provider.isPlayerPlacedBlock(level, pos); }
	public static void removePlayerPlacedBlock(ServerLevel level, BlockPos pos) { provider.removePlayerPlacedBlock(level, pos); }
}

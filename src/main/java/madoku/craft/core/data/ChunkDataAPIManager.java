package madoku.craft.core.data;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Public contract for player-placed block chunk data. */
public final class ChunkDataAPIManager {
	private ChunkDataAPIManager() {
	}

	public static void initialize() { MadokuChunkDataManager.initialize(); }
	public static void reset() { MadokuChunkDataManager.reset(); }
	public static void loadPersistedData(MinecraftServer server) { MadokuChunkDataManager.loadPersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { MadokuChunkDataManager.autosavePersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { MadokuChunkDataManager.savePersistedData(server); }
	public static void recordPlayerPlacedBlock(ServerLevel level, BlockPos pos) { MadokuChunkDataManager.recordPlayerPlacedBlock(level, pos); }
	public static boolean isPlayerPlacedBlock(ServerLevel level, BlockPos pos) { return MadokuChunkDataManager.isPlayerPlacedBlock(level, pos); }
	public static void removePlayerPlacedBlock(ServerLevel level, BlockPos pos) { MadokuChunkDataManager.removePlayerPlacedBlock(level, pos); }
}

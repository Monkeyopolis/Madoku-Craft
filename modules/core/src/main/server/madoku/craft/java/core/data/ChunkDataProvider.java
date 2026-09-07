package madoku.craft.java.core.data;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Provider contract for player-placed block chunk data. */
public interface ChunkDataProvider {
	default void initialize() { }
	default void reset() { }
	default void loadPersistedData(MinecraftServer server) { }
	default void autosavePersistedData(MinecraftServer server) { }
	default void savePersistedData(MinecraftServer server) { }
	default void recordPlayerPlacedBlock(ServerLevel level, BlockPos pos) { }
	default boolean isPlayerPlacedBlock(ServerLevel level, BlockPos pos) { return false; }
	default void removePlayerPlacedBlock(ServerLevel level, BlockPos pos) { }
}

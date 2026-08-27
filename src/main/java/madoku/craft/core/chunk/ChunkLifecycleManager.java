package madoku.craft.core.chunk;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

final class ChunkLifecycleManager {
	private static volatile boolean serverStopping;

	private ChunkLifecycleManager() {
	}

	static void initialize() {
		ServerChunkEvents.CHUNK_LOAD.register(ChunkLifecycleManager::onChunkLoad);
		ServerChunkEvents.CHUNK_UNLOAD.register(ChunkLifecycleManager::onChunkUnload);
	}

	static void reset() {
		serverStopping = false;
	}

	static void loadPersistedData(MinecraftServer server) {
		serverStopping = false;
	}

	static void onServerStarted(MinecraftServer server) {
		serverStopping = false;
	}

	static void autosavePersistedData(MinecraftServer server) {
		// Persistence is coordinated by DataSaveCoordinatorManager after subsystem state is written.
	}

	static void onServerStopping(MinecraftServer server) {
		serverStopping = server != null;
	}

	static void savePersistedData(MinecraftServer server) {
		// Persistence is coordinated by DataSaveCoordinatorManager after subsystem state is written.
	}

	private static void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean generated) {
		if (level == null || chunk == null || !ChunkConfigManager.isChunkSystemEnabled()) {
			return;
		}
		MadokuChunkManager.putChunkStatus(
			MadokuChunkManager.levelId(level),
			chunk.getPos().pack(),
			FullChunkStatus.FULL
		);
		MadokuChunkManager.enqueueChunkLoaded(level, chunk.getPos().x(), chunk.getPos().z());
	}

	private static void onChunkUnload(ServerLevel level, LevelChunk chunk) {
		if (level == null || chunk == null || serverStopping) {
			return;
		}
		MadokuChunkManager.removeChunk(MadokuChunkManager.levelId(level), chunk.getPos().pack());
		MadokuChunkManager.enqueueChunkUnloaded(level, chunk.getPos().x(), chunk.getPos().z());
	}
}

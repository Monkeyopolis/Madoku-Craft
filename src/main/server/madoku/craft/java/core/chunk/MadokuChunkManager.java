package madoku.craft.java.core.chunk;

import net.minecraft.server.MinecraftServer;

/** Orchestrates the chunk subsystem through its public API contract. */
public final class MadokuChunkManager {
	private MadokuChunkManager() {
	}

	public static void initialize() { ChunkAPIManager.initialize(); }
	public static void reset() { ChunkAPIManager.reset(); }
	public static void loadPersistedData(MinecraftServer server) { ChunkAPIManager.loadPersistedData(server); }
	public static void onServerStarted(MinecraftServer server) { ChunkAPIManager.onServerStarted(server); }
	public static void onServerTick(MinecraftServer server) { ChunkAPIManager.onServerTick(server); }
	public static void autosavePersistedData(MinecraftServer server) { ChunkAPIManager.autosavePersistedData(server); }
	public static void onServerStopping(MinecraftServer server) { ChunkAPIManager.onServerStopping(server); }
	public static void savePersistedData(MinecraftServer server) { ChunkAPIManager.savePersistedData(server); }
}

package madoku.craft.java.core.data;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;

/** Public API for indexed per-dimension chunk data. */
public final class WorldChunkDataAPIManager {
	private static final WorldChunkDataProvider UNAVAILABLE_PROVIDER = new WorldChunkDataProvider() { };
	private static volatile WorldChunkDataProvider provider = UNAVAILABLE_PROVIDER;

	private WorldChunkDataAPIManager() { }

	public static void registerProvider(WorldChunkDataProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("World chunk data provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static boolean isInitialized() { return provider.isInitialized(); }
	public static void loadPersistedData(MinecraftServer server) { provider.loadPersistedData(server); }
	public static void onServerStarted(MinecraftServer server) { provider.onServerStarted(server); }
	public static void autosavePersistedData(MinecraftServer server) { provider.autosavePersistedData(server); }
	public static void onServerStopping(MinecraftServer server) { provider.onServerStopping(server); }
	public static void savePersistedData(MinecraftServer server) { provider.savePersistedData(server); }
	public static long getAutoSaveMinutes() { return provider.getAutoSaveMinutes(); }
	public static long getAutoSaveIntervalTicks() { return provider.getAutoSaveIntervalTicks(); }
	public static String dimensionId(ServerLevel level) { return provider.dimensionId(level); }
	public static JsonObject getChunkData(ServerLevel level, int chunkX, int chunkZ) { return provider.getChunkData(level, chunkX, chunkZ); }
	public static void setChunkData(ServerLevel level, int chunkX, int chunkZ, JsonObject data) { provider.setChunkData(level, chunkX, chunkZ, data); }
	public static JsonObject getChunkSystemData(ServerLevel level, int chunkX, int chunkZ, String systemId) { return provider.getChunkSystemData(level, chunkX, chunkZ, systemId); }
	public static void setChunkSystemData(ServerLevel level, int chunkX, int chunkZ, String systemId, JsonObject data) { provider.setChunkSystemData(level, chunkX, chunkZ, systemId, data); }
	public static JsonObject getChunkSystemData(WorldChunkDataKey key, String systemId) { return provider.getChunkSystemData(key, systemId); }
	public static void setChunkSystemData(WorldChunkDataKey key, String systemId, JsonObject data) { provider.setChunkSystemData(key, systemId, data); }
	public static void removeChunkSystemData(WorldChunkDataKey key, String systemId) { provider.removeChunkSystemData(key, systemId); }
	public static Map<WorldChunkDataKey, JsonObject> getAllChunkSystemData(String systemId) { return provider.getAllChunkSystemData(systemId); }
}

package madoku.craft.java.core.data;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;

/** Provider contract for indexed per-dimension chunk data. */
public interface WorldChunkDataProvider {
	default void initialize() { }
	default void reset() { }
	default boolean isInitialized() { return false; }
	default void loadPersistedData(MinecraftServer server) { }
	default void onServerStarted(MinecraftServer server) { }
	default void autosavePersistedData(MinecraftServer server) { }
	default void onServerStopping(MinecraftServer server) { }
	default void savePersistedData(MinecraftServer server) { }
	default long getAutoSaveMinutes() { return 0L; }
	default long getAutoSaveIntervalTicks() { return 0L; }
	default String dimensionId(ServerLevel level) { return ""; }
	default JsonObject getChunkData(ServerLevel level, int chunkX, int chunkZ) { return new JsonObject(); }
	default void setChunkData(ServerLevel level, int chunkX, int chunkZ, JsonObject data) { }
	default JsonObject getChunkSystemData(ServerLevel level, int chunkX, int chunkZ, String systemId) { return new JsonObject(); }
	default void setChunkSystemData(ServerLevel level, int chunkX, int chunkZ, String systemId, JsonObject data) { }
	default JsonObject getChunkSystemData(WorldChunkDataKey key, String systemId) { return new JsonObject(); }
	default void setChunkSystemData(WorldChunkDataKey key, String systemId, JsonObject data) { }
	default void removeChunkSystemData(WorldChunkDataKey key, String systemId) { }
	default Map<WorldChunkDataKey, JsonObject> getAllChunkSystemData(String systemId) { return Map.of(); }
}

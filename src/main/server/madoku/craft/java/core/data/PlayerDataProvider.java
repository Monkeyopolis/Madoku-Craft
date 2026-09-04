package madoku.craft.java.core.data;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

/** Provider contract for indexed per-player data. */
public interface PlayerDataProvider {
	default void initialize() { }
	default void reset() { }
	default boolean isInitialized() { return false; }
	default void loadPersistedData(MinecraftServer server) { }
	default void onServerStarted(MinecraftServer server) { }
	default void autosavePersistedData(MinecraftServer server) { }
	default void savePersistedData(MinecraftServer server) { }
	default JsonObject getSystemData(String systemId) { return new JsonObject(); }
	default JsonObject getSystemData(String systemId, String entriesKey, String playerIdKey) { return new JsonObject(); }
	default void setSystemData(String systemId, JsonObject data) { }
	default void setSystemData(String systemId, JsonObject data, String entriesKey, String playerIdKey) { }
	default long getAutoSaveIntervalTicks() { return 0L; }
}

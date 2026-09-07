package madoku.craft.java.core.data;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

/** Provider contract for indexed global world data. */
public interface WorldDataProvider {
	default void initialize() { }
	default void reset() { }
	default boolean isInitialized() { return false; }
	default void loadPersistedData(MinecraftServer server) { }
	default void onServerStarted(MinecraftServer server) { }
	default void autosavePersistedData(MinecraftServer server) { }
	default void savePersistedData(MinecraftServer server) { }
	default JsonObject getSystemData(String systemId) { return new JsonObject(); }
	default void setSystemData(String systemId, JsonObject data) { }
}

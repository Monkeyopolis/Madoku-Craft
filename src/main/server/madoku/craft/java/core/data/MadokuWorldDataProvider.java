package madoku.craft.java.core.data;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

/** Built-in world-data provider backed by vanilla SavedData. */
public final class MadokuWorldDataProvider implements WorldDataProvider {
	@Override public void initialize() { WorldDataRuntimeManager.initialize(); }
	@Override public void reset() { WorldDataRuntimeManager.reset(); }
	@Override public boolean isInitialized() { return WorldDataRuntimeManager.isInitialized(); }
	@Override public void loadPersistedData(MinecraftServer server) { WorldDataRuntimeManager.loadPersistedData(server); }
	@Override public void onServerStarted(MinecraftServer server) { WorldDataRuntimeManager.onServerStarted(server); }
	@Override public void autosavePersistedData(MinecraftServer server) { WorldDataRuntimeManager.autosavePersistedData(server); }
	@Override public void savePersistedData(MinecraftServer server) { WorldDataRuntimeManager.savePersistedData(server); }
	@Override public JsonObject getSystemData(String systemId) { return WorldDataRuntimeManager.getSystemData(systemId); }
	@Override public void setSystemData(String systemId, JsonObject data) { WorldDataRuntimeManager.setSystemData(systemId, data); }
}

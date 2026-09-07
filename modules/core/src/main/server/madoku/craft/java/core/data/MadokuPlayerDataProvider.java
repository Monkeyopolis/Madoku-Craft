package madoku.craft.java.core.data;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Built-in player-data provider backed by native player attachments. */
public final class MadokuPlayerDataProvider implements PlayerDataProvider {
	@Override public void initialize() { PlayerDataRuntimeManager.initialize(); }
	@Override public void reset() { PlayerDataRuntimeManager.reset(); }
	@Override public boolean isInitialized() { return PlayerDataRuntimeManager.isInitialized(); }
	@Override public void loadPersistedData(MinecraftServer server) { PlayerDataRuntimeManager.loadPersistedData(server); }
	@Override public void onServerStarted(MinecraftServer server) { PlayerDataRuntimeManager.onServerStarted(server); }
	@Override public void autosavePersistedData(MinecraftServer server) { PlayerDataRuntimeManager.autosavePersistedData(server); }
	@Override public void savePersistedData(MinecraftServer server) { PlayerDataRuntimeManager.savePersistedData(server); }
	@Override public JsonObject getSystemData(String systemId) { return PlayerDataRuntimeManager.getSystemData(systemId); }
	@Override public JsonObject getSystemData(String systemId, String entriesKey, String playerIdKey) { return PlayerDataRuntimeManager.getSystemData(systemId, entriesKey, playerIdKey); }
	@Override public JsonObject getSystemDataForPlayer(ServerPlayer player, String systemId, String entriesKey, String playerIdKey) { return PlayerDataRuntimeManager.getSystemDataForPlayer(player, systemId, entriesKey, playerIdKey); }
	@Override public void setSystemData(String systemId, JsonObject data) { PlayerDataRuntimeManager.setSystemData(systemId, data); }
	@Override public void setSystemData(String systemId, JsonObject data, String entriesKey, String playerIdKey) { PlayerDataRuntimeManager.setSystemData(systemId, data, entriesKey, playerIdKey); }
	@Override public long getAutoSaveIntervalTicks() { return PlayerDataRuntimeManager.getAutoSaveIntervalTicks(); }
}

package madoku.craft.java.core.data;

import net.minecraft.server.MinecraftServer;

/** Built-in provider backed by the Madoku shared-data implementation. */
public final class MadokuDataProvider implements DataProvider {
	@Override public void initialize() { MadokuDataManager.initialize(); }
	@Override public void reset() { MadokuDataManager.reset(); }
	@Override public boolean isInitialized() { return MadokuDataManager.isInitialized(); }
	@Override public void loadPersistedData(MinecraftServer server) { MadokuDataManager.loadPersistedData(server); }
	@Override public void onServerStarted(MinecraftServer server) { MadokuDataManager.onServerStarted(server); }
	@Override public void autosavePersistedData(MinecraftServer server) { MadokuDataManager.autosavePersistedData(server); }
	@Override public void onServerStopping(MinecraftServer server) { MadokuDataManager.onServerStopping(server); }
	@Override public void savePersistedData(MinecraftServer server) { MadokuDataManager.savePersistedData(server); }
}

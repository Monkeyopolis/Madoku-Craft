package madoku.craft.java.core.data;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

/** Public API for indexed global world data. */
public final class WorldDataAPIManager {
	private static final WorldDataProvider UNAVAILABLE_PROVIDER = new WorldDataProvider() { };
	private static volatile WorldDataProvider provider = UNAVAILABLE_PROVIDER;

	private WorldDataAPIManager() { }

	public static void registerProvider(WorldDataProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("World data provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static boolean isInitialized() { return provider.isInitialized(); }
	public static void loadPersistedData(MinecraftServer server) { provider.loadPersistedData(server); }
	public static void onServerStarted(MinecraftServer server) { provider.onServerStarted(server); }
	public static void autosavePersistedData(MinecraftServer server) { provider.autosavePersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { provider.savePersistedData(server); }
	public static JsonObject getSystemData(String systemId) { return provider.getSystemData(systemId); }
	public static void setSystemData(String systemId, JsonObject data) { provider.setSystemData(systemId, data); }
}

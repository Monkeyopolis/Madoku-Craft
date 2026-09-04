package madoku.craft.java.core.data;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

/** Public API for indexed per-player data. */
public final class PlayerDataAPIManager {
	private static final PlayerDataProvider UNAVAILABLE_PROVIDER = new PlayerDataProvider() { };
	private static volatile PlayerDataProvider provider = UNAVAILABLE_PROVIDER;

	private PlayerDataAPIManager() { }

	public static void registerProvider(PlayerDataProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Player data provider must not be null.");
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
	public static JsonObject getSystemData(String systemId, String entriesKey, String playerIdKey) { return provider.getSystemData(systemId, entriesKey, playerIdKey); }
	public static void setSystemData(String systemId, JsonObject data) { provider.setSystemData(systemId, data); }
	public static void setSystemData(String systemId, JsonObject data, String entriesKey, String playerIdKey) { provider.setSystemData(systemId, data, entriesKey, playerIdKey); }
	public static long getAutoSaveIntervalTicks() { return provider.getAutoSaveIntervalTicks(); }
}

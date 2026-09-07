package madoku.craft.java.core.data;

import net.minecraft.server.MinecraftServer;

/** Public contract for coordinated persisted-state saves. */
public final class DataSaveCoordinatorAPIManager {
	private static final DataSaveCoordinatorProvider UNAVAILABLE_PROVIDER = new DataSaveCoordinatorProvider() { };
	private static volatile DataSaveCoordinatorProvider provider = UNAVAILABLE_PROVIDER;

	private DataSaveCoordinatorAPIManager() { }

	public static void registerProvider(DataSaveCoordinatorProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Data-save coordinator provider must not be null.");
		provider = candidate;
	}

	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static void autosave(MinecraftServer server) { provider.autosave(server); }
	public static void saveAndWait(MinecraftServer server) { provider.saveAndWait(server); }
}

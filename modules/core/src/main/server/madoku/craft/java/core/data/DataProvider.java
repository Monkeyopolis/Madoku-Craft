package madoku.craft.java.core.data;

import net.minecraft.server.MinecraftServer;

/** Provider contract for the shared data subsystem. */
public interface DataProvider {
	default void initialize() { }
	default void reset() { }
	default boolean isInitialized() { return false; }
	default void loadPersistedData(MinecraftServer server) { }
	default void onServerStarted(MinecraftServer server) { }
	default void autosavePersistedData(MinecraftServer server) { }
	default void onServerStopping(MinecraftServer server) { }
	default void savePersistedData(MinecraftServer server) { }
}

package madoku.craft.java.core.sync;

import net.minecraft.server.MinecraftServer;

/** Provider contract for server-to-client synchronization. */
public interface SyncProvider {
	default void initialize() { }
	default void initializeClient() { }
	default void reset() { }
	default boolean isInitialized() { return false; }
	default void onServerStarted(MinecraftServer server) { }
	default void onServerStopping(MinecraftServer server) { }
	default boolean shouldRunWorldSync(MinecraftServer server) { return false; }
}

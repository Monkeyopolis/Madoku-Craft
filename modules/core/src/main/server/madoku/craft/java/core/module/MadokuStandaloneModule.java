package madoku.craft.java.core.module;

import net.minecraft.server.MinecraftServer;

/** Lifecycle contract used by a module's standalone Fabric entrypoint. */
public interface MadokuStandaloneModule {
	void initialize();

	default void reset() {
	}

	default void loadPersistedData(MinecraftServer server) {
	}

	default void onServerStarted(MinecraftServer server) {
	}

	default void onServerStartTick(MinecraftServer server) {
	}

	default void onServerTick(MinecraftServer server) {
	}

	default void autosavePersistedData(MinecraftServer server) {
	}

	default void onServerStopping(MinecraftServer server) {
	}

	default void onServerStopped(MinecraftServer server) {
	}
}

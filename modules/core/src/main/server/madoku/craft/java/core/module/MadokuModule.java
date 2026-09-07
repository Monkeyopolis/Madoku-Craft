package madoku.craft.java.core.module;

import java.util.Collection;
import java.util.List;

import net.minecraft.server.MinecraftServer;

/**
 * The lifecycle contract implemented by a feature module.
 *
 * <p>Modules are registered by the bundle entrypoint. A module may depend on
 * another registered module by id, but feature modules should prefer Core
 * contracts and providers over direct implementation imports.</p>
 */
public interface MadokuModule {
	/**
	 * Stable module id used for dependency resolution and diagnostics.
	 */
	String id();

	/**
	 * Other module ids that must be initialized first.
	 */
	default Collection<String> dependencies() {
		return List.of();
	}

	/**
	 * Initializes server/common behavior and registrations.
	 */
	default void initialize() {
	}

	/**
	 * Initializes client-only behavior and registrations.
	 */
	default void initializeClient() {
	}

	/**
	 * Receives the server-started lifecycle event after common initialization.
	 */
	default void onServerStarted(MinecraftServer server) {
	}

	/**
	 * Receives the server-stopped lifecycle event before the module is discarded.
	 */
	default void onServerStopped(MinecraftServer server) {
	}
}

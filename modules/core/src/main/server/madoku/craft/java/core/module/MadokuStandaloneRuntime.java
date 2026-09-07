package madoku.craft.java.core.module;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Installs the standard server lifecycle for a standalone Madoku module. */
public final class MadokuStandaloneRuntime {
	private MadokuStandaloneRuntime() {
	}

	public static void initialize(MadokuStandaloneModule module) {
		if (module == null) {
			throw new IllegalArgumentException("Standalone module must not be null.");
		}

		module.initialize();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			module.reset();
			module.loadPersistedData(server);
			module.onServerStarted(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(module::onServerStopping);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			module.onServerStopped(server);
			module.reset();
		});
		ServerTickEvents.START_SERVER_TICK.register(module::onServerStartTick);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			module.onServerTick(server);
			module.autosavePersistedData(server);
		});
	}
}

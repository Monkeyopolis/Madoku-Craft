package madoku.craft.java.core;

import madoku.craft.java.core.module.MadokuStandaloneModule;
import madoku.craft.java.core.module.MadokuStandaloneRuntime;
import madoku.craft.java.core.season.SeasonAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.server.MinecraftServer;

/** Fabric entrypoint for the standalone Core jar. */
public final class MadokuCoreInitializer implements ModInitializer, MadokuStandaloneModule {
	@Override
	public void onInitialize() {
		MadokuStandaloneRuntime.initialize(this);
	}

	@Override
	public void initialize() {
		MadokuCoreManager.initialize();
		EntitySleepEvents.ALLOW_RESETTING_TIME.register(TimeAPIManager::shouldAllowResettingTime);
	}

	@Override
	public void reset() {
		MadokuCoreManager.reset();
	}

	@Override
	public void loadPersistedData(MinecraftServer server) {
		MadokuCoreManager.loadPersistedData(server);
	}

	@Override
	public void onServerStarted(MinecraftServer server) {
		MadokuCoreManager.onServerStarted(server);
	}

	@Override
	public void onServerStartTick(MinecraftServer server) {
		TimeAPIManager.refreshSleepTickIncrement(server);
		SeasonAPIManager.onServerStartTick(server);
	}

	@Override
	public void onServerTick(MinecraftServer server) {
		TimeAPIManager.advance(server, TimeAPIManager.getCachedSleepTickIncrement());
		TimeAPIManager.update(server);
		MadokuCoreManager.onServerTick(server);
		SeasonAPIManager.onServerTick(server);
		if (MadokuCoreManager.shouldRunWorldSync(server)) {
			TimeAPIManager.broadcastWorldTimeIfChanged(server);
			SeasonAPIManager.broadcastWorldSeasonIfChanged(server);
			SeasonAPIManager.syncPlayerClimateIfChanged(server);
		}
	}

	@Override
	public void autosavePersistedData(MinecraftServer server) {
		MadokuCoreManager.autosavePersistedData(server);
	}

	@Override
	public void onServerStopping(MinecraftServer server) {
		MadokuCoreManager.onServerStopping(server);
	}
}

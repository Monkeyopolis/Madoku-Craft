package madoku.craft.java.levels;

import madoku.craft.java.core.module.MadokuStandaloneModule;
import madoku.craft.java.core.module.MadokuStandaloneRuntime;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.MinecraftServer;

/** Fabric entrypoint for the standalone Levels jar. */
public final class MadokuLevelsInitializer implements ModInitializer, MadokuStandaloneModule {
	@Override public void onInitialize() { MadokuStandaloneRuntime.initialize(this); }
	@Override public void initialize() { MadokuLevelsManager.initialize(); }
	@Override public void reset() { MadokuLevelsManager.reset(); }
	@Override public void loadPersistedData(MinecraftServer server) { MadokuLevelsManager.loadPersistedData(server); }
	@Override public void onServerTick(MinecraftServer server) { MadokuLevelsManager.flushDirtySyncs(server); }
}

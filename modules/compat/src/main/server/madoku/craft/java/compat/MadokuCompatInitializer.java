package madoku.craft.java.compat;

import madoku.craft.java.core.module.MadokuStandaloneModule;
import madoku.craft.java.core.module.MadokuStandaloneRuntime;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.MinecraftServer;

/** Fabric entrypoint for the optional Madoku compatibility jar. */
public final class MadokuCompatInitializer implements ModInitializer, MadokuStandaloneModule {
	@Override public void onInitialize() { MadokuStandaloneRuntime.initialize(this); }
	@Override public void initialize() { MadokuCompatManager.initialize(); }
	@Override public void onServerStarted(MinecraftServer server) { MadokuCompatManager.onServerStarted(server); }
	@Override public void onServerTick(MinecraftServer server) { MadokuCompatManager.onServerTick(server); }
	@Override public void onServerStopped(MinecraftServer server) { MadokuCompatManager.onServerStopped(); }
}

package madoku.craft.java.items;

import madoku.craft.java.core.module.MadokuStandaloneModule;
import madoku.craft.java.core.module.MadokuStandaloneRuntime;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.MinecraftServer;

/** Fabric entrypoint for the standalone Items jar. */
public final class MadokuItemsInitializer implements ModInitializer, MadokuStandaloneModule {
	@Override public void onInitialize() { MadokuStandaloneRuntime.initialize(this); }
	@Override public void initialize() { MadokuItemsManager.initialize(); }
	@Override public void reset() { MadokuItemsManager.reset(); }
	@Override public void onServerStarted(MinecraftServer server) { MadokuItemsManager.onServerStarted(server); }
	@Override public void onServerTick(MinecraftServer server) { MadokuItemsManager.onServerTick(server); }
}

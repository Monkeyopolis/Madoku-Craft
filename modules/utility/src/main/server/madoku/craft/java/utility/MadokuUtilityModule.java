package madoku.craft.java.utility;

import madoku.craft.java.core.module.MadokuModule;
import madoku.craft.java.utility.smelting.MadokuUtilityManager;
import net.minecraft.server.MinecraftServer;

/** Core lifecycle adapter for the Utility module. */
public final class MadokuUtilityModule implements MadokuModule {
	@Override
	public String id() {
		return "utility";
	}

	@Override
	public void initialize() {
		MadokuUtilityManager.initialize();
	}

	@Override
	public void onServerStarted(MinecraftServer server) {
		MadokuUtilityManager.onServerStarted(server);
	}

	@Override
	public void onServerStopped(MinecraftServer server) {
		MadokuUtilityManager.onServerStopped(server);
	}
}

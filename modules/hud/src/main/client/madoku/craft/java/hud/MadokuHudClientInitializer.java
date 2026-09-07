package madoku.craft.java.hud;

import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint for the standalone HUD jar. */
public final class MadokuHudClientInitializer implements ClientModInitializer {
	@Override public void onInitializeClient() { MadokuHudManager.initialize(); }
}

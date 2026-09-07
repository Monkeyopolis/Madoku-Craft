package madoku.craft.java.levels;

import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint for the standalone Levels jar. */
public final class MadokuLevelsClientInitializer implements ClientModInitializer {
	@Override public void onInitializeClient() { MadokuLevelsClient.initialize(); }
}

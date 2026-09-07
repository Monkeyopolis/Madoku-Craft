package madoku.craft.java.compat;

import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint for the optional Madoku compatibility jar. */
public final class MadokuCompatClientInitializer implements ClientModInitializer {
	@Override public void onInitializeClient() { MadokuCompatClient.initialize(); }
}

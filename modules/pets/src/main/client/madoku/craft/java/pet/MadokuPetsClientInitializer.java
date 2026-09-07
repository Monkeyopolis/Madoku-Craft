package madoku.craft.java.pet;

import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint for the standalone Pets jar. */
public final class MadokuPetsClientInitializer implements ClientModInitializer {
	@Override public void onInitializeClient() { MadokuPetClient.initialize(); }
}

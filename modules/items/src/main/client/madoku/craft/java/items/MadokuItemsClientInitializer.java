package madoku.craft.java.items;

import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint for the standalone Items jar. */
public final class MadokuItemsClientInitializer implements ClientModInitializer {
	@Override public void onInitializeClient() { MadokuItemsClient.initialize(); }
}

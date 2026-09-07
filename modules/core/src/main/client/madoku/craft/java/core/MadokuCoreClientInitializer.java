package madoku.craft.java.core;

import madoku.craft.java.core.season.MadokuSeasonClient;
import madoku.craft.java.core.sync.MadokuSyncClient;
import madoku.craft.java.core.sync.SyncAPIManager;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint for the standalone Core jar. */
public final class MadokuCoreClientInitializer implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SyncAPIManager.initializeClient();
		MadokuSyncClient.initialize();
		MadokuSeasonClient.initialize();
	}
}

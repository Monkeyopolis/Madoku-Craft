package madoku.craft.java;

import madoku.craft.java.compat.MadokuCompatClient;
import madoku.craft.java.farming.MadokuFarmingClient;
import madoku.craft.java.items.MadokuItemsClient;
import madoku.craft.java.levels.MadokuLevelsClient;
import madoku.craft.java.hud.MadokuHudManager;
import madoku.craft.java.core.season.MadokuSeasonClient;
import madoku.craft.java.core.sync.MadokuSyncClient;
import madoku.craft.java.core.sync.SyncAPIManager;
import madoku.craft.java.core.time.MadokuClientTimeManager;
import madoku.craft.java.utility.MadokuUtilityClient;
import madoku.craft.java.pet.MadokuPetClient;
import net.fabricmc.api.ClientModInitializer;

public class MadokuCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MadokuUtilityClient.initialize();
		SyncAPIManager.initializeClient();
		MadokuSyncClient.initialize();
		MadokuClientTimeManager.initialize();
		MadokuSeasonClient.initialize();
		MadokuCompatClient.initialize();
		MadokuItemsClient.initialize();
		MadokuFarmingClient.initialize();
		MadokuHudManager.initialize();
		MadokuPetClient.initialize();
		MadokuLevelsClient.initialize();
	}
}

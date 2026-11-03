package madoku.craft.java;

import madoku.craft.java.attributes.MadokuAttributesClientInitializer;
import madoku.craft.java.compat.MadokuCompatClientInitializer;
import madoku.craft.java.core.MadokuCoreClientInitializer;
import madoku.craft.java.core.module.MadokuClientRuntime;
import madoku.craft.java.farming.MadokuFarmingClientInitializer;
import madoku.craft.java.hud.MadokuHudClientInitializer;
import madoku.craft.java.items.MadokuItemsClientInitializer;
import madoku.craft.java.levels.MadokuLevelsClientInitializer;
import madoku.craft.java.mob.MadokuMobsClientInitializer;
import madoku.craft.java.pet.MadokuPetsClientInitializer;
import madoku.craft.java.quests.MadokuQuestsClientInitializer;
import madoku.craft.java.utility.MadokuUtilityClientInitializer;
import net.fabricmc.api.ClientModInitializer;

public class MadokuCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MadokuClientRuntime.initialize(new MadokuCoreClientInitializer());
		MadokuClientRuntime.initialize(new MadokuUtilityClientInitializer());
		MadokuClientRuntime.initialize(new MadokuCompatClientInitializer());
		MadokuClientRuntime.initialize(new MadokuItemsClientInitializer());
		MadokuClientRuntime.initialize(new MadokuFarmingClientInitializer());
		MadokuClientRuntime.initialize(new MadokuHudClientInitializer());
		MadokuClientRuntime.initialize(new MadokuPetsClientInitializer());
		MadokuClientRuntime.initialize(new MadokuLevelsClientInitializer());
		MadokuClientRuntime.initialize(new MadokuMobsClientInitializer());
		MadokuClientRuntime.initialize(new MadokuAttributesClientInitializer());
		MadokuClientRuntime.initialize(new MadokuQuestsClientInitializer());
	}
}

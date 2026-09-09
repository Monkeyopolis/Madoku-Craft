package madoku.craft.java.compat;

import madoku.craft.java.compat.attributes.MadokuAttributeFeatureAdapters;
import madoku.craft.java.compat.farming.MadokuFarmingFeatureAdapters;
import madoku.craft.java.compat.levels.MadokuLevelsFeatureAdapters;
import madoku.craft.java.compat.loot.MadokuLootFeatureAdapters;
import madoku.craft.java.compat.mobs.MadokuMobFeatureAdapters;
import madoku.craft.java.compat.utility.MadokuUtilityFeatureAdapters;

/** Registers adapters owned by Compat. */
public final class MadokuCompatManager {
	private MadokuCompatManager() { }

	/** Installs feature-to-feature bridges before gameplay begins. */
	public static void initialize() {
		if (MadokuCompatModuleState.hasAll(
			MadokuCompatModuleState.CORE_ID,
			MadokuCompatModuleState.ATTRIBUTES_ID
		)) {
			MadokuAttributeFeatureAdapters.initialize();
		}
		if (MadokuCompatModuleState.hasAll(
			MadokuCompatModuleState.CORE_ID,
			MadokuCompatModuleState.FARMING_ID
		)) {
			MadokuFarmingFeatureAdapters.initialize();
		}
		if (MadokuCompatModuleState.hasAll(
			MadokuCompatModuleState.CORE_ID,
			MadokuCompatModuleState.LEVELS_ID
			)
			&& MadokuCompatModuleState.hasAny(
				MadokuCompatModuleState.ATTRIBUTES_ID,
				MadokuCompatModuleState.PETS_ID
			)) {
			MadokuLevelsFeatureAdapters.initialize();
		}
		if (MadokuCompatModuleState.hasAll(MadokuCompatModuleState.CORE_ID)
			&& MadokuCompatModuleState.hasAny(
				MadokuCompatModuleState.ATTRIBUTES_ID,
				MadokuCompatModuleState.FARMING_ID,
				MadokuCompatModuleState.MOBS_ID,
				MadokuCompatModuleState.ITEMS_ID,
				MadokuCompatModuleState.PETS_ID
			)) {
			MadokuLootFeatureAdapters.initialize();
		}
		if (MadokuCompatModuleState.hasAll(
			MadokuCompatModuleState.CORE_ID,
			MadokuCompatModuleState.MOBS_ID
		)) {
			MadokuMobFeatureAdapters.initialize();
		}
		if (MadokuCompatModuleState.hasAll(
			MadokuCompatModuleState.CORE_ID,
			MadokuCompatModuleState.UTILITY_ID,
			MadokuCompatModuleState.ITEMS_ID
		)) {
			MadokuUtilityFeatureAdapters.initialize();
		}
	}
}

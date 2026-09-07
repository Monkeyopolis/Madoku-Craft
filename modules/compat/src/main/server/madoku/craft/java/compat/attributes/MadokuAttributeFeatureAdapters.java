package madoku.craft.java.compat.attributes;

import madoku.craft.java.attributes.HungerAPIManager;
import madoku.craft.java.attributes.LuckAPIManager;
import madoku.craft.java.attributes.LuckFeatureAdapter;
import madoku.craft.java.compat.MadokuCompatModuleState;
import madoku.craft.java.core.enchant.EnchantBooksAPIManager;
import madoku.craft.java.farming.FarmingAPIManager;
import madoku.craft.java.levels.LevelsPlayerAPIManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Installs bridges between Attributes and other Madoku feature modules. */
public final class MadokuAttributeFeatureAdapters {
	private MadokuAttributeFeatureAdapters() { }

	public static void initialize() {
		if (MadokuCompatModuleState.hasAll(
			MadokuCompatModuleState.ATTRIBUTES_ID,
			MadokuCompatModuleState.LEVELS_ID
		)) {
			HungerAPIManager.registerFeatureAdapter(LevelsPlayerAPIManager::getPlayerHungerBonusPoints);
		}
		madoku.craft.java.attributes.ArmorAPIManager.registerFeatureAdapter(
			EnchantBooksAPIManager::resolveBreachArmorEffectiveness
		);
		LuckAPIManager.registerEnchantmentAdapter(EnchantBooksAPIManager::applyConfiguredFortune);
		if (!MadokuCompatModuleState.hasAll(
			MadokuCompatModuleState.ATTRIBUTES_ID,
			MadokuCompatModuleState.FARMING_ID
		)) {
			return;
		}
		LuckAPIManager.registerFeatureAdapter(new LuckFeatureAdapter() {
			@Override
			public boolean isManagedCrop(ServerLevel level, BlockPos pos, BlockState state) {
				return FarmingAPIManager.isManagedCrop(level, pos, state);
			}

			@Override
			public boolean isCropHarvestReady(ServerLevel level, BlockPos pos, BlockState state) {
				return FarmingAPIManager.isCropHarvestReady(level, pos, state);
			}
		});
	}
}

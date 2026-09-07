package madoku.craft.java.compat.levels;

import madoku.craft.java.attributes.HealthAPIManager;
import madoku.craft.java.attributes.HungerAPIManager;
import madoku.craft.java.attributes.LuckAPIManager;
import madoku.craft.java.attributes.MadokuAttributesManager;
import madoku.craft.java.compat.MadokuCompatModuleState;
import madoku.craft.java.levels.LevelsFeatureAPIManager;
import madoku.craft.java.levels.LevelsFeatureAdapter;
import madoku.craft.java.pet.PetAbilitiesAPIManager;

import net.minecraft.server.level.ServerPlayer;

/** Installs Levels bridges for Attributes and Pets. */
public final class MadokuLevelsFeatureAdapters {
	private MadokuLevelsFeatureAdapters() { }

	public static void initialize() {
		LevelsFeatureAPIManager.registerAdapter(new LevelsFeatureAdapter() {
			@Override
			public boolean useAttributesContainer() {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ATTRIBUTES_ID)
					&& MadokuAttributesManager.isEnabled()
					&& (HungerAPIManager.isEnabled() || LuckAPIManager.isEnabled());
			}

			@Override
			public boolean isHungerEnabled() {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ATTRIBUTES_ID)
					&& HungerAPIManager.isEnabled();
			}

			@Override
			public boolean isLuckEnabled() {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ATTRIBUTES_ID)
					&& LuckAPIManager.isEnabled();
			}

			@Override
			public void applyPlayerMaxHealthAbilityBonus(ServerPlayer player) {
				if (MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.PETS_ID)) {
					PetAbilitiesAPIManager.applyPlayerMaxHealthAbilityBonus(player);
				}
			}

			@Override
			public void applyPlayerDamageAbilityBonus(ServerPlayer player) {
				if (MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.PETS_ID)) {
					PetAbilitiesAPIManager.applyPlayerDamageAbilityBonus(player);
				}
			}

			@Override
			public void applyPlayerArmorAbilityBonus(ServerPlayer player) {
				if (MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.PETS_ID)) {
					PetAbilitiesAPIManager.applyPlayerArmorAbilityBonus(player);
				}
			}

			@Override
			public void handleMaximumHungerChanged(ServerPlayer player) {
				if (MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ATTRIBUTES_ID)) {
					HungerAPIManager.handleMaximumHungerChanged(player);
				}
			}

			@Override
			public void restoreJoinHealth(ServerPlayer player) {
				if (MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ATTRIBUTES_ID)) {
					HealthAPIManager.restoreJoinHealth(player);
				}
			}
		});
	}
}

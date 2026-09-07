package madoku.craft.java.compat.smithing;

import madoku.craft.java.core.smithing.SmithingFeatureAPIManager;
import madoku.craft.java.core.smithing.SmithingFeatureAdapter;
import madoku.craft.java.compat.MadokuCompatModuleState;
import madoku.craft.java.items.ItemsAPIManager;
import madoku.craft.java.pet.PetAPIManager;
import madoku.craft.java.pet.PetEntitiesAPIManager;

import net.minecraft.world.item.ItemStack;

/** Connects the optional Items and Pets modules to Core smithing transactions. */
public final class MadokuSmithingFeatureAdapters {
	private MadokuSmithingFeatureAdapters() {
	}

	public static void initialize() {
		SmithingFeatureAPIManager.registerAdapter(new SmithingFeatureAdapter() {
			@Override
			public boolean isItemsEnabled() {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ITEMS_ID)
					&& ItemsAPIManager.isEnabled();
			}

			@Override
			public boolean isRarityCategoryItem(ItemStack stack) {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ITEMS_ID)
					&& ItemsAPIManager.isRarityCategoryItem(stack);
			}

			@Override
			public boolean areItemLevelsEnabled() {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ITEMS_ID)
					&& ItemsAPIManager.areItemLevelsEnabled();
			}

			@Override
			public void setItemLevel(ItemStack stack, int level) {
				if (MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ITEMS_ID)) ItemsAPIManager.setItemLevel(stack, level);
			}

			@Override
			public Integer getItemLevel(ItemStack stack) {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ITEMS_ID) ? ItemsAPIManager.getItemLevel(stack) : null;
			}

			@Override
			public int getItemStartingLevel() {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ITEMS_ID) ? ItemsAPIManager.getItemStartingLevel() : 1;
			}

			@Override
			public int getItemMaximumLevel() {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ITEMS_ID) ? ItemsAPIManager.getItemMaximumLevel() : 1;
			}

			@Override
			public boolean isPetsEnabled() {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.PETS_ID)
					&& PetAPIManager.isEnabled();
			}

			@Override
			public boolean isPetItem(ItemStack stack) {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.PETS_ID)
					&& PetEntitiesAPIManager.isPetItem(stack);
			}

			@Override
			public int petLevel(ItemStack stack) {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.PETS_ID) ? PetEntitiesAPIManager.petLevel(stack) : 1;
			}

			@Override
			public int maxPetLevel() {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.PETS_ID) ? PetAPIManager.maxPetLevel() : 1;
			}

			@Override
			public void setPetLevel(ItemStack stack, int level) {
				if (MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.PETS_ID)) PetEntitiesAPIManager.setPetLevel(stack, level);
			}
		});
	}
}

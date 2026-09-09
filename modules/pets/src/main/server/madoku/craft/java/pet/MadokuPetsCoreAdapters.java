package madoku.craft.java.pet;

import madoku.craft.java.core.iteminput.ItemInputFeatureAPIManager;
import madoku.craft.java.core.iteminput.ItemInputFeatureAdapter;
import madoku.craft.java.core.loot.LootFeatureAPIManager;
import madoku.craft.java.core.loot.LootFeatureAdapter;
import madoku.craft.java.core.smithing.SmithingFeatureAPIManager;
import madoku.craft.java.core.smithing.SmithingFeatureAdapter;
import madoku.craft.java.core.rarity.RarityAPIManager;
import madoku.craft.java.core.rarity.RarityEligibilityAPIManager;
import madoku.craft.java.core.rarity.RarityEligibilityAdapter;
import net.minecraft.world.item.ItemStack;

/** Installs the Core adapters that are implemented by the Pets module. */
public final class MadokuPetsCoreAdapters {
	private MadokuPetsCoreAdapters() {
	}

	public static void initialize() {
		RarityEligibilityAPIManager.registerAdapter(new RarityEligibilityAdapter() {
			@Override
			public boolean isEligible(ItemStack stack) {
				return PetEntitiesAPIManager.isPetItem(stack);
			}
		});
		SmithingFeatureAPIManager.registerAdapter(new SmithingFeatureAdapter() {
			@Override
			public boolean isPetsEnabled() {
				return PetAPIManager.isEnabled();
			}

			@Override
			public boolean isPetItem(ItemStack stack) {
				return PetEntitiesAPIManager.isPetItem(stack);
			}

			@Override
			public int petLevel(ItemStack stack) {
				return PetEntitiesAPIManager.petLevel(stack);
			}

			@Override
			public int maxPetLevel() {
				return PetAPIManager.maxPetLevel();
			}

			@Override
			public void setPetLevel(ItemStack stack, int level) {
				PetEntitiesAPIManager.setPetLevel(stack, level);
			}
		});
		LootFeatureAPIManager.registerAdapter(new LootFeatureAdapter() {
			@Override
			public void applyPetLore(ItemStack stack) {
				PetHagAPIManager.applyLore(stack);
			}

			@Override
			public boolean isPetsEnabled() {
				return PetAPIManager.isEnabled();
			}
		});
		ItemInputFeatureAPIManager.registerAdapter(new ItemInputFeatureAdapter() {
			@Override
			public boolean isPetItem(ItemStack stack) {
				return PetEntitiesAPIManager.isPetItem(stack);
			}

			@Override
			public RarityAPIManager.Tier petRarity(ItemStack stack) {
				return RarityAPIManager.fromString(PetHagAPIManager.rarity(stack));
			}

			@Override
			public void applyPetLore(ItemStack stack) {
				PetHagAPIManager.applyLore(stack);
			}
		});
	}
}

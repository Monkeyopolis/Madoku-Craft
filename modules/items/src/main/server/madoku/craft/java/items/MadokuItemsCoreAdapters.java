package madoku.craft.java.items;

import madoku.craft.java.core.enchant.EnchantItemAPIManager;
import madoku.craft.java.core.iteminput.ItemInputFeatureAPIManager;
import madoku.craft.java.core.iteminput.ItemInputFeatureAdapter;
import madoku.craft.java.core.loot.LootFeatureAPIManager;
import madoku.craft.java.core.loot.LootFeatureAdapter;
import madoku.craft.java.core.rarity.RarityItemAPIManager;
import madoku.craft.java.core.rarity.RarityEligibilityAPIManager;
import madoku.craft.java.core.rarity.RarityEligibilityAdapter;
import madoku.craft.java.core.recipes.RecipesItemAPIManager;
import madoku.craft.java.core.recipes.RecipesItemAdapter;
import madoku.craft.java.core.rarity.RarityItemAdapter;
import madoku.craft.java.core.smithing.SmithingFeatureAPIManager;
import madoku.craft.java.core.smithing.SmithingFeatureAdapter;
import net.minecraft.world.item.ItemStack;

/** Installs the Core adapters that are implemented by the Items module. */
public final class MadokuItemsCoreAdapters {
	private MadokuItemsCoreAdapters() {
	}

	public static void initialize() {
		RarityEligibilityAPIManager.registerAdapter(new RarityEligibilityAdapter() {
			@Override
			public boolean isEligible(ItemStack stack) {
				return ItemsAPIManager.isRarityCategoryItem(stack);
			}
		});
		RarityItemAPIManager.registerAdapter(new RarityItemAdapter() {
			@Override
			public boolean isRarityCategoryItem(ItemStack stack) {
				return ItemsAPIManager.isRarityCategoryItem(stack);
			}

			@Override
			public void applyRarityScaling(ItemStack stack, double multiplier) {
				ItemsAPIManager.applyRarityScaling(stack, multiplier);
			}

			@Override
			public void updateDurabilityLore(ItemStack stack) {
				ItemsAPIManager.updateDurabilityLore(stack);
			}
		});
		EnchantItemAPIManager.registerAdapter(ItemsAPIManager::updateDurabilityLore);
		RecipesItemAPIManager.registerAdapter(new RecipesItemAdapter() {
			@Override
			public boolean isRarityCategoryItem(ItemStack stack) {
				return ItemsAPIManager.isRarityCategoryItem(stack);
			}

			@Override
			public void applyConfiguredItemLevel(ItemStack stack, int level) {
				ItemsAPIManager.applyConfiguredItemLevel(stack, level);
			}

			@Override
			public void applyConfiguredItemLevel(ItemStack stack, int level, boolean updateLore) {
				ItemsAPIManager.applyConfiguredItemLevel(stack, level, updateLore);
			}
		});
		LootFeatureAPIManager.registerAdapter(new LootFeatureAdapter() {
			@Override
			public void applyGeneratedItemLevel(ItemStack stack, net.minecraft.util.RandomSource random) {
				ItemsAPIManager.applyGeneratedItemLevel(stack, random);
			}

			@Override
			public boolean isRarityCategoryItem(ItemStack stack) {
				return ItemsAPIManager.isRarityCategoryItem(stack);
			}
		});
		ItemInputFeatureAPIManager.registerAdapter(new ItemInputFeatureAdapter() {
			@Override
			public void applyItemLevel(ItemStack stack, int level) {
				ItemsAPIManager.applyConfiguredItemLevel(stack, level);
			}
		});
		SmithingFeatureAPIManager.registerAdapter(new SmithingFeatureAdapter() {
			@Override
			public boolean isItemsEnabled() {
				return ItemsAPIManager.isEnabled();
			}

			@Override
			public boolean isRarityCategoryItem(ItemStack stack) {
				return ItemsAPIManager.isRarityCategoryItem(stack);
			}

			@Override
			public boolean areItemLevelsEnabled() {
				return ItemsAPIManager.areItemLevelsEnabled();
			}

			@Override
			public void setItemLevel(ItemStack stack, int level) {
				ItemsAPIManager.setItemLevel(stack, level);
			}

			@Override
			public Integer getItemLevel(ItemStack stack) {
				return ItemsAPIManager.getItemLevel(stack);
			}

			@Override
			public int getItemStartingLevel() {
				return ItemsAPIManager.getItemStartingLevel();
			}

			@Override
			public int getItemMaximumLevel() {
				return ItemsAPIManager.getItemMaximumLevel();
			}
		});
	}
}

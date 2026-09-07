package madoku.craft.java.items;

import madoku.craft.java.core.enchant.EnchantItemAPIManager;
import madoku.craft.java.core.rarity.RarityItemAPIManager;
import madoku.craft.java.core.recipes.RecipesItemAPIManager;
import madoku.craft.java.core.recipes.RecipesItemAdapter;
import madoku.craft.java.core.rarity.RarityItemAdapter;
import net.minecraft.world.item.ItemStack;

/** Installs the Core adapters that are implemented by the Items module. */
public final class MadokuItemsCoreAdapters {
	private MadokuItemsCoreAdapters() {
	}

	public static void initialize() {
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
	}
}

package madoku.craft.java.items;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Orchestrates the Madoku Items subsystems. */
public final class MadokuItemsManager {
	private MadokuItemsManager() { }

	public static void initialize() {
		ItemsAPIManager.registerProvider(new MadokuItemsProvider());
		ItemsConfigManager.initialize();
		ItemsCategoriesManager.initialize();
		ItemsStacksManager.initialize();
	}

	public static void reset() {
		ItemsCategoriesManager.reset();
		ItemsStacksManager.reset();
	}

	public static void onServerStarted(MinecraftServer server) {
		ItemsCategoriesManager.onServerStarted(server);
		ItemsStacksManager.onServerStarted(server);
	}

	public static void onServerTick(MinecraftServer server) { ItemsCategoriesManager.onServerTick(server); }

	public static boolean isEnabled() { return ItemsCategoriesManager.isEnabled(); }

	public static boolean areItemLevelsEnabled() { return ItemsCategoriesManager.areItemLevelsEnabled(); }

	public static boolean isConfiguredFuel(ItemStack stack) { return ItemsCategoriesManager.isConfiguredFuel(stack); }

	public static boolean isRarityCategoryItem(ItemStack stack) { return ItemsCategoriesManager.isRarityCategoryItem(stack); }

	public static boolean isRarityCategoryItem(Item item) { return ItemsCategoriesManager.isRarityCategoryItem(item); }

	public static boolean isWeaponCategoryItem(Item item) { return ItemsCategoriesManager.isWeaponCategoryItem(item); }

	public static void applyGeneratedItemLevel(ItemStack stack, RandomSource randomSource) {
		ItemsCategoriesManager.applyGeneratedItemLevel(stack, randomSource);
	}

	public static void applyConfiguredItemLevel(ItemStack stack, int level) {
		ItemsCategoriesManager.applyConfiguredItemLevel(stack, level);
	}

	public static void applyConfiguredItemLevel(ItemStack stack, int level, boolean updateLore) {
		ItemsCategoriesManager.applyConfiguredItemLevel(stack, level, updateLore);
	}

	public static void setItemLevel(ItemStack stack, int level) {
		ItemsCategoriesManager.setItemLevel(stack, level);
	}

	public static Integer getItemLevel(ItemStack stack) { return ItemsCategoriesManager.getItemLevel(stack); }

	public static int getItemStartingLevel() { return ItemsCategoriesManager.getItemStartingLevel(); }

	public static int getItemMaximumLevel() { return ItemsCategoriesManager.getItemMaximumLevel(); }

	public static void applyRarityScaling(ItemStack stack, double multiplier) {
		ItemsCategoriesManager.applyRarityScaling(stack, multiplier);
	}

	public static void updateDurabilityLore(ItemStack stack) {
		ItemsCategoriesManager.updateDurabilityLore(stack);
	}
}

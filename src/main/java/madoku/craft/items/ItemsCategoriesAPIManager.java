package madoku.craft.items;

import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Public contract for configurable item categories, levels, and scaling. */
public final class ItemsCategoriesAPIManager {
	private static final ItemsCategoriesProvider UNAVAILABLE_PROVIDER = new ItemsCategoriesProvider() { };
	private static volatile ItemsCategoriesProvider provider = UNAVAILABLE_PROVIDER;

	private ItemsCategoriesAPIManager() { }
	public static void registerProvider(ItemsCategoriesProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Items categories provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static void onServerStarted(MinecraftServer server) { provider.onServerStarted(server); }
	public static void applyConfiguredItemMetadata() { provider.applyConfiguredItemMetadata(); }
	public static String createClientSyncSnapshot() { return provider.createClientSyncSnapshot(); }
	public static void applySynchronizedProfiles(String snapshotJson) { provider.applySynchronizedProfiles(snapshotJson); }
	public static void resetClientSynchronizedState() { provider.resetClientSynchronizedState(); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static int applySingleStackRule(ItemStack stack, int currentLimit) { return provider.applySingleStackRule(stack, currentLimit); }
	public static int adjustFuelTicks(ItemStack stack, int originalTicks) { return provider.adjustFuelTicks(stack, originalTicks); }
	public static boolean isConfiguredFuel(ItemStack stack) { return provider.isConfiguredFuel(stack); }
	public static boolean isToolCategoryItem(Item item) { return provider.isToolCategoryItem(item); }
	public static boolean isToolCategoryItem(ItemStack stack) { return provider.isToolCategoryItem(stack); }
	public static boolean isArmorCategoryItem(Item item) { return provider.isArmorCategoryItem(item); }
	public static boolean isArmorCategoryItem(ItemStack stack) { return provider.isArmorCategoryItem(stack); }
	public static boolean isWeaponCategoryItem(Item item) { return provider.isWeaponCategoryItem(item); }
	public static boolean isRarityCategoryItem(Item item) { return provider.isRarityCategoryItem(item); }
	public static boolean isRarityCategoryItem(ItemStack stack) { return provider.isRarityCategoryItem(stack); }
	public static boolean areItemLevelsEnabled() { return provider.areItemLevelsEnabled(); }
	public static int getItemStartingLevel() { return provider.getItemStartingLevel(); }
	public static int getItemMaximumLevel() { return provider.getItemMaximumLevel(); }
	public static Integer getItemLevel(ItemStack stack) { return provider.getItemLevel(stack); }
	public static void applyGeneratedItemLevel(ItemStack stack, RandomSource randomSource) { provider.applyGeneratedItemLevel(stack, randomSource); }
	public static void applyConfiguredItemLevel(ItemStack stack, int level) { provider.applyConfiguredItemLevel(stack, level); }
	public static void applyConfiguredItemLevel(ItemStack stack, int level, boolean updateLore) { provider.applyConfiguredItemLevel(stack, level, updateLore); }
	public static void setItemLevel(ItemStack stack, int level) { provider.setItemLevel(stack, level); }
	public static void applyRarityScaling(ItemStack stack, double multiplier) { provider.applyRarityScaling(stack, multiplier); }
	public static void applyItemLevelScaling(ItemStack stack, double multiplier) { provider.applyItemLevelScaling(stack, multiplier); }
	public static void updateDurabilityLore(ItemStack stack) { provider.updateDurabilityLore(stack); }
	public static Set<String> getCategories(Item item) { return provider.getCategories(item); }
	public static boolean hasCategory(Item item, String category) { return provider.hasCategory(item, category); }
	public static boolean hasCategory(ItemStack stack, String category) { return provider.hasCategory(stack, category); }
}

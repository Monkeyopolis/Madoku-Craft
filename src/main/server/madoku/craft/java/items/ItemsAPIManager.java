package madoku.craft.java.items;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Public aggregate contract for the Madoku Items subsystem. */
public final class ItemsAPIManager {
	public static final String CATEGORY_FUEL = "fuel";
	public static final String CATEGORY_OTHER = "other";
	public static final String CATEGORY_TOOL = "tool";
	public static final String CATEGORY_WEAPON = "weapon";
	public static final String CATEGORY_ARMOR = "armor";
	private static final ItemsProvider UNAVAILABLE_PROVIDER = new ItemsProvider() { };
	private static volatile ItemsProvider provider = UNAVAILABLE_PROVIDER;

	private ItemsAPIManager() { }
	public static void registerProvider(ItemsProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Items provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static void onServerStarted(MinecraftServer server) { provider.onServerStarted(server); }
	public static void onServerTick(MinecraftServer server) { provider.onServerTick(server); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static boolean areItemLevelsEnabled() { return provider.areItemLevelsEnabled(); }
	public static boolean isConfiguredFuel(ItemStack stack) { return provider.isConfiguredFuel(stack); }
	public static boolean isRarityCategoryItem(ItemStack stack) { return provider.isRarityCategoryItem(stack); }
	public static boolean isRarityCategoryItem(Item item) { return provider.isRarityCategoryItem(item); }
	public static boolean isWeaponCategoryItem(Item item) { return provider.isWeaponCategoryItem(item); }
	public static void applyGeneratedItemLevel(ItemStack stack, RandomSource randomSource) { provider.applyGeneratedItemLevel(stack, randomSource); }
	public static void applyConfiguredItemLevel(ItemStack stack, int level) { provider.applyConfiguredItemLevel(stack, level); }
	public static void applyConfiguredItemLevel(ItemStack stack, int level, boolean updateLore) { provider.applyConfiguredItemLevel(stack, level, updateLore); }
	public static void setItemLevel(ItemStack stack, int level) { provider.setItemLevel(stack, level); }
	public static Integer getItemLevel(ItemStack stack) { return provider.getItemLevel(stack); }
	public static int getItemStartingLevel() { return provider.getItemStartingLevel(); }
	public static int getItemMaximumLevel() { return provider.getItemMaximumLevel(); }
	public static void applyRarityScaling(ItemStack stack, double multiplier) { provider.applyRarityScaling(stack, multiplier); }
	public static void updateDurabilityLore(ItemStack stack) { provider.updateDurabilityLore(stack); }
}

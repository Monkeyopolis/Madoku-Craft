package madoku.craft.java.items;

import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Provider contract for configurable item categories and levels. */
public interface ItemsCategoriesProvider {
	default void initialize() { }
	default void reset() { }
	default void onServerStarted(MinecraftServer server) { }
	default void applyConfiguredItemMetadata() { }
	default String createClientSyncSnapshot() { return "{}"; }
	default void applySynchronizedProfiles(String snapshotJson) { }
	default void resetClientSynchronizedState() { }
	default boolean isEnabled() { return false; }
	default int applySingleStackRule(ItemStack stack, int currentLimit) { return currentLimit; }
	default int adjustFuelTicks(ItemStack stack, int originalTicks) { return originalTicks; }
	default boolean isConfiguredFuel(ItemStack stack) { return false; }
	default boolean isToolCategoryItem(Item item) { return false; }
	default boolean isToolCategoryItem(ItemStack stack) { return false; }
	default boolean isArmorCategoryItem(Item item) { return false; }
	default boolean isArmorCategoryItem(ItemStack stack) { return false; }
	default boolean isWeaponCategoryItem(Item item) { return false; }
	default boolean isRarityCategoryItem(Item item) { return false; }
	default boolean isRarityCategoryItem(ItemStack stack) { return false; }
	default boolean areItemLevelsEnabled() { return false; }
	default int getItemStartingLevel() { return 1; }
	default int getItemMaximumLevel() { return 1; }
	default Integer getItemLevel(ItemStack stack) { return null; }
	default void applyGeneratedItemLevel(ItemStack stack, RandomSource randomSource) { }
	default void applyConfiguredItemLevel(ItemStack stack, int level) { }
	default void applyConfiguredItemLevel(ItemStack stack, int level, boolean updateLore) { }
	default void setItemLevel(ItemStack stack, int level) { }
	default void applyRarityScaling(ItemStack stack, double multiplier) { }
	default void applyItemLevelScaling(ItemStack stack, double multiplier) { }
	default void updateDurabilityLore(ItemStack stack) { }
	default Set<String> getCategories(Item item) { return Set.of(); }
	default boolean hasCategory(Item item, String category) { return false; }
	default boolean hasCategory(ItemStack stack, String category) { return false; }
}

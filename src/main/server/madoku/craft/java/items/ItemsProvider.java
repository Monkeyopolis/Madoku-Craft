package madoku.craft.java.items;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Provider contract implemented by the module that owns Madoku Items. */
public interface ItemsProvider {
	default void initialize() { }
	default void reset() { }
	default void onServerStarted(MinecraftServer server) { }
	default boolean isEnabled() { return false; }
	default boolean areItemLevelsEnabled() { return false; }
	default boolean isConfiguredFuel(ItemStack stack) { return false; }
	default boolean isRarityCategoryItem(ItemStack stack) { return false; }
	default boolean isRarityCategoryItem(Item item) { return false; }
	default boolean isWeaponCategoryItem(Item item) { return false; }
	default void applyGeneratedItemLevel(ItemStack stack, RandomSource randomSource) { }
	default void applyConfiguredItemLevel(ItemStack stack, int level) { }
	default void applyConfiguredItemLevel(ItemStack stack, int level, boolean updateLore) { }
	default void setItemLevel(ItemStack stack, int level) { }
	default Integer getItemLevel(ItemStack stack) { return null; }
	default int getItemStartingLevel() { return 1; }
	default int getItemMaximumLevel() { return 1; }
	default void applyRarityScaling(ItemStack stack, double multiplier) { }
	default void updateDurabilityLore(ItemStack stack) { }
}

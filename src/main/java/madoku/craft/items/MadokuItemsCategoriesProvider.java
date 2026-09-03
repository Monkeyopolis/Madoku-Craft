package madoku.craft.items;

import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Built-in provider backed by the Madoku item-category implementation. */
public final class MadokuItemsCategoriesProvider implements ItemsCategoriesProvider {
	@Override public void initialize() { ItemsCategoriesManager.initialize(); }
	@Override public void reset() { ItemsCategoriesManager.reset(); }
	@Override public void onServerStarted(MinecraftServer server) { ItemsCategoriesManager.onServerStarted(server); }
	@Override public void applyConfiguredItemMetadata() { ItemsCategoriesManager.applyConfiguredItemMetadata(); }
	@Override public String createClientSyncSnapshot() { return ItemsCategoriesManager.createClientSyncSnapshot(); }
	@Override public void applySynchronizedProfiles(String snapshotJson) { ItemsCategoriesManager.applySynchronizedProfiles(snapshotJson); }
	@Override public void resetClientSynchronizedState() { ItemsCategoriesManager.resetClientSynchronizedState(); }
	@Override public boolean isEnabled() { return ItemsCategoriesManager.isEnabled(); }
	@Override public int applySingleStackRule(ItemStack stack, int currentLimit) { return ItemsCategoriesManager.applySingleStackRule(stack, currentLimit); }
	@Override public int adjustFuelTicks(ItemStack stack, int originalTicks) { return ItemsCategoriesManager.adjustFuelTicks(stack, originalTicks); }
	@Override public boolean isConfiguredFuel(ItemStack stack) { return ItemsCategoriesManager.isConfiguredFuel(stack); }
	@Override public boolean isToolCategoryItem(Item item) { return ItemsCategoriesManager.isToolCategoryItem(item); }
	@Override public boolean isToolCategoryItem(ItemStack stack) { return ItemsCategoriesManager.isToolCategoryItem(stack); }
	@Override public boolean isArmorCategoryItem(Item item) { return ItemsCategoriesManager.isArmorCategoryItem(item); }
	@Override public boolean isArmorCategoryItem(ItemStack stack) { return ItemsCategoriesManager.isArmorCategoryItem(stack); }
	@Override public boolean isWeaponCategoryItem(Item item) { return ItemsCategoriesManager.isWeaponCategoryItem(item); }
	@Override public boolean isRarityCategoryItem(Item item) { return ItemsCategoriesManager.isRarityCategoryItem(item); }
	@Override public boolean isRarityCategoryItem(ItemStack stack) { return ItemsCategoriesManager.isRarityCategoryItem(stack); }
	@Override public boolean areItemLevelsEnabled() { return ItemsCategoriesManager.areItemLevelsEnabled(); }
	@Override public int getItemStartingLevel() { return ItemsCategoriesManager.getItemStartingLevel(); }
	@Override public int getItemMaximumLevel() { return ItemsCategoriesManager.getItemMaximumLevel(); }
	@Override public Integer getItemLevel(ItemStack stack) { return ItemsCategoriesManager.getItemLevel(stack); }
	@Override public void applyGeneratedItemLevel(ItemStack stack, RandomSource randomSource) { ItemsCategoriesManager.applyGeneratedItemLevel(stack, randomSource); }
	@Override public void applyConfiguredItemLevel(ItemStack stack, int level) { ItemsCategoriesManager.applyConfiguredItemLevel(stack, level); }
	@Override public void applyConfiguredItemLevel(ItemStack stack, int level, boolean updateLore) { ItemsCategoriesManager.applyConfiguredItemLevel(stack, level, updateLore); }
	@Override public void setItemLevel(ItemStack stack, int level) { ItemsCategoriesManager.setItemLevel(stack, level); }
	@Override public void applyRarityScaling(ItemStack stack, double multiplier) { ItemsCategoriesManager.applyRarityScaling(stack, multiplier); }
	@Override public void applyItemLevelScaling(ItemStack stack, double multiplier) { ItemsCategoriesManager.applyItemLevelScaling(stack, multiplier); }
	@Override public void updateDurabilityLore(ItemStack stack) { ItemsCategoriesManager.updateDurabilityLore(stack); }
	@Override public Set<String> getCategories(Item item) { return ItemsCategoriesManager.getCategories(item); }
	@Override public boolean hasCategory(Item item, String category) { return ItemsCategoriesManager.hasCategory(item, category); }
	@Override public boolean hasCategory(ItemStack stack, String category) { return ItemsCategoriesManager.hasCategory(stack, category); }
}

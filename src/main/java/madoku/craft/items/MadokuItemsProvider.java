package madoku.craft.items;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Built-in provider backed by the Madoku Items runtime orchestrator. */
public final class MadokuItemsProvider implements ItemsProvider {
	@Override public void initialize() { MadokuItemsManager.initialize(); }
	@Override public void reset() { MadokuItemsManager.reset(); }
	@Override public void onServerStarted(MinecraftServer server) { MadokuItemsManager.onServerStarted(server); }
	@Override public boolean isEnabled() { return MadokuItemsManager.isEnabled(); }
	@Override public boolean areItemLevelsEnabled() { return MadokuItemsManager.areItemLevelsEnabled(); }
	@Override public boolean isConfiguredFuel(ItemStack stack) { return MadokuItemsManager.isConfiguredFuel(stack); }
	@Override public boolean isRarityCategoryItem(ItemStack stack) { return MadokuItemsManager.isRarityCategoryItem(stack); }
	@Override public boolean isRarityCategoryItem(Item item) { return MadokuItemsManager.isRarityCategoryItem(item); }
	@Override public boolean isWeaponCategoryItem(Item item) { return MadokuItemsManager.isWeaponCategoryItem(item); }
	@Override public void applyGeneratedItemLevel(ItemStack stack, RandomSource randomSource) { MadokuItemsManager.applyGeneratedItemLevel(stack, randomSource); }
	@Override public void applyConfiguredItemLevel(ItemStack stack, int level) { MadokuItemsManager.applyConfiguredItemLevel(stack, level); }
	@Override public void applyConfiguredItemLevel(ItemStack stack, int level, boolean updateLore) { MadokuItemsManager.applyConfiguredItemLevel(stack, level, updateLore); }
	@Override public void setItemLevel(ItemStack stack, int level) { MadokuItemsManager.setItemLevel(stack, level); }
	@Override public Integer getItemLevel(ItemStack stack) { return MadokuItemsManager.getItemLevel(stack); }
	@Override public int getItemStartingLevel() { return MadokuItemsManager.getItemStartingLevel(); }
	@Override public int getItemMaximumLevel() { return MadokuItemsManager.getItemMaximumLevel(); }
	@Override public void applyRarityScaling(ItemStack stack, double multiplier) { MadokuItemsManager.applyRarityScaling(stack, multiplier); }
	@Override public void updateDurabilityLore(ItemStack stack) { MadokuItemsManager.updateDurabilityLore(stack); }
}

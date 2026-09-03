package madoku.craft.java.core.smithing;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Built-in provider backed by the Madoku Smithing implementation. */
public final class MadokuSmithingProvider implements SmithingProvider {
	@Override public void initialize() { MadokuSmithingManager.initialize(); }
	@Override public void reset() { MadokuSmithingManager.reset(); }
	@Override public void onServerStarted(MinecraftServer server) { MadokuSmithingManager.onServerStarted(server); }
	@Override public boolean acceptsPetItems() { return MadokuSmithingManager.acceptsPetItems(); }
	@Override public boolean acceptsExtendedItems() { return MadokuSmithingManager.acceptsExtendedItems(); }
	@Override public boolean isTemplateItem(ItemStack stack) { return MadokuSmithingManager.isTemplateItem(stack); }
	@Override public boolean isNetheriteUpgradeTemplate(ItemStack stack) { return MadokuSmithingManager.isNetheriteUpgradeTemplate(stack); }
	@Override public boolean isBottleCatalyst(ItemStack stack) { return MadokuSmithingManager.isBottleCatalyst(stack); }
	@Override public boolean isManagedBase(ItemStack stack) { return MadokuSmithingManager.isManagedBase(stack); }
	@Override public boolean isAllowedAdditional(SmithingMenu menu, ItemStack stack) { return MadokuSmithingManager.isAllowedAdditional(menu, stack); }
	@Override public void applyCustomResult(SmithingMenu menu) { MadokuSmithingManager.applyCustomResult(menu); }
	@Override public void prepareSwappedRecipeResult(SmithingMenu menu, Level level) { MadokuSmithingManager.prepareSwappedRecipeResult(menu, level); }
}

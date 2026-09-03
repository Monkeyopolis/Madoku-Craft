package madoku.craft.java.core.smithing;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Provider contract for Madoku Smithing. */
public interface SmithingProvider {
	default void initialize() { }
	default void reset() { }
	default void onServerStarted(MinecraftServer server) { }
	default boolean acceptsPetItems() { return false; }
	default boolean acceptsExtendedItems() { return false; }
	default boolean isTemplateItem(ItemStack stack) { return false; }
	default boolean isNetheriteUpgradeTemplate(ItemStack stack) { return false; }
	default boolean isBottleCatalyst(ItemStack stack) { return false; }
	default boolean isManagedBase(ItemStack stack) { return false; }
	default boolean isAllowedAdditional(SmithingMenu menu, ItemStack stack) { return false; }
	default void applyCustomResult(SmithingMenu menu) { }
	default void prepareSwappedRecipeResult(SmithingMenu menu, Level level) { }
}

package madoku.craft.java.core.smithing;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Public contract for Madoku Smithing. */
public final class SmithingAPIManager {
	private static final SmithingProvider UNAVAILABLE_PROVIDER = new SmithingProvider() { };
	private static volatile SmithingProvider provider = UNAVAILABLE_PROVIDER;

	private SmithingAPIManager() { }

	public static void registerProvider(SmithingProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Smithing provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static void onServerStarted(MinecraftServer server) { provider.onServerStarted(server); }
	public static boolean acceptsPetItems() { return provider.acceptsPetItems(); }
	public static boolean acceptsExtendedItems() { return provider.acceptsExtendedItems(); }
	public static boolean isTemplateItem(ItemStack stack) { return provider.isTemplateItem(stack); }
	public static boolean isNetheriteUpgradeTemplate(ItemStack stack) { return provider.isNetheriteUpgradeTemplate(stack); }
	public static boolean isBottleCatalyst(ItemStack stack) { return provider.isBottleCatalyst(stack); }
	public static boolean isManagedBase(ItemStack stack) { return provider.isManagedBase(stack); }
	public static boolean isAllowedAdditional(SmithingMenu menu, ItemStack stack) { return provider.isAllowedAdditional(menu, stack); }
	public static void applyCustomResult(SmithingMenu menu) { provider.applyCustomResult(menu); }
	public static void prepareSwappedRecipeResult(SmithingMenu menu, Level level) { provider.prepareSwappedRecipeResult(menu, level); }
}

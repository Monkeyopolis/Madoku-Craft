package madoku.craft.items;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Orchestrates the Madoku Items subsystems. */
public final class MadokuItemsManager {
	private MadokuItemsManager() { }

	public static void initialize() {
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

	public static boolean isEnabled() { return ItemsCategoriesManager.isEnabled(); }

	public static boolean useMadokuLuck() { return ItemsCategoriesManager.useMadokuLuck(); }

	public static boolean isConfiguredFuel(ItemStack stack) { return ItemsCategoriesManager.isConfiguredFuel(stack); }

	public static boolean isRarityCategoryItem(ItemStack stack) { return ItemsCategoriesManager.isRarityCategoryItem(stack); }

	public static boolean isRarityCategoryItem(Item item) { return ItemsCategoriesManager.isRarityCategoryItem(item); }
}

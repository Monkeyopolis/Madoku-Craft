package madoku.craft.java.core.iteminput;

import madoku.craft.java.core.rarity.RarityAPIManager;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Access point for optional feature behavior required by Core item-input commands. */
public final class ItemInputFeatureAPIManager {
	private static final List<ItemInputFeatureAdapter> adapters = new CopyOnWriteArrayList<>();

	private ItemInputFeatureAPIManager() {
	}

	public static void registerAdapter(ItemInputFeatureAdapter candidate) {
		if (candidate == null) {
			throw new IllegalArgumentException("Item-input feature adapter must not be null.");
		}
		if (!adapters.contains(candidate)) {
			adapters.add(candidate);
		}
	}

	public static void unregisterAdapter() {
		adapters.clear();
	}

	public static boolean isPetItem(ItemStack stack) {
		for (ItemInputFeatureAdapter adapter : adapters) {
			if (adapter.isPetItem(stack)) return true;
		}
		return false;
	}

	public static RarityAPIManager.Tier petRarity(ItemStack stack) {
		for (ItemInputFeatureAdapter adapter : adapters) {
			if (adapter.isPetItem(stack)) return adapter.petRarity(stack);
		}
		return null;
	}

	public static void applyItemLevel(ItemStack stack, int level) {
		for (ItemInputFeatureAdapter adapter : adapters) {
			adapter.applyItemLevel(stack, level);
		}
	}

	public static void applyPetLore(ItemStack stack) {
		for (ItemInputFeatureAdapter adapter : adapters) {
			adapter.applyPetLore(stack);
		}
	}
}

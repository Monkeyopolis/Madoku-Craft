package madoku.craft.java.core.smithing;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Access point for optional feature behavior required by the Core smithing subsystem. */
public final class SmithingFeatureAPIManager {
	private static final List<SmithingFeatureAdapter> adapters = new CopyOnWriteArrayList<>();

	private SmithingFeatureAPIManager() {
	}

	public static void registerAdapter(SmithingFeatureAdapter candidate) {
		if (candidate == null) {
			throw new IllegalArgumentException("Smithing feature adapter must not be null.");
		}
		if (!adapters.contains(candidate)) {
			adapters.add(candidate);
		}
	}

	public static void unregisterAdapter() {
		adapters.clear();
	}

	public static boolean isItemsEnabled() {
		for (SmithingFeatureAdapter adapter : adapters) {
			if (adapter.isItemsEnabled()) return true;
		}
		return false;
	}

	public static boolean isRarityCategoryItem(ItemStack stack) {
		for (SmithingFeatureAdapter adapter : adapters) {
			if (adapter.isRarityCategoryItem(stack)) return true;
		}
		return false;
	}

	public static boolean areItemLevelsEnabled() {
		for (SmithingFeatureAdapter adapter : adapters) {
			if (adapter.areItemLevelsEnabled()) return true;
		}
		return false;
	}

	public static void setItemLevel(ItemStack stack, int level) {
		for (SmithingFeatureAdapter adapter : adapters) {
			if (adapter.isRarityCategoryItem(stack)) {
				adapter.setItemLevel(stack, level);
				return;
			}
		}
	}

	public static Integer getItemLevel(ItemStack stack) {
		for (SmithingFeatureAdapter adapter : adapters) {
			if (adapter.isRarityCategoryItem(stack)) return adapter.getItemLevel(stack);
		}
		return null;
	}

	public static int getItemStartingLevel() {
		for (SmithingFeatureAdapter adapter : adapters) {
			if (adapter.isItemsEnabled()) return adapter.getItemStartingLevel();
		}
		return 1;
	}

	public static int getItemMaximumLevel() {
		for (SmithingFeatureAdapter adapter : adapters) {
			if (adapter.isItemsEnabled()) return adapter.getItemMaximumLevel();
		}
		return 1;
	}

	public static boolean isPetsEnabled() {
		for (SmithingFeatureAdapter adapter : adapters) {
			if (adapter.isPetsEnabled()) return true;
		}
		return false;
	}

	public static boolean isPetItem(ItemStack stack) {
		for (SmithingFeatureAdapter adapter : adapters) {
			if (adapter.isPetItem(stack)) return true;
		}
		return false;
	}

	public static int petLevel(ItemStack stack) {
		for (SmithingFeatureAdapter adapter : adapters) {
			if (adapter.isPetItem(stack)) return adapter.petLevel(stack);
		}
		return 1;
	}

	public static int maxPetLevel() {
		for (SmithingFeatureAdapter adapter : adapters) {
			if (adapter.isPetsEnabled()) return adapter.maxPetLevel();
		}
		return 1;
	}

	public static void setPetLevel(ItemStack stack, int level) {
		for (SmithingFeatureAdapter adapter : adapters) {
			if (adapter.isPetItem(stack)) {
				adapter.setPetLevel(stack, level);
				return;
			}
		}
	}
}

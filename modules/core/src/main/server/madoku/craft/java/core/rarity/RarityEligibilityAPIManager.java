package madoku.craft.java.core.rarity;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Access point for the optional modules that are allowed to apply item rarity. */
public final class RarityEligibilityAPIManager {
	private static final List<RarityEligibilityAdapter> adapters = new CopyOnWriteArrayList<>();

	private RarityEligibilityAPIManager() {
	}

	public static void registerAdapter(RarityEligibilityAdapter candidate) {
		if (candidate == null) {
			throw new IllegalArgumentException("Rarity eligibility adapter must not be null.");
		}
		if (!adapters.contains(candidate)) {
			adapters.add(candidate);
		}
	}

	public static void unregisterAdapter() {
		adapters.clear();
	}

	public static boolean isEligible(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		for (RarityEligibilityAdapter adapter : adapters) {
			if (adapter.isEligible(stack)) {
				return true;
			}
		}
		return false;
	}
}

package madoku.craft.java.pet;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;

/** Public contract for Madoku pet trading and pet-item presentation. */
public final class PetHagAPIManager {
	private static final PetHagProvider UNAVAILABLE_PROVIDER = new PetHagProvider() { };
	private static volatile PetHagProvider provider = UNAVAILABLE_PROVIDER;
	private PetHagAPIManager() { }
	public static void registerProvider(PetHagProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Pet hag provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static List<Item> tradeItems() { return provider.tradeItems(); }
	public static int rarityWeight(String rarity) { return provider.rarityWeight(rarity); }
	public static ItemStack tradeStack(Item item, int level) { return provider.tradeStack(item, level); }
	public static ItemCost tradeIngredient(Item item, int level) { return provider.tradeIngredient(item, level); }
	public static String rarity(ItemStack stack) { return provider.rarity(stack); }
	public static void applyLore(ItemStack stack) { provider.applyLore(stack); }
}

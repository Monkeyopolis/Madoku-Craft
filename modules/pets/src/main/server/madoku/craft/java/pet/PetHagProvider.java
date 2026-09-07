package madoku.craft.java.pet;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;

/** Provider contract for Madoku pet trading. */
public interface PetHagProvider {
	default void initialize() { }
	default List<Item> tradeItems() { return List.of(); }
	default int rarityWeight(String rarity) { return 0; }
	default ItemStack tradeStack(Item item, int level) { return ItemStack.EMPTY; }
	default ItemCost tradeIngredient(Item item, int level) { return null; }
	default String rarity(ItemStack stack) { return "common"; }
	default void applyLore(ItemStack stack) { }
}

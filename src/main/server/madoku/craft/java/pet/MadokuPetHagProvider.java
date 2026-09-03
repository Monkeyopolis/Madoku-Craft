package madoku.craft.java.pet;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;

/** Built-in provider backed by the Madoku pet hag implementation. */
public final class MadokuPetHagProvider implements PetHagProvider {
	@Override public void initialize() { PetHagManager.initialize(); }
	@Override public List<Item> tradeItems() { return PetHagManager.tradeItems(); }
	@Override public int rarityWeight(String rarity) { return PetHagManager.rarityWeight(rarity); }
	@Override public ItemStack tradeStack(Item item, int level) { return PetHagManager.tradeStack(item, level); }
	@Override public ItemCost tradeIngredient(Item item, int level) { return PetHagManager.tradeIngredient(item, level); }
	@Override public String rarity(ItemStack stack) { return PetHagManager.rarity(stack); }
	@Override public void applyLore(ItemStack stack) { PetHagManager.applyLore(stack); }
}

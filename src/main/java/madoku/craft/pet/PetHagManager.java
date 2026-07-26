package madoku.craft.pet;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Owns the Hag-facing pet trade pool, rarity, and item presentation helpers. */
public final class PetHagManager {
	private PetHagManager() {
	}

	public static void initialize() {
	}

	public static List<Item> tradeItems() {
		return MadokuPetManager.tradeSpawnEggItems();
	}

	public static int rarityWeight(String rarity) {
		return MadokuPetManager.petTradeRarityWeight(rarity);
	}

	public static String rarity(ItemStack stack) {
		return MadokuPetManager.petRarity(stack);
	}

	public static void applyLore(ItemStack stack) {
		MadokuPetManager.applySupportedSpawnEggLore(stack);
	}
}

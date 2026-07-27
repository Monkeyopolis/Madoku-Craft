package madoku.craft.pet;

import java.util.List;
import madoku.craft.pet.PetConfigManager.PetRule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Owns the Hag-facing pet trade pool, rarity, and item presentation helpers. */
public final class PetHagManager {
	private PetHagManager() {
	}

	public static void initialize() {
	}

	public static List<Item> tradeItems() {
		List<Item> items = new java.util.ArrayList<>();
		for (PetRule rule : PetConfigManager.rules().values()) {
			if (rule == null || !rule.enabled) continue;
			Item item = PetConfigManager.resolveItem(rule.itemId);
			if (item instanceof SpawnEggItem) items.add(item);
		}
		items.sort((left, right) -> BuiltInRegistries.ITEM.getKey(left).toString().compareTo(BuiltInRegistries.ITEM.getKey(right).toString()));
		return List.copyOf(items);
	}

	public static int rarityWeight(String rarity) {
		return switch (PetConfigManager.normalizePetRarity(rarity)) {
			case MadokuPetManager.PET_RARITY_RARE -> PetConfigManager.settings().petRarityRareChanceWeight;
			case MadokuPetManager.PET_RARITY_EPIC -> PetConfigManager.settings().petRarityEpicChanceWeight;
			case MadokuPetManager.PET_RARITY_MYTHIC -> PetConfigManager.settings().petRarityMythicChanceWeight;
			default -> PetConfigManager.settings().petRarityCommonChanceWeight;
		};
	}

	public static String rarity(ItemStack stack) {
		PetRule rule = PetConfigManager.resolvePetRule(stack);
		return rule == null ? MadokuPetManager.PET_RARITY_COMMON : rule.rarity;
	}

	public static void applyLore(ItemStack stack) {
		PetHudManager.applySupportedPetLore(stack);
	}
}

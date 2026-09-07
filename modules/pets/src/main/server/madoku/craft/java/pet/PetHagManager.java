package madoku.craft.java.pet;

import java.util.List;

import madoku.craft.java.core.rarity.RarityAPIManager;
import madoku.craft.java.core.rarity.RarityAPIManager.Tier;
import madoku.craft.java.pet.PetConfigManager.PetRule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/** Owns the Hag-facing pet trade pool, rarity, and item presentation helpers. */
public final class PetHagManager {
	private PetHagManager() {
	}

	public static void initialize() {
		PetHagAPIManager.registerProvider(new MadokuPetHagProvider());
	}

	public static List<Item> tradeItems() {
		List<Item> items = new java.util.ArrayList<>();
		for (PetRule rule : PetConfigManager.rules().values()) {
			if (rule == null || !rule.enabled) continue;
			Item item = PetEntitiesManager.petItem(rule.petId);
			if (item != null && !items.contains(item)) items.add(item);
		}
		items.sort((left, right) -> BuiltInRegistries.ITEM.getKey(left).toString().compareTo(BuiltInRegistries.ITEM.getKey(right).toString()));
		return List.copyOf(items);
	}

	public static int rarityWeight(String rarity) {
		Tier tier = RarityAPIManager.fromString(PetConfigManager.normalizePetRarity(rarity));
		Tier resolved = tier == null ? Tier.COMMON : tier;
		return Math.max(0, (int) Math.round(RarityAPIManager.resolveWeight(resolved, 0.0D, false)));
	}

	public static ItemStack tradeStack(Item item, int level) {
		ItemStack stack = new ItemStack(item);
		PetEntitiesManager.setPetLevel(stack, level);
		Tier tier = RarityAPIManager.fromString(rarity(stack));
		RarityAPIManager.applyConfiguredRarity(stack, tier == null ? Tier.COMMON : tier);
		applyLore(stack);
		return stack;
	}

	public static ItemCost tradeIngredient(Item item, int level) {
		if (level <= 1) return new ItemCost(Items.EGG, 16);
		CompoundTag levelTag = new CompoundTag();
		levelTag.putInt("madoku-pet-level", level - 1);
		ItemStack requiredStack = new ItemStack(item, 4);
		PetEntitiesManager.setPetLevel(requiredStack, level - 1);
		PetHudManager.applyAbilityLore(requiredStack);
		ItemLore requiredLore = requiredStack.get(DataComponents.LORE);
		return new ItemCost(item, 4).withComponents(builder -> {
			builder.expect(DataComponents.CUSTOM_DATA, CustomData.of(levelTag));
			if (requiredLore != null) builder.expect(DataComponents.LORE, requiredLore);
			return builder;
		});
	}

	public static String rarity(ItemStack stack) {
		PetRule rule = PetConfigManager.resolvePetRule(stack);
		return rule == null ? MadokuPetManager.PET_RARITY_COMMON : rule.rarity;
	}

	public static void applyLore(ItemStack stack) {
		PetHudManager.applySupportedPetLore(stack);
	}
}


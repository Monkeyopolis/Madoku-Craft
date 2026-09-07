package madoku.craft.java.pet;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Provider contract for Madoku pet item presentation. */
public interface PetHudProvider {
	default void initialize() { }
	default void applyAbilityLore(ItemStack stack) { }
	default void applySupportedPetLore(ItemStack stack) { }
	default List<PetHudAPIManager.AbilityHudEntry> abilityEntries(ItemStack stack) { return List.of(); }
}

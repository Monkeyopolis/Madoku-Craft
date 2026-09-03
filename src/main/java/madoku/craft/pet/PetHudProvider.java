package madoku.craft.pet;

import net.minecraft.world.item.ItemStack;

/** Provider contract for Madoku pet item presentation. */
public interface PetHudProvider {
	default void initialize() { }
	default void applyAbilityLore(ItemStack stack) { }
	default void applySupportedPetLore(ItemStack stack) { }
}

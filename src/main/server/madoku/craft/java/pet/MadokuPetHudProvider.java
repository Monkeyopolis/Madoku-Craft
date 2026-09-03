package madoku.craft.java.pet;

import net.minecraft.world.item.ItemStack;

/** Built-in provider backed by the Madoku pet HUD implementation. */
public final class MadokuPetHudProvider implements PetHudProvider {
	@Override public void initialize() { PetHudManager.initialize(); }
	@Override public void applyAbilityLore(ItemStack stack) { PetHudManager.applyAbilityLore(stack); }
	@Override public void applySupportedPetLore(ItemStack stack) { PetHudManager.applySupportedPetLore(stack); }
}

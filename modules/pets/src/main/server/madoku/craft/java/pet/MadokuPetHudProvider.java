package madoku.craft.java.pet;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Built-in provider backed by the Madoku pet HUD implementation. */
public final class MadokuPetHudProvider implements PetHudProvider {
	@Override public void initialize() { PetHudManager.initialize(); }
	@Override public void applyAbilityLore(ItemStack stack) { PetHudManager.applyAbilityLore(stack); }
	@Override public void applySupportedPetLore(ItemStack stack) { PetHudManager.applySupportedPetLore(stack); }
	@Override public List<PetHudAPIManager.AbilityHudEntry> abilityEntries(ItemStack stack) {
		PetConfigManager.PetRule rule = PetConfigManager.resolvePetRule(stack);
		if (rule == null || !rule.enabled) return List.of();
		List<PetHudAPIManager.AbilityHudEntry> entries = new ArrayList<>();
		for (PetConfigManager.PetAbilityRule ability : rule.abilities) {
			entries.add(new PetHudAPIManager.AbilityHudEntry(ability.abilityType, ability.cooldownTicks));
		}
		return List.copyOf(entries);
	}
}

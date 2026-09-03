package madoku.craft.pet;

import net.minecraft.world.item.ItemStack;

/** Built-in provider backed by the Madoku pet configuration implementation. */
public final class MadokuPetConfigProvider implements PetConfigProvider {
	@Override public void initialize() { PetConfigManager.initialize(); }
	@Override public boolean isEnabled() { return PetConfigManager.isEnabled(); }
	@Override public boolean areEntitiesEnabled() { return PetConfigManager.areEntitiesEnabled(); }
	@Override public boolean isValidPet(ItemStack stack) { return PetConfigManager.isValidPet(stack); }
	@Override public String petRarity(ItemStack stack) { return PetConfigManager.petRarity(stack); }
	@Override public String normalizeKey(String value) { return PetConfigManager.normalizeKey(value); }
	@Override public String createClientSyncSnapshot() { return PetConfigManager.createClientSyncSnapshot(); }
	@Override public void applyClientSyncSnapshot(String snapshot) { PetConfigManager.applyClientSyncSnapshot(snapshot); }
	@Override public void resetClientSyncState() { PetConfigManager.resetClientSyncState(); }
}

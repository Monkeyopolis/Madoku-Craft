package madoku.craft.java.pet;

import net.minecraft.world.item.ItemStack;

/** Provider contract for Madoku pet configuration. */
public interface PetConfigProvider {
	default void initialize() { }
	default boolean isEnabled() { return false; }
	default boolean areEntitiesEnabled() { return false; }
	default boolean isValidPet(ItemStack stack) { return false; }
	default String petRarity(ItemStack stack) { return "common"; }
	default String normalizeKey(String value) { return value == null ? "" : value.trim().toLowerCase(); }
	default String createClientSyncSnapshot() { return "{}"; }
	default void applyClientSyncSnapshot(String snapshot) { }
	default void resetClientSyncState() { }
}

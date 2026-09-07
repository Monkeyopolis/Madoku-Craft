package madoku.craft.java.pet;

import net.minecraft.world.item.ItemStack;

/** Public contract for Madoku pet configuration and synchronization. */
public final class PetConfigAPIManager {
	public static final String ROOT_FOLDER = "madoku-craft";
	public static final String PET_FOLDER = "madoku-craft-pets";
	public static final String ENTITY_FOLDER = "madoku-entities";
	public static final String ABILITY_FOLDER = "madoku-abilities";
	private static final PetConfigProvider UNAVAILABLE_PROVIDER = new PetConfigProvider() { };
	private static volatile PetConfigProvider provider = UNAVAILABLE_PROVIDER;

	private PetConfigAPIManager() { }
	public static void registerProvider(PetConfigProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Pet config provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static boolean areEntitiesEnabled() { return provider.areEntitiesEnabled(); }
	public static boolean isValidPet(ItemStack stack) { return provider.isValidPet(stack); }
	public static String petRarity(ItemStack stack) { return provider.petRarity(stack); }
	public static String normalizeKey(String value) { return provider.normalizeKey(value); }
	public static String createClientSyncSnapshot() { return provider.createClientSyncSnapshot(); }
	public static void applyClientSyncSnapshot(String snapshot) { provider.applyClientSyncSnapshot(snapshot); }
	public static void resetClientSyncState() { provider.resetClientSyncState(); }
}

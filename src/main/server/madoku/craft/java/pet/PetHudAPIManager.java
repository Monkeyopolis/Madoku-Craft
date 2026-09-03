package madoku.craft.java.pet;

import net.minecraft.world.item.ItemStack;

/** Public contract for Madoku pet item presentation. */
public final class PetHudAPIManager {
	private static final PetHudProvider UNAVAILABLE_PROVIDER = new PetHudProvider() { };
	private static volatile PetHudProvider provider = UNAVAILABLE_PROVIDER;
	private PetHudAPIManager() { }
	public static void registerProvider(PetHudProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Pet HUD provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void applyAbilityLore(ItemStack stack) { provider.applyAbilityLore(stack); }
	public static void applySupportedPetLore(ItemStack stack) { provider.applySupportedPetLore(stack); }
}

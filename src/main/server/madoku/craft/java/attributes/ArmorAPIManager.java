package madoku.craft.java.attributes;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Public contract for the armor attribute subsystem. */
public final class ArmorAPIManager {
	private static final ArmorProvider UNAVAILABLE_PROVIDER = new ArmorProvider() { };
	private static volatile ArmorProvider provider = UNAVAILABLE_PROVIDER;

	private ArmorAPIManager() {
	}

	public static void registerProvider(ArmorProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Armor provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static boolean isResistanceEnabled() { return provider.isResistanceEnabled(); }
	public static boolean shouldOverrideVanillaArmorDamage(DamageSource source) {
		return provider.shouldOverrideVanillaArmorDamage(source);
	}
	public static float applyCustomArmorDamage(LivingEntity entity, DamageSource source, float amount) {
		return provider.applyCustomArmorDamage(entity, source, amount);
	}
}

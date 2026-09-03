package madoku.craft.attributes;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Public contract for the armor attribute subsystem. */
public final class ArmorAPIManager {
	private ArmorAPIManager() {
	}

	public static void initialize() { MadokuArmorManager.initialize(); }
	public static boolean isEnabled() { return MadokuArmorManager.isEnabled(); }
	public static boolean isResistanceEnabled() { return MadokuArmorManager.isResistanceEnabled(); }
	public static boolean shouldOverrideVanillaArmorDamage(DamageSource source) {
		return MadokuArmorManager.shouldOverrideVanillaArmorDamage(source);
	}
	public static float applyCustomArmorDamage(LivingEntity entity, DamageSource source, float amount) {
		return MadokuArmorManager.applyCustomArmorDamage(entity, source, amount);
	}
}

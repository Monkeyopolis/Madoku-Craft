package madoku.craft.attributes;

import net.minecraft.world.entity.LivingEntity;

/** Public contract for the oxygen attribute subsystem. */
public final class OxygenAPIManager {
	private OxygenAPIManager() {
	}

	public static void initialize() { MadokuOxygenManager.initialize(); }
	public static boolean isEnabled() { return MadokuOxygenManager.isEnabled(); }
	public static int getMaximumOxygenTicksForEntity(LivingEntity entity) {
		return MadokuOxygenManager.getMaximumOxygenTicksForEntity(entity);
	}
	public static void applyClientSynchronizedSettings(boolean enabled, int maximum) {
		MadokuOxygenManager.applyClientSynchronizedSettings(enabled, maximum);
	}
	public static void resetClientSynchronizedSettings() {
		MadokuOxygenManager.resetClientSynchronizedSettings();
	}
}

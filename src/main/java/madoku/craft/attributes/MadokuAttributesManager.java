package madoku.craft.attributes;

import madoku.craft.debug.MadokuDebugManager;
import madoku.craft.debug.MadokuDebugRegistry;
import madoku.craft.armor.MadokuArmorManager;
import madoku.craft.health.MadokuHealthManager;
import madoku.craft.hunger.MadokuHungerManager;
import madoku.craft.luck.MadokuLuckManager;
import madoku.craft.oxygen.MadokuOxygenManager;

public final class MadokuAttributesManager {
	private static volatile AttributesConfigManager.Settings settings = AttributesConfigManager.Settings.defaults();

	private MadokuAttributesManager() {
	}

	public static void initialize() {
		loadStaticConfig();
		MadokuDebugManager.bootstrapHierarchy(MadokuDebugRegistry.ATTRIBUTES);
		MadokuArmorManager.initialize();
		MadokuHealthManager.initialize();
		MadokuHungerManager.initialize();
		MadokuOxygenManager.initialize();
		MadokuLuckManager.initialize();
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	private static void loadStaticConfig() {
		settings = AttributesConfigManager.loadSettings();
	}
}


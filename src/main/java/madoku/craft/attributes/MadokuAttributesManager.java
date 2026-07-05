package madoku.craft.attributes;

import madoku.craft.api.metadata.MadokuMetaDataManager;
import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.attributes.armor.MadokuArmorManager;
import madoku.craft.attributes.health.MadokuHealthManager;
import madoku.craft.attributes.hunger.MadokuHungerManager;
import madoku.craft.attributes.luck.MadokuLuckManager;
import madoku.craft.attributes.oxygen.MadokuOxygenManager;

public final class MadokuAttributesManager {
	private static volatile AttributesConfigManager.Settings settings = AttributesConfigManager.Settings.defaults();

	private MadokuAttributesManager() {
	}

	public static void initialize() {
		loadStaticConfig();
		MadokuMetaDataManager.registerMainSystem(MadokuMetaDataManager.ATTRIBUTES);
		MadokuDebugManager.bootstrapMainSystem(MadokuMetaDataManager.ATTRIBUTES);
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


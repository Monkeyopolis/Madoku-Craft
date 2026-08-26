package madoku.craft.attributes;

public final class MadokuAttributesManager {
	private static volatile AttributesConfigManager.Settings settings = AttributesConfigManager.Settings.defaults();

	private MadokuAttributesManager() {
	}

	public static void initialize() {
		loadStaticConfig();
		MadokuExperienceManager.initialize();
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


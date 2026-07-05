package madoku.craft.attributes;

import madoku.craft.debug.MadokuDebug;
import madoku.craft.armor.MadokuArmorManager;
import madoku.craft.health.MadokuHealthManager;
import madoku.craft.hunger.MadokuHungerManager;
import madoku.craft.luck.MadokuLuckManager;
import madoku.craft.oxygen.MadokuOxygenManager;

import java.util.List;
import java.util.Map;

public final class MadokuAttributesManager {
	private static volatile AttributesConfigManager.Settings settings = AttributesConfigManager.Settings.defaults();

	private MadokuAttributesManager() {
	}

	public static void initialize() {
		loadStaticConfig();
		MadokuDebug.bootstrapHierarchy(
			"attributes",
			Map.ofEntries(
				Map.entry("health", List.of("health", "health-penalty", "absorption", "health-boost", "poison", "regeneration", "wither")),
				Map.entry("hunger", List.of("hunger", "hunger-depletion", "saturation", "hunger-effect", "starvation-penalty")),
				Map.entry("armor", List.of("main", "armor-points", "armor-toughness-points", "resistance")),
				Map.entry("oxygen", List.of("oxygen", "water-breathing", "conduit-power", "dolphins-grace", "breath-of-the-nautilus", "suffocating-penalty")),
				Map.entry("luck", List.of("luck", "luck-effect", "block-drops", "mob-drops", "creeper-grief-reduction", "skeleton-accuracy-reduction", "player-critical-damage"))
			)
		);
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


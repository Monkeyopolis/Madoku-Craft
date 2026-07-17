package madoku.craft.difficulty.system;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RegionalStructureDifficultyConfigManager {
	private RegionalStructureDifficultyConfigManager() {
	}

	public static Map<String, JsonObject> buildDefaultFileDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		defaults.put("difficulty-one", buildRuleDefaults(0, List.of(
			"igloo", "swamp_hut", "buried_treasure", "nether_fossil", "ocean_ruin_cold", "ocean_ruin_warm",
			"village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga"
		)));
		defaults.put("difficulty-two", buildRuleDefaults(1, List.of(
			"trial_ruins", "shipwreck", "shipwreck_beached", "ruined_portal", "ruined_portal_desert",
			"ruined_portal_jungle", "ruined_portal_mountain", "ruined_portal_nether",
			"ruined_portal_ocean", "ruined_portal_swamp"
		)));
		defaults.put("difficulty-three", buildRuleDefaults(2, List.of(
			"jungle_pyramid", "desert_pyramid", "pillager_outpost", "mineshaft", "mineshaft_mesa"
		)));
		defaults.put("difficulty-four", buildRuleDefaults(3, List.of(
			"fortress", "mansion", "monument", "stronghold", "trial_chambers", "end_city",
			"ancient_city", "bastion_remnant"
		)));
		return defaults;
	}

	public static JsonObject buildRuleDefaults(int adjustment, List<String> structureList) {
		return RegionalDifficultyConfigManager.buildStructureRuleDefaults(adjustment, structureList);
	}
}


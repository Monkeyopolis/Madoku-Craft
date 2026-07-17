package madoku.craft.difficulty.system;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RegionalTimeDifficultyConfigManager {
	private RegionalTimeDifficultyConfigManager() {
	}

	public static Map<String, JsonObject> buildDefaultFileDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		defaults.put("difficulty-one", buildRuleDefaults(0, 28, 0));
		defaults.put("difficulty-two", buildRuleDefaults(29, 168, 1));
		defaults.put("difficulty-three", buildRuleDefaults(169, 336, 2));
		defaults.put("difficulty-four", buildRuleDefaults(337, RegionalDifficultyConfigManager.TIME_UNBOUNDED_MAX_DAY, 3));
		return defaults;
	}

	public static JsonObject buildRuleDefaults(int minDay, int maxDay, int adjustment) {
		return RegionalDifficultyConfigManager.buildTimeRuleDefaults(minDay, maxDay, adjustment);
	}

	public static List<RegionalDifficultyConfigManager.TimeTierDefinition> defaultTimeTiers() {
		return RegionalDifficultyConfigManager.defaultTimeTiers();
	}
}


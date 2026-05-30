package madoku.craft.difficulty.system;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RegionalBiomeDifficultyConfigManager {
	private RegionalBiomeDifficultyConfigManager() {
	}

	public static Map<String, JsonObject> buildDefaultFileDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		defaults.put("difficulty-one", buildRuleDefaults(0, List.of(
			"mushroom_fields", "meadow", "cherry_grove", "forest", "flower_forest",
			"taiga", "birch_forest", "sparse_jungle", "river", "beach", "plains",
			"sunflower_plains", "savanna", "old_growth_pine_taiga", "old_growth_spruce_taiga",
			"old_growth_birch_forest"
		)));
		defaults.put("difficulty-two", buildRuleDefaults(1, List.of(
			"grove", "windswept_forest", "frozen_river", "snowy_beach", "snowy_plains",
			"lush_caves", "ocean", "cold_ocean", "lukewarm_ocean", "warm_ocean",
			"jungle", "swamp", "desert", "snowy_taiga"
		)));
		defaults.put("difficulty-three", buildRuleDefaults(2, List.of(
			"stony_peaks", "windswept_hills", "windswept_gravelly_hills", "stony_shore",
			"savanna_plateau", "wooded_badlands", "dripstone_caves", "nether_wastes",
			"deep_ocean", "frozen_ocean", "deep_cold_ocean", "deep_lukewarm_ocean",
			"bamboo_jungle", "dark_forest", "deep_frozen_ocean", "windswept_savanna",
			"snowy_slopes"
		)));
		defaults.put("difficulty-four", buildRuleDefaults(3, List.of(
			"frozen_peaks", "ice_spikes", "badlands", "crimson_forest", "warped_forest",
			"end_midlands", "end_highlands", "small_end_islands", "end_barrens",
			"jagged_peaks", "pale_garden", "eroded_badlands", "deep_dark",
			"soul_sand_valley", "basalt_deltas", "the_end"
		)));
		return defaults;
	}

	public static JsonObject buildRuleDefaults(int adjustment, List<String> biomeList) {
		return RegionalDifficultyConfigManager.buildBiomeRuleDefaults(adjustment, biomeList);
	}
}

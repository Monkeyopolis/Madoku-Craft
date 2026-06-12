package madoku.craft.difficulty.system;

import com.google.gson.JsonObject;

public final class RegionalScalingConfigParched {
	private RegionalScalingConfigParched() {
	}

	public static JsonObject buildDefaults(
		double health,
		double movementSpeed,
		double armor,
		double damage,
		double knockbackResistance,
		double experienceDrop
	) {
		JsonObject root = RegionalScalingConfigManager.buildMobScalingDefaults(
			"minecraft:parched",
			health,
			movementSpeed,
			null,
			null,
			null,
			armor,
			damage,
			knockbackResistance,
			experienceDrop,
			5.0d,
			0.02d,
			null
		);
		root.add(
			RegionalDifficultyConfigManager.FIELD_ATTACK_ACCURACY,
			RegionalDifficultyConfigManager.buildScalingValueRule(RegionalDifficultyConfigManager.SCALING_TYPE_ADD, 0.02d)
		);
		return root;
	}
}

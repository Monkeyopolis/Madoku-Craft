package madoku.craft.difficulty.system;

import com.google.gson.JsonObject;

public final class RegionalScalingConfigSkeleton {
	private RegionalScalingConfigSkeleton() {
	}

	public static JsonObject buildDefaults(
		double health,
		double movementSpeed,
		double armor,
		double damage,
		double knockbackResistance,
		double experienceDrop
	) {
		return madoku.craft.config.JsonFormatBuilder.object()
			.putAll(RegionalScalingConfigManager.buildMobScalingDefaults(
				"minecraft:skeleton",
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
			))
			.put(
				RegionalDifficultyConfigManager.FIELD_ATTACK_ACCURACY,
				RegionalDifficultyConfigManager.buildScalingValueRule(RegionalDifficultyConfigManager.SCALING_TYPE_ADD, 0.02d)
			)
			.build();
	}
}


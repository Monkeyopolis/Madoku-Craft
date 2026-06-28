package madoku.craft.difficulty.system;

import com.google.gson.JsonObject;

public final class RegionalScalingConfigWitherSkeleton {
	private RegionalScalingConfigWitherSkeleton() {
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
				"minecraft:wither_skeleton",
				health,
				movementSpeed,
				null,
				null,
				null,
				armor,
				damage,
				knockbackResistance,
				experienceDrop,
				1.0d,
				1.0d,
				null
			))
			.put(
				RegionalDifficultyConfigManager.FIELD_ATTACK_ACCURACY,
				RegionalDifficultyConfigManager.buildScalingValueRule(RegionalDifficultyConfigManager.SCALING_TYPE_ADD, 0.02d)
			)
			.build();
	}
}


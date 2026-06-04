package madoku.craft.difficulty.system;

import com.google.gson.JsonObject;
import madoku.craft.mob.system.MobConfigManager;

public final class RegionalScalingConfigDrowned {
	private RegionalScalingConfigDrowned() {
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
			"minecraft:drowned",
			health,
			movementSpeed,
			armor,
			damage,
			knockbackResistance,
			experienceDrop,
			null,
			null
		);
		root.add(
			RegionalDifficultyConfigManager.FIELD_RANGED_DAMAGE,
			RegionalDifficultyConfigManager.buildScalingValueRule(
				RegionalDifficultyConfigManager.SCALING_TYPE_MULTIPLY,
				5.0d
			)
		);
		root.add(
			MobConfigManager.FIELD_SWIMMING_SPEED,
			RegionalDifficultyConfigManager.buildScalingValueRule(
				RegionalDifficultyConfigManager.SCALING_TYPE_MULTIPLY,
				2.0d
			)
		);
		return root;
	}
}

package madoku.craft.difficulty.system;

import com.google.gson.JsonObject;

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
		return RegionalScalingConfigManager.buildMobScalingDefaults(
			"minecraft:drowned",
			health,
			movementSpeed,
			2.0d,
			null,
			null,
			armor,
			damage,
			knockbackResistance,
			experienceDrop,
			5.0d,
			null,
			null
		);
	}
}

package madoku.craft.difficulty.system;

import com.google.gson.JsonObject;

public final class RegionalScalingConfigCreeper {
	private RegionalScalingConfigCreeper() {
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
			"minecraft:creeper",
			health,
			movementSpeed,
			null,
			null,
			null,
			armor,
			damage,
			knockbackResistance,
			experienceDrop,
			null,
			null,
			5.0d
		);
	}
}


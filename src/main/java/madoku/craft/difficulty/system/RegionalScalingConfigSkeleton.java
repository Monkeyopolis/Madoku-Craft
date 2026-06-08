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
		return RegionalScalingConfigManager.buildMobScalingDefaults(
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
			2.0d,
			null
		);
	}
}

package madoku.craft.difficulty.system;

import com.google.gson.JsonObject;

public final class RegionalScalingConfigZombie {
	private RegionalScalingConfigZombie() {
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
			"minecraft:zombie",
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
			null
		);
	}
}

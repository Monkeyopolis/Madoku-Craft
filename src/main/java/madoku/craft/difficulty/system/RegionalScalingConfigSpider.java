package madoku.craft.difficulty.system;

import com.google.gson.JsonObject;

public final class RegionalScalingConfigSpider {
	private RegionalScalingConfigSpider() {
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
			"minecraft:spider",
			health,
			movementSpeed,
			null,
			null,
			10.0d,
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


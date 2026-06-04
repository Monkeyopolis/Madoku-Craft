package madoku.craft.difficulty.system;

import com.google.gson.JsonObject;

public final class RegionalScalingConfigZombieVillager {
	private RegionalScalingConfigZombieVillager() {
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
			"minecraft:zombie_villager",
			health,
			movementSpeed,
			armor,
			damage,
			knockbackResistance,
			experienceDrop,
			null,
			null
		);
	}
}

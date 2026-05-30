package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobSystemConfigZombieVillager {
	private MobSystemConfigZombieVillager() {
	}

	public static JsonObject buildDefaults() {
		return MadokuMobConfigManager.buildZombieTypeDefaults(
			MadokuMobConfigManager.FILE_ZOMBIE_VILLAGER,
			MadokuMobConfigManager.FIELD_ADULT_ZOMBIE_VILLAGER,
			MadokuMobConfigManager.FIELD_BABY_ZOMBIE_VILLAGER,
			20.0d,
			10.0d,
			0.0d,
			5.0d,
			2.5d,
			0.25d,
			0.25d,
			1.0d,
			7
		);
	}
}



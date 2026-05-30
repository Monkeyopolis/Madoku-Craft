package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobSystemConfigZombie {
	private MobSystemConfigZombie() {
	}

	public static JsonObject buildDefaults() {
		return MadokuMobConfigManager.buildZombieTypeDefaults(
			MadokuMobConfigManager.FILE_ZOMBIE,
			MadokuMobConfigManager.FIELD_ADULT_ZOMBIE,
			MadokuMobConfigManager.FIELD_BABY_ZOMBIE,
			24.0d,
			12.0d,
			1.0d,
			6.0d,
			3.0d,
			0.2d,
			0.2d,
			1.0d,
			7
		);
	}
}



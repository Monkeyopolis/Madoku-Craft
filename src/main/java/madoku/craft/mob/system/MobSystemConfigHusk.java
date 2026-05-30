package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobSystemConfigHusk {
	private MobSystemConfigHusk() {
	}

	public static JsonObject buildDefaults() {
		return MadokuMobConfigManager.buildZombieTypeDefaults(
			MadokuMobConfigManager.FILE_HUSK,
			MadokuMobConfigManager.FIELD_ADULT_HUSK,
			MadokuMobConfigManager.FIELD_BABY_HUSK,
			20.0d,
			10.0d,
			1.0d,
			5.0d,
			2.5d,
			0.2d,
			0.25d,
			1.0d,
			7
		);
	}
}



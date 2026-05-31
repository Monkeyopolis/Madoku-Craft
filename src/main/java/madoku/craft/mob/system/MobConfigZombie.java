package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigZombie {
	private MobConfigZombie() {
	}

	public static JsonObject buildDefaults() {
		return MobConfigManager.buildZombieTypeDefaults(
			MobConfigManager.FILE_ZOMBIE,
			MobConfigManager.FIELD_ADULT_ZOMBIE,
			MobConfigManager.FIELD_BABY_ZOMBIE,
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




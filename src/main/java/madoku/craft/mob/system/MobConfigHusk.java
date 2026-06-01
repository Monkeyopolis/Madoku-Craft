package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigHusk {
	private MobConfigHusk() {
	}

	public static JsonObject buildDefaults() {
		return MobConfigZombie.buildZombieTypeDefaults(
			MobConfigManager.FILE_HUSK,
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




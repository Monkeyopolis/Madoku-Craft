package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobSystemConfigBogged {
	private MobSystemConfigBogged() {
	}

	public static JsonObject buildDefaults() {
		return MadokuMobConfigManager.buildSkeletonDefaults(
			MadokuMobConfigManager.FILE_BOGGED,
			12.0d,
			0.0d,
			2.0d,
			0.25d,
			0.0d,
			1.0d,
			7,
			4.0d
		);
	}
}



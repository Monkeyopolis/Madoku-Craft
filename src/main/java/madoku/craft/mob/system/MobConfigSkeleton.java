package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigSkeleton {
	private MobConfigSkeleton() {
	}

	public static JsonObject buildDefaults() {
		return MobConfigManager.buildSkeletonDefaults(
			MobConfigManager.FILE_SKELETON,
			16.0d,
			0.0d,
			3.0d,
			0.25d,
			0.0d,
			1.0d,
			7,
			5.0d
		);
	}
}




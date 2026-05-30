package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobSystemConfigHag {
	private MobSystemConfigHag() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MadokuMobConfigManager.FIELD_ENABLED, true);
		JsonObject hag = new JsonObject();
		hag.add(
			MadokuMobConfigManager.FIELD_MOB_STATS,
			MadokuMobConfigManager.buildMobStatsDefaults(40.0d, 1.0d, null, 0.25d, 0.2d, null, 11)
		);
		MadokuMobConfigManager.ensureMobSchema(hag, false, false);
		root.add(MadokuMobConfigManager.FILE_HAG, hag);
		return root;
	}
}



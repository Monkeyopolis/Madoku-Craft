package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigHag {
	private MobConfigHag() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		JsonObject hag = new JsonObject();
		hag.add(
			MobConfigManager.FIELD_MOB_STATS,
			MobConfigManager.buildMobStatsDefaults(40.0d, 1.0d, null, 0.25d, 0.2d, null, 11)
		);
		MobConfigManager.ensureMobSchema(hag, false, false);
		root.add(MobConfigManager.FILE_HAG, hag);
		return root;
	}
}




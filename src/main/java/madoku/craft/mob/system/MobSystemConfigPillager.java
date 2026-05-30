package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobSystemConfigPillager {
	private MobSystemConfigPillager() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MadokuMobConfigManager.FIELD_ENABLED, true);
		JsonObject pillager = new JsonObject();
		JsonObject mobStats = MadokuMobConfigManager.buildMobStatsDefaults(20.0d, 1.0d, 5.0d, 0.25d, 0.1d, 1.0d, 11);
		mobStats.addProperty(MadokuMobConfigManager.FIELD_RANGED_DAMAGE, 6.0d);
		mobStats.addProperty(MadokuMobConfigManager.FIELD_ATTACK_INTERVAL, 20.0d);
		mobStats.addProperty(MadokuMobConfigManager.FIELD_ATTACK_ACCURACY, 0.7d);
		mobStats.addProperty(MadokuMobConfigManager.FIELD_CHARGE_UP_TICKS, 10.0d);
		pillager.add(MadokuMobConfigManager.FIELD_MOB_STATS, mobStats);
		MadokuMobConfigManager.ensureMobSchema(pillager, false, false);
		root.add(MadokuMobConfigManager.FILE_PILLAGER, pillager);
		return root;
	}
}



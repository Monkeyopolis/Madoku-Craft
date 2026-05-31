package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigPillager {
	private MobConfigPillager() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		JsonObject pillager = new JsonObject();
		JsonObject mobStats = MobConfigManager.buildMobStatsDefaults(20.0d, 1.0d, 5.0d, 0.25d, 0.1d, 1.0d, 11);
		mobStats.addProperty(MobConfigManager.FIELD_RANGED_DAMAGE, 6.0d);
		mobStats.addProperty(MobConfigManager.FIELD_ATTACK_INTERVAL, 20.0d);
		mobStats.addProperty(MobConfigManager.FIELD_ATTACK_ACCURACY, 0.7d);
		mobStats.addProperty(MobConfigManager.FIELD_CHARGE_UP_TICKS, 10.0d);
		pillager.add(MobConfigManager.FIELD_MOB_STATS, mobStats);
		MobConfigManager.ensureMobSchema(pillager, false, false);
		root.add(MobConfigManager.FILE_PILLAGER, pillager);
		return root;
	}
}




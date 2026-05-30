package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobSystemConfigWitherSkeleton {
	private MobSystemConfigWitherSkeleton() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MadokuMobConfigManager.FIELD_ENABLED, true);
		JsonObject witherSkeleton = new JsonObject();
		MadokuMobConfigManager.addArmorSpawnDefaults(witherSkeleton);
		JsonObject spawnRules = MadokuMobConfigManager.getOrCreateObject(witherSkeleton, MadokuMobConfigManager.FIELD_SPAWN_RULES);
		spawnRules.addProperty(MadokuMobConfigManager.FIELD_WITHER_SWORD_SPAWN_WEIGHT, 90.0d);
		spawnRules.addProperty(MadokuMobConfigManager.FIELD_WITHER_BOW_SPAWN_WEIGHT, 10.0d);
		JsonObject mobStats = MadokuMobConfigManager.buildMobStatsDefaults(20.0d, 0.0d, 7.0d, 0.25d, 0.0d, 1.0d, 11);
		mobStats.addProperty(MadokuMobConfigManager.FIELD_RANGED_DAMAGE, 6.0d);
		mobStats.addProperty(MadokuMobConfigManager.FIELD_ATTACK_INTERVAL, 20.0d);
		mobStats.addProperty(MadokuMobConfigManager.FIELD_ATTACK_ACCURACY, 0.7d);
		mobStats.addProperty(MadokuMobConfigManager.FIELD_CHARGE_UP_TICKS, 10.0d);
		witherSkeleton.add(MadokuMobConfigManager.FIELD_MOB_STATS, mobStats);
		MadokuMobConfigManager.ensureMobSchema(witherSkeleton, false, false);
		root.add(MadokuMobConfigManager.FILE_WITHER_SKELETON, witherSkeleton);
		return root;
	}
}



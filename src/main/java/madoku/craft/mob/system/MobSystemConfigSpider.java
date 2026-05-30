package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobSystemConfigSpider {
	private MobSystemConfigSpider() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MadokuMobConfigManager.FIELD_ENABLED, true);
		JsonObject spider = new JsonObject();
		JsonObject spawnRules = MadokuMobConfigManager.getOrCreateObject(spider, MadokuMobConfigManager.FIELD_SPAWN_RULES);
		spawnRules.addProperty(MadokuMobConfigManager.FIELD_SCALE_DIFFICULTY_STEP, 0.05d);
		spawnRules.addProperty(MadokuMobConfigManager.FIELD_SPIDER_SPAWN_WEIGHT, 90.0d);
		spawnRules.addProperty(MadokuMobConfigManager.FIELD_CAVE_SPIDER_SPAWN_WEIGHT, 5.0d);
		spawnRules.addProperty(MadokuMobConfigManager.FIELD_SPIDER_JOCKEY_SPAWN_WEIGHT, 5.0d);
		spider.add(
			MadokuMobConfigManager.FIELD_MOB_STATS,
			MadokuMobConfigManager.buildMobStatsDefaults(16.0d, 0.0d, 4.0d, 0.3d, 0.0d, 0.7d, 7)
		);
		MadokuMobConfigManager.ensureMobSchema(spider, false, false);
		root.add(MadokuMobConfigManager.FILE_SPIDER, spider);
		return root;
	}
}



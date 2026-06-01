package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigSpider {
	private MobConfigSpider() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		JsonObject spider = new JsonObject();
		JsonObject spawnRules = MobConfigManager.getOrCreateObject(spider, MobConfigManager.FIELD_SPAWN_RULES);
		spawnRules.addProperty(MobConfigManager.FIELD_SPIDER_SPAWN_WEIGHT, 90.0d);
		spawnRules.addProperty(MobConfigManager.FIELD_CAVE_SPIDER_SPAWN_WEIGHT, 5.0d);
		spawnRules.addProperty(MobConfigManager.FIELD_SPIDER_JOCKEY_SPAWN_WEIGHT, 5.0d);
		spider.add(
			MobConfigManager.FIELD_MOB_STATS,
			MobConfigManager.buildMobStatsDefaults(16.0d, 0.0d, 4.0d, 0.3d, 0.0d, 0.7d, 7)
		);
		MobConfigManager.ensureMobSchema(spider, false, false);
		root.add(MobConfigManager.FILE_SPIDER, spider);
		return root;
	}
}




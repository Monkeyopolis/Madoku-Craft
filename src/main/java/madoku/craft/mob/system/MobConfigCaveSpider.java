package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigCaveSpider {
	private MobConfigCaveSpider() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		JsonObject caveSpider = new JsonObject();
		JsonObject spawnRules = MobConfigManager.getOrCreateObject(caveSpider, MobConfigManager.FIELD_SPAWN_RULES);
		spawnRules.addProperty(MobConfigManager.FIELD_SCALE_DIFFICULTY_STEP, 0.05d);
		caveSpider.add(
			MobConfigManager.FIELD_MOB_STATS,
			MobConfigManager.buildMobStatsDefaults(12.0d, 0.0d, 3.0d, 0.3d, 0.0d, 0.7d, 7)
		);
		MobConfigManager.ensureMobSchema(caveSpider, false, false);
		root.add(MobConfigManager.FILE_CAVE_SPIDER, caveSpider);
		return root;
	}
}




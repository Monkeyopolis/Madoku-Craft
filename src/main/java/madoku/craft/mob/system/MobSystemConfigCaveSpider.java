package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobSystemConfigCaveSpider {
	private MobSystemConfigCaveSpider() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MadokuMobConfigManager.FIELD_ENABLED, true);
		JsonObject caveSpider = new JsonObject();
		JsonObject spawnRules = MadokuMobConfigManager.getOrCreateObject(caveSpider, MadokuMobConfigManager.FIELD_SPAWN_RULES);
		spawnRules.addProperty(MadokuMobConfigManager.FIELD_SCALE_DIFFICULTY_STEP, 0.05d);
		caveSpider.add(
			MadokuMobConfigManager.FIELD_MOB_STATS,
			MadokuMobConfigManager.buildMobStatsDefaults(12.0d, 0.0d, 3.0d, 0.3d, 0.0d, 0.7d, 7)
		);
		MadokuMobConfigManager.ensureMobSchema(caveSpider, false, false);
		root.add(MadokuMobConfigManager.FILE_CAVE_SPIDER, caveSpider);
		return root;
	}
}



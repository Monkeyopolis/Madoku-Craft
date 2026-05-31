package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigCreeper {
	private MobConfigCreeper() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);

		JsonObject creeper = new JsonObject();
		JsonObject creeperStats = MobConfigManager.buildMobStatsDefaults(12.0d, 1.0d, null, 0.25d, 0.1d, null, 7);
		creeperStats.addProperty(MobConfigManager.FIELD_GRIEF_POWER_MULTIPLIER, 0.5d);
		creeperStats.addProperty(MobConfigManager.FIELD_EXPLOSION_POWER, 3.0d);
		creeperStats.addProperty(MobConfigManager.FIELD_EXPLOSION_DESTRUCTION_CHANCE, 0.4d);
		creeperStats.addProperty(MobConfigManager.FIELD_FUSE_LENGTH, 30.0d);
		creeper.add(MobConfigManager.FIELD_MOB_STATS, creeperStats);
		JsonObject creeperSpawnRules = MobConfigManager.getOrCreateObject(creeper, MobConfigManager.FIELD_SPAWN_RULES);
		creeperSpawnRules.addProperty(MobConfigManager.FIELD_EXPLOSION_DESTRUCTION_DIFFICULTY_STEP, 0.2d);
		creeperSpawnRules.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, 95.0d);

		JsonObject charged = new JsonObject();
		JsonObject chargedStats = MobConfigManager.buildMobStatsDefaults(12.0d, 1.0d, null, 0.3d, 0.2d, null, 11);
		chargedStats.addProperty(MobConfigManager.FIELD_GRIEF_POWER_MULTIPLIER, 0.5d);
		chargedStats.addProperty(MobConfigManager.FIELD_EXPLOSION_POWER, 5.0d);
		chargedStats.addProperty(MobConfigManager.FIELD_EXPLOSION_DESTRUCTION_CHANCE, 0.6d);
		chargedStats.addProperty(MobConfigManager.FIELD_FUSE_LENGTH, 24.0d);
		charged.add(MobConfigManager.FIELD_MOB_STATS, chargedStats);
		JsonObject chargedSpawnRules = MobConfigManager.getOrCreateObject(charged, MobConfigManager.FIELD_SPAWN_RULES);
		chargedSpawnRules.addProperty(MobConfigManager.FIELD_EXPLOSION_DESTRUCTION_DIFFICULTY_STEP, 0.2d);
		chargedSpawnRules.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, 5.0d);

		MobConfigManager.ensureMobSchema(creeper, false, false);
		MobConfigManager.ensureMobSchema(charged, false, false);
		root.add(MobConfigManager.FIELD_CREEPER, creeper);
		root.add(MobConfigManager.FIELD_CHARGED_CREEPER, charged);
		return root;
	}
}




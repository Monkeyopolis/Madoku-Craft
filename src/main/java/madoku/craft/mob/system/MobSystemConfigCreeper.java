package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobSystemConfigCreeper {
	private MobSystemConfigCreeper() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MadokuMobConfigManager.FIELD_ENABLED, true);

		JsonObject creeper = new JsonObject();
		JsonObject creeperStats = MadokuMobConfigManager.buildMobStatsDefaults(12.0d, 1.0d, null, 0.25d, 0.1d, null, 7);
		creeperStats.addProperty(MadokuMobConfigManager.FIELD_GRIEF_POWER_MULTIPLIER, 0.5d);
		creeperStats.addProperty(MadokuMobConfigManager.FIELD_EXPLOSION_POWER, 3.0d);
		creeperStats.addProperty(MadokuMobConfigManager.FIELD_EXPLOSION_DESTRUCTION_CHANCE, 0.4d);
		creeperStats.addProperty(MadokuMobConfigManager.FIELD_FUSE_LENGTH, 30.0d);
		creeper.add(MadokuMobConfigManager.FIELD_MOB_STATS, creeperStats);
		JsonObject creeperSpawnRules = MadokuMobConfigManager.getOrCreateObject(creeper, MadokuMobConfigManager.FIELD_SPAWN_RULES);
		creeperSpawnRules.addProperty(MadokuMobConfigManager.FIELD_EXPLOSION_DESTRUCTION_DIFFICULTY_STEP, 0.2d);
		creeperSpawnRules.addProperty(MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 95.0d);

		JsonObject charged = new JsonObject();
		JsonObject chargedStats = MadokuMobConfigManager.buildMobStatsDefaults(12.0d, 1.0d, null, 0.3d, 0.2d, null, 11);
		chargedStats.addProperty(MadokuMobConfigManager.FIELD_GRIEF_POWER_MULTIPLIER, 0.5d);
		chargedStats.addProperty(MadokuMobConfigManager.FIELD_EXPLOSION_POWER, 5.0d);
		chargedStats.addProperty(MadokuMobConfigManager.FIELD_EXPLOSION_DESTRUCTION_CHANCE, 0.6d);
		chargedStats.addProperty(MadokuMobConfigManager.FIELD_FUSE_LENGTH, 24.0d);
		charged.add(MadokuMobConfigManager.FIELD_MOB_STATS, chargedStats);
		JsonObject chargedSpawnRules = MadokuMobConfigManager.getOrCreateObject(charged, MadokuMobConfigManager.FIELD_SPAWN_RULES);
		chargedSpawnRules.addProperty(MadokuMobConfigManager.FIELD_EXPLOSION_DESTRUCTION_DIFFICULTY_STEP, 0.2d);
		chargedSpawnRules.addProperty(MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 5.0d);

		MadokuMobConfigManager.ensureMobSchema(creeper, false, false);
		MadokuMobConfigManager.ensureMobSchema(charged, false, false);
		root.add(MadokuMobConfigManager.FIELD_CREEPER, creeper);
		root.add(MadokuMobConfigManager.FIELD_CHARGED_CREEPER, charged);
		return root;
	}
}



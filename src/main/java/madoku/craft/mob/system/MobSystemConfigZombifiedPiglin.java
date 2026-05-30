package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobSystemConfigZombifiedPiglin {
	private MobSystemConfigZombifiedPiglin() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MadokuMobConfigManager.FIELD_ENABLED, true);
		JsonObject pigman = new JsonObject();
		pigman.add(MadokuMobConfigManager.FIELD_ADULT_ZOMBIFIED_PIGLIN, buildZombifiedPiglinAdultVariant());
		pigman.add(MadokuMobConfigManager.FIELD_BABY_ZOMBIFIED_PIGLIN, buildZombifiedPiglinBabyVariant());
		root.add(MadokuMobConfigManager.FILE_ZOMBIFIED_PIGLIN, pigman);
		return root;
	}

	private static JsonObject buildZombifiedPiglinAdultVariant() {
		JsonObject root = new JsonObject();
		root.add(
			MadokuMobConfigManager.FIELD_MOB_STATS,
			MadokuMobConfigManager.buildMobStatsDefaults(24.0d, 1.0d, 6.0d, 0.25d, 0.1d, 1.0d, 11)
		);
		MadokuMobConfigManager.addArmorSpawnDefaults(root);
		MadokuMobConfigManager.applyIronArmorDefaults(root);
		JsonObject spawnRules = MadokuMobConfigManager.getOrCreateObject(root, MadokuMobConfigManager.FIELD_SPAWN_RULES);
		spawnRules.addProperty(MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 90.0d);
		MadokuMobConfigManager.ensureMobSchema(root, false, false);
		return root;
	}

	private static JsonObject buildZombifiedPiglinBabyVariant() {
		JsonObject root = new JsonObject();
		root.add(
			MadokuMobConfigManager.FIELD_MOB_STATS,
			MadokuMobConfigManager.buildMobStatsDefaults(12.0d, 0.0d, 3.0d, 0.25d, 0.0d, 1.0d, 3)
		);
		MadokuMobConfigManager.addArmorSpawnDefaults(root);
		MadokuMobConfigManager.applyIronArmorDefaults(root);
		JsonObject spawnRules = MadokuMobConfigManager.getOrCreateObject(root, MadokuMobConfigManager.FIELD_SPAWN_RULES);
		spawnRules.addProperty(MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 10.0d);
		MadokuMobConfigManager.ensureMobSchema(root, false, false);
		return root;
	}
}



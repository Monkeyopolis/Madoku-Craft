package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigZombifiedPiglin {
	private MobConfigZombifiedPiglin() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		JsonObject pigman = new JsonObject();
		pigman.add(MobConfigManager.FIELD_ADULT_ZOMBIFIED_PIGLIN, buildZombifiedPiglinAdultVariant());
		pigman.add(MobConfigManager.FIELD_BABY_ZOMBIFIED_PIGLIN, buildZombifiedPiglinBabyVariant());
		root.add(MobConfigManager.FILE_ZOMBIFIED_PIGLIN, pigman);
		return root;
	}

	private static JsonObject buildZombifiedPiglinAdultVariant() {
		JsonObject root = new JsonObject();
		root.add(
			MobConfigManager.FIELD_MOB_STATS,
			MobConfigManager.buildMobStatsDefaults(24.0d, 1.0d, 6.0d, 0.25d, 0.1d, 1.0d, 11)
		);
		MobConfigManager.addArmorSpawnDefaults(root);
		MobConfigManager.applyIronArmorDefaults(root);
		JsonObject spawnRules = MobConfigManager.getOrCreateObject(root, MobConfigManager.FIELD_SPAWN_RULES);
		spawnRules.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, 90.0d);
		MobConfigManager.ensureMobSchema(root, false, false);
		return root;
	}

	private static JsonObject buildZombifiedPiglinBabyVariant() {
		JsonObject root = new JsonObject();
		root.add(
			MobConfigManager.FIELD_MOB_STATS,
			MobConfigManager.buildMobStatsDefaults(12.0d, 0.0d, 3.0d, 0.25d, 0.0d, 1.0d, 3)
		);
		MobConfigManager.addArmorSpawnDefaults(root);
		MobConfigManager.applyIronArmorDefaults(root);
		JsonObject spawnRules = MobConfigManager.getOrCreateObject(root, MobConfigManager.FIELD_SPAWN_RULES);
		spawnRules.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, 10.0d);
		MobConfigManager.ensureMobSchema(root, false, false);
		return root;
	}
}




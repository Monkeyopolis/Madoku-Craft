package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobSystemConfigPiglin {
	private MobSystemConfigPiglin() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MadokuMobConfigManager.FIELD_ENABLED, true);
		JsonObject piglin = new JsonObject();
		piglin.add(MadokuMobConfigManager.FIELD_ADULT_PIGLIN, buildPiglinAdultVariant());
		piglin.add(MadokuMobConfigManager.FIELD_BABY_PIGLIN, buildPiglinBabyVariant());
		root.add(MadokuMobConfigManager.FILE_PIGLIN, piglin);
		return root;
	}

	private static JsonObject buildPiglinAdultVariant() {
		JsonObject root = new JsonObject();
		root.add(
			MadokuMobConfigManager.FIELD_MOB_STATS,
			MadokuMobConfigManager.buildMobStatsDefaults(24.0d, 1.0d, 6.0d, 0.25d, 0.1d, 1.0d, 11)
		);
		JsonObject mobStats = MadokuMobConfigManager.getOrCreateObject(root, MadokuMobConfigManager.FIELD_MOB_STATS);
		mobStats.addProperty(MadokuMobConfigManager.FIELD_RANGED_DAMAGE, 5.0d);
		mobStats.addProperty(MadokuMobConfigManager.FIELD_ATTACK_INTERVAL, 20.0d);
		mobStats.addProperty(MadokuMobConfigManager.FIELD_ATTACK_ACCURACY, 0.7d);
		mobStats.addProperty(MadokuMobConfigManager.FIELD_CHARGE_UP_TICKS, 10.0d);
		MadokuMobConfigManager.addArmorSpawnDefaults(root);
		MadokuMobConfigManager.applyPiglinGoldArmorDefaults(root);
		JsonObject spawnRules = MadokuMobConfigManager.getOrCreateObject(root, MadokuMobConfigManager.FIELD_SPAWN_RULES);
		spawnRules.addProperty(MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 90.0d);
		spawnRules.addProperty(MadokuMobConfigManager.FIELD_CROSSBOW_SPAWN_WEIGHT, 50.0d);
		spawnRules.addProperty(MadokuMobConfigManager.FIELD_GOLDEN_SWORD_SPAWN_WEIGHT, 50.0d);
		MadokuMobConfigManager.ensureMobSchema(root, false, false);
		return root;
	}

	private static JsonObject buildPiglinBabyVariant() {
		JsonObject root = new JsonObject();
		root.add(
			MadokuMobConfigManager.FIELD_MOB_STATS,
			MadokuMobConfigManager.buildMobStatsDefaults(12.0d, 0.0d, 3.0d, 0.25d, 0.0d, 1.0d, 3)
		);
		JsonObject mobStats = MadokuMobConfigManager.getOrCreateObject(root, MadokuMobConfigManager.FIELD_MOB_STATS);
		mobStats.addProperty(MadokuMobConfigManager.FIELD_RANGED_DAMAGE, 5.0d);
		mobStats.addProperty(MadokuMobConfigManager.FIELD_ATTACK_INTERVAL, 20.0d);
		mobStats.addProperty(MadokuMobConfigManager.FIELD_ATTACK_ACCURACY, 0.7d);
		mobStats.addProperty(MadokuMobConfigManager.FIELD_CHARGE_UP_TICKS, 10.0d);
		MadokuMobConfigManager.addArmorSpawnDefaults(root);
		MadokuMobConfigManager.applyPiglinGoldArmorDefaults(root);
		JsonObject spawnRules = MadokuMobConfigManager.getOrCreateObject(root, MadokuMobConfigManager.FIELD_SPAWN_RULES);
		spawnRules.addProperty(MadokuMobConfigManager.FIELD_SPAWN_WEIGHT, 10.0d);
		MadokuMobConfigManager.ensureMobSchema(root, false, false);
		return root;
	}
}



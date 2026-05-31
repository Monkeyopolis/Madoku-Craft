package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigPiglin {
	private MobConfigPiglin() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		JsonObject piglin = new JsonObject();
		piglin.add(MobConfigManager.FIELD_ADULT_PIGLIN, buildPiglinAdultVariant());
		piglin.add(MobConfigManager.FIELD_BABY_PIGLIN, buildPiglinBabyVariant());
		root.add(MobConfigManager.FILE_PIGLIN, piglin);
		return root;
	}

	private static JsonObject buildPiglinAdultVariant() {
		JsonObject root = new JsonObject();
		root.add(
			MobConfigManager.FIELD_MOB_STATS,
			MobConfigManager.buildMobStatsDefaults(24.0d, 1.0d, 6.0d, 0.25d, 0.1d, 1.0d, 11)
		);
		JsonObject mobStats = MobConfigManager.getOrCreateObject(root, MobConfigManager.FIELD_MOB_STATS);
		mobStats.addProperty(MobConfigManager.FIELD_RANGED_DAMAGE, 5.0d);
		mobStats.addProperty(MobConfigManager.FIELD_ATTACK_INTERVAL, 20.0d);
		mobStats.addProperty(MobConfigManager.FIELD_ATTACK_ACCURACY, 0.7d);
		mobStats.addProperty(MobConfigManager.FIELD_CHARGE_UP_TICKS, 10.0d);
		MobConfigManager.addArmorSpawnDefaults(root);
		MobConfigManager.applyPiglinGoldArmorDefaults(root);
		JsonObject spawnRules = MobConfigManager.getOrCreateObject(root, MobConfigManager.FIELD_SPAWN_RULES);
		spawnRules.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, 90.0d);
		spawnRules.addProperty(MobConfigManager.FIELD_CROSSBOW_SPAWN_WEIGHT, 50.0d);
		spawnRules.addProperty(MobConfigManager.FIELD_GOLDEN_SWORD_SPAWN_WEIGHT, 50.0d);
		MobConfigManager.ensureMobSchema(root, false, false);
		return root;
	}

	private static JsonObject buildPiglinBabyVariant() {
		JsonObject root = new JsonObject();
		root.add(
			MobConfigManager.FIELD_MOB_STATS,
			MobConfigManager.buildMobStatsDefaults(12.0d, 0.0d, 3.0d, 0.25d, 0.0d, 1.0d, 3)
		);
		JsonObject mobStats = MobConfigManager.getOrCreateObject(root, MobConfigManager.FIELD_MOB_STATS);
		mobStats.addProperty(MobConfigManager.FIELD_RANGED_DAMAGE, 5.0d);
		mobStats.addProperty(MobConfigManager.FIELD_ATTACK_INTERVAL, 20.0d);
		mobStats.addProperty(MobConfigManager.FIELD_ATTACK_ACCURACY, 0.7d);
		mobStats.addProperty(MobConfigManager.FIELD_CHARGE_UP_TICKS, 10.0d);
		MobConfigManager.addArmorSpawnDefaults(root);
		MobConfigManager.applyPiglinGoldArmorDefaults(root);
		JsonObject spawnRules = MobConfigManager.getOrCreateObject(root, MobConfigManager.FIELD_SPAWN_RULES);
		spawnRules.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, 10.0d);
		MobConfigManager.ensureMobSchema(root, false, false);
		return root;
	}
}




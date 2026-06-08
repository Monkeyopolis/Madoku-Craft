package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigHag {
	private MobConfigHag() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_STATS, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_GOALS, true);
		root.addProperty(MobConfigManager.FIELD_MOB_VARIANT, false);

		JsonObject hag = new JsonObject();
		hag.addProperty(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true);
		hag.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALING, true);

		JsonObject difficultyScale = new JsonObject();
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d);
		hag.add(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale);

		JsonObject regionalDifficultyScale = new JsonObject();
		regionalDifficultyScale.addProperty(MobConfigManager.FIELD_ENABLED, true);
		hag.add(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale);

		JsonObject defaultGroup = new JsonObject();
		defaultGroup.add(
			MobConfigManager.FIELD_MOB_STATS,
			MobConfigManager.buildMobStatsDefaults(40.0d, 1.0d, null, 0.25d, 0.2d, null, 11)
		);
		defaultGroup.add(MobConfigManager.FIELD_SPAWN_RULES, MobConfigManager.buildMobSpawnRulesDefaults());
		MobConfigManager.ensureMobSchema(defaultGroup, false);
		hag.add(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup);

		root.add(MobConfigManager.FILE_HAG, hag);
		return root;
	}
}

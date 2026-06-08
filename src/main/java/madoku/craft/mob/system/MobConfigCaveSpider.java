package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigCaveSpider {
	private MobConfigCaveSpider() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_STATS, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_GOALS, true);
		root.addProperty(MobConfigManager.FIELD_MOB_VARIANT, false);

		JsonObject caveSpider = new JsonObject();
		caveSpider.addProperty(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true);
		caveSpider.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALING, true);

		JsonObject difficultyScale = new JsonObject();
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d);
		caveSpider.add(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale);

		JsonObject regionalDifficultyScale = new JsonObject();
		regionalDifficultyScale.addProperty(MobConfigManager.FIELD_ENABLED, true);
		caveSpider.add(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale);

		JsonObject defaultGroup = new JsonObject();
		defaultGroup.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, 100.0d);
		defaultGroup.add(
			MobConfigManager.FIELD_MOB_STATS,
			buildCaveSpiderMobStatsDefaults()
		);
		defaultGroup.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			buildCaveSpiderSpawnRulesDefaults()
		);
		defaultGroup.add(
			MobConfigManager.FIELD_MOB_BEHAVIOR,
			MobConfigManager.buildMobBehaviorDefaults(behavior ->
				behavior.addProperty(MobConfigManager.FIELD_RETALIATE_WHEN_HURT, true)
			)
		);
		defaultGroup.add(
			MobConfigManager.FIELD_MOB_GOALS,
			MobConfigManager.buildMobGoalsDefaults(goals -> {
				MobConfigManager.addMobGoal(goals, "hurt-by-target", true, 1, 100.0d, 0);
				MobConfigManager.addMobGoal(goals, "target-player", true, 2, 100.0d, 0);
				MobConfigManager.addMobGoal(goals, "melee-attack", true, 4, 100.0d, 20);
			})
		);
		caveSpider.add(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup);
		root.add(MobConfigManager.FILE_CAVE_SPIDER, caveSpider);
		return root;
	}

	private static JsonObject buildCaveSpiderMobStatsDefaults() {
		JsonObject mobStats = MobConfigManager.buildMobStatsDefaults(
			12.0d,
			null,
			5.0d,
			0.27d,
			null,
			null,
			null,
			0.5d,
			7,
			null,
			null,
			null,
			null,
			"minecraft-entities-cave-spider.json"
		);
		mobStats.add(
			MobConfigManager.FIELD_MOB_EFFECT,
			MobConfigManager.buildMobEffectDefaults("minecraft:poison", 15)
		);
		return mobStats;
	}

	private static JsonObject buildCaveSpiderSpawnRulesDefaults() {
		return MobConfigManager.mobSpawnRules().spawnWeight(100.0d).build();
	}
}

package madoku.craft.mob.system;

import com.google.gson.JsonObject;

import madoku.craft.config.JsonFormatBuilder;

public final class MobConfigCaveSpider {
	private MobConfigCaveSpider() {
	}

	public static JsonObject buildDefaults() {
		JsonObject defaultGroup = JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_SPAWN_WEIGHT, 100.0d)
			.put(MobConfigManager.FIELD_MOB_STATS, buildCaveSpiderMobStatsDefaults())
			.put(MobConfigManager.FIELD_SPAWN_RULES, buildCaveSpiderSpawnRulesDefaults())
			.put(
				MobConfigManager.FIELD_MOB_BEHAVIOR,
				MobConfigManager.buildMobBehaviorDefaults(behavior ->
					behavior.addProperty(MobConfigManager.FIELD_RETALIATE_WHEN_HURT, true)
				)
			)
			.put(
				MobConfigManager.FIELD_MOB_GOALS,
				MobConfigManager.buildMobGoalsDefaults(goals -> {
					MobConfigManager.addMobGoal(goals, "hurt-by-target", true, 1, 100.0d, 0);
					MobConfigManager.addMobGoal(goals, "target-player", true, 2, 100.0d, 0);
					MobConfigManager.addMobGoal(goals, "melee-attack", true, 4, 100.0d, 20);
				})
			)
			.build();

		JsonObject caveSpider = JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true)
			.put(MobConfigManager.FIELD_DIFFICULTY_SCALING, true)
			.object(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale -> difficultyScale
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d))
			.object(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale ->
				regionalDifficultyScale.put(MobConfigManager.FIELD_ENABLED, true))
			.put(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup)
			.build();

		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_OVERRIDE_STATS, true)
			.put(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true)
			.put(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true)
			.put(MobConfigManager.FIELD_OVERRIDE_GOALS, true)
			.put(MobConfigManager.FIELD_MOB_VARIANT, false)
			.put(MobConfigManager.FILE_CAVE_SPIDER, caveSpider)
			.build();
	}

	private static JsonObject buildCaveSpiderMobStatsDefaults() {
		return JsonFormatBuilder.object()
			.putAll(MobConfigManager.buildMobStatsDefaults(
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
			))
			.put(MobConfigManager.FIELD_MOB_EFFECT, MobConfigManager.buildMobEffectDefaults("minecraft:poison", 15))
			.build();
	}

	private static JsonObject buildCaveSpiderSpawnRulesDefaults() {
		return MobConfigManager.mobSpawnRules().spawnWeight(100.0d).build();
	}
}


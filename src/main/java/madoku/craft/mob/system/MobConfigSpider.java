package madoku.craft.mob.system;

import com.google.gson.JsonObject;

import madoku.craft.config.JsonFormatBuilder;

public final class MobConfigSpider {
	private static final String DEFAULT_MOB_DROPS = "minecraft-entities-spider.json";

	private MobConfigSpider() {
	}

	public static JsonObject buildDefaults() {
		JsonObject defaultGroup = JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_MOB_STATS, buildSpiderStatsDefaults())
			.put(MobConfigManager.FIELD_SPAWN_RULES, buildSpiderVariantSpawnRulesDefaults(80.0d))
			.put(MobConfigManager.FIELD_MOB_BEHAVIOR, buildSpiderBehaviorDefaults())
			.put(MobConfigManager.FIELD_MOB_GOALS, buildSpiderGoalsDefaults())
			.build();

		JsonObject spider = JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true)
			.put(MobConfigManager.FIELD_DIFFICULTY_SCALING, true)
			.object(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale -> difficultyScale
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_SCALE, 0.10d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d))
			.object(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale ->
				regionalDifficultyScale.put(MobConfigManager.FIELD_ENABLED, true))
			.put(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup)
			.put("cave-spider", buildCaveSpiderVariantDefaults())
			.put("spider-jockey", buildSpiderJockeyVariantDefaults())
			.build();

		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_OVERRIDE_STATS, true)
			.put(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true)
			.put(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true)
			.put(MobConfigManager.FIELD_OVERRIDE_GOALS, true)
			.put(MobConfigManager.FIELD_MOB_VARIANT, true)
			.put(MobConfigManager.FILE_SPIDER, spider)
			.build();
	}

	private static JsonObject buildSpiderJockeyVariantDefaults() {
		JsonObject jockey = JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.object(MobConfigManager.FIELD_JOCKEY_PASSENGER, passenger -> passenger
				.object(MobConfigManager.FIELD_MOB, passengerMob -> passengerMob
					.put(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:skeleton"))
				.put(MobConfigManager.FIELD_MAIN_HAND, "minecraft:bow"))
			.object(MobConfigManager.FIELD_JOCKEY_MOUNT, mount -> mount
				.object(MobConfigManager.FIELD_MOB, mountMob -> mountMob
					.put(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:spider")))
			.build();
		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_SHARED_COMPONENTS, true)
			.put(
				MobConfigManager.FIELD_SPAWN_RULES,
				MobConfigManager.mobSpawnRules().spawnWeight(10.0d).mobJockey(jockey).build()
			)
			.build();
	}

	private static JsonObject buildCaveSpiderVariantDefaults() {
		JsonObject alternativeMob = JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_MOB, "minecraft:cave_spider")
			.build();
		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_SHARED_COMPONENTS, true)
			.put(
				MobConfigManager.FIELD_SPAWN_RULES,
				MobConfigManager.mobSpawnRules().spawnWeight(10.0d).spawnAlternativeMob(alternativeMob).build()
			)
			.build();
	}

	private static JsonObject buildSpiderStatsDefaults() {
		return MobConfigManager.buildMobStatsDefaults(
			16.0d,
			null,
			4.0d,
			0.3d,
			null,
			null,
			null,
			0.5d,
			7,
			null,
			null,
			null,
			null,
			DEFAULT_MOB_DROPS
		);
	}

	private static JsonObject buildSpiderVariantSpawnRulesDefaults(double spawnWeight) {
		return MobConfigManager.mobSpawnRules().spawnWeight(spawnWeight).build();
	}

	private static JsonObject buildSpiderBehaviorDefaults() {
		return MobConfigManager.buildMobBehaviorDefaults(behavior ->
			behavior.addProperty(MobConfigManager.FIELD_RETALIATE_WHEN_HURT, true)
		);
	}

	private static JsonObject buildSpiderGoalsDefaults() {
		return MobConfigManager.buildMobGoalsDefaults(goals -> {
			MobConfigManager.addMobGoal(goals, "hurt-by-target", true, 1, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "target-player", true, 2, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "melee-attack", true, 4, 100.0d, 20);
		});
	}
}

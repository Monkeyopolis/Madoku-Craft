package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigSpider {
	private static final String DEFAULT_MOB_DROPS = "minecraft-entities-spider.json";

	private MobConfigSpider() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_STATS, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_GOALS, true);
		root.addProperty(MobConfigManager.FIELD_MOB_VARIANT, true);

		JsonObject spider = new JsonObject();
		spider.addProperty(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true);
		spider.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALING, true);

		JsonObject difficultyScale = new JsonObject();
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_SCALE, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d);
		spider.add(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale);

		JsonObject regionalDifficultyScale = new JsonObject();
		regionalDifficultyScale.addProperty(MobConfigManager.FIELD_ENABLED, true);
		spider.add(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale);

		JsonObject defaultGroup = buildSpiderDefaultGroup();
		spider.add(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup);
		spider.add("cave-spider", buildCaveSpiderVariantDefaults());
		spider.add("spider-jockey", buildSpiderJockeyVariantDefaults());

		root.add(MobConfigManager.FILE_SPIDER, spider);
		return root;
	}

	private static JsonObject buildSpiderDefaultGroup() {
		JsonObject group = new JsonObject();
		group.add(MobConfigManager.FIELD_MOB_STATS, buildSpiderStatsDefaults());
		group.add(MobConfigManager.FIELD_SPAWN_RULES, buildSpiderVariantSpawnRulesDefaults(80.0d));
		group.add(MobConfigManager.FIELD_MOB_BEHAVIOR, buildSpiderBehaviorDefaults());
		group.add(MobConfigManager.FIELD_MOB_GOALS, buildSpiderGoalsDefaults());
		return group;
	}

	private static JsonObject buildCaveSpiderVariantDefaults() {
		JsonObject variant = new JsonObject();
		variant.addProperty(MobConfigManager.FIELD_SHARED_COMPONENTS, true);

		JsonObject alternativeMob = new JsonObject();
		alternativeMob.addProperty(MobConfigManager.FIELD_ENABLED, true);
		alternativeMob.addProperty(MobConfigManager.FIELD_MOB, "minecraft:cave_spider");
		variant.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules().spawnWeight(10.0d).spawnAlternativeMob(alternativeMob).build()
		);
		return variant;
	}

	private static JsonObject buildSpiderJockeyVariantDefaults() {
		JsonObject variant = new JsonObject();
		variant.addProperty(MobConfigManager.FIELD_SHARED_COMPONENTS, true);

		JsonObject jockey = new JsonObject();
		jockey.addProperty(MobConfigManager.FIELD_ENABLED, true);

		JsonObject passenger = new JsonObject();
		JsonObject passengerMob = new JsonObject();
		passengerMob.addProperty(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:skeleton");
		passenger.add(MobConfigManager.FIELD_MOB, passengerMob);
		passenger.addProperty(MobConfigManager.FIELD_MAIN_HAND, "minecraft:bow");
		jockey.add(MobConfigManager.FIELD_JOCKEY_PASSENGER, passenger);

		JsonObject mount = new JsonObject();
		JsonObject mountMob = new JsonObject();
		mountMob.addProperty(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:spider");
		mount.add(MobConfigManager.FIELD_MOB, mountMob);
		jockey.add(MobConfigManager.FIELD_JOCKEY_MOUNT, mount);

		variant.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules().spawnWeight(10.0d).mobJockey(jockey).build()
		);
		return variant;
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

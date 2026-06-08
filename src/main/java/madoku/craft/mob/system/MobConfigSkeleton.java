package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigSkeleton {
	private static final String DEFAULT_MOB_DROPS = "minecraft-entities-skeleton.json";

	private MobConfigSkeleton() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_STATS, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_GOALS, true);
		root.addProperty(MobConfigManager.FIELD_WEAPON_DAMAGE, false);
		root.addProperty(MobConfigManager.FIELD_MOB_VARIANT, true);

		JsonObject skeleton = new JsonObject();
		skeleton.addProperty(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true);
		skeleton.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALING, true);

		JsonObject difficultyScale = new JsonObject();
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d);
		skeleton.add(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale);

		JsonObject regionalDifficultyScale = new JsonObject();
		regionalDifficultyScale.addProperty(MobConfigManager.FIELD_ENABLED, true);
		skeleton.add(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale);

		JsonObject defaultGroup = new JsonObject();
		defaultGroup.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, 80.0d);
		defaultGroup.add(
			MobConfigManager.FIELD_MOB_STATS,
			buildSkeletonMobStatsDefaults()
		);
		defaultGroup.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules().spawnWeight(80.0d).build()
		);
		defaultGroup.add(
			MobConfigManager.FIELD_MOB_BEHAVIOR,
			MobConfigManager.buildMobBehaviorDefaults(behavior -> behavior.addProperty(MobConfigManager.FIELD_BOW_ATTACK, true))
		);
		defaultGroup.add(
			MobConfigManager.FIELD_MOB_GOALS,
			MobConfigManager.buildMobGoalsDefaults(goals -> {
				MobConfigManager.addMobGoal(goals, "hurt-by-target", true, 1, 100.0d, 0);
				MobConfigManager.addMobGoal(goals, "target-player", true, 2, 100.0d, 0);
				MobConfigManager.addMobGoal(goals, "ranged-attack", true, 4, 100.0d, 20);
			})
		);
		skeleton.add(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup);
		skeleton.add("melee-skeleton", buildMeleeSkeletonVariantDefaults());
		skeleton.add("skeleton-jockey", buildSkeletonJockeyVariantDefaults());

		root.add(MobConfigManager.FILE_SKELETON, skeleton);
		return root;
	}

	private static JsonObject buildSkeletonMobStatsDefaults() {
		JsonObject mobStats = MobConfigManager.buildMobStatsDefaults(
				16.0d,
				null,
				null,
				0.24d,
				null,
				null,
				null,
				1.0d,
				7,
				5.0d,
				0.7d,
				20.0d,
				10.0d,
				DEFAULT_MOB_DROPS
		);
		mobStats.add(
			MobConfigManager.FIELD_MOB_WEAPON,
			MobConfigManager.buildMobWeaponDefaults("minecraft:bow")
		);
		return mobStats;
	}

	private static JsonObject buildMeleeSkeletonVariantDefaults() {
		JsonObject variant = new JsonObject();
		variant.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules().spawnWeight(10.0d).build()
		);

		JsonObject stats = MobConfigManager.buildMobStatsDefaults(
			20.0d,
			null,
			5.0d,
			0.24d,
			null,
			null,
			null,
			1.0d,
			7,
			null,
			null,
			null,
			null,
			DEFAULT_MOB_DROPS
		);
		stats.add(
			MobConfigManager.FIELD_MOB_WEAPON,
			MobConfigManager.buildMobWeaponDefaults("empty")
		);
		stats.addProperty(MobConfigManager.FIELD_TRUE_DAMAGE, 1.0d);
		variant.add(MobConfigManager.FIELD_MOB_STATS, stats);
		variant.add(
			MobConfigManager.FIELD_MOB_GOALS,
			MobConfigManager.buildMobGoalsDefaults(goals -> {
				MobConfigManager.addMobGoal(goals, "hurt-by-target", true, 1, 100.0d, 0);
				MobConfigManager.addMobGoal(goals, "target-player", true, 2, 100.0d, 0);
				MobConfigManager.addMobGoal(goals, "melee-attack", true, 4, 100.0d, 20);
			})
		);
		return variant;
	}

	private static JsonObject buildSkeletonJockeyVariantDefaults() {
		JsonObject variant = new JsonObject();
		variant.addProperty(MobConfigManager.FIELD_SHARED_COMPONENTS, true);

		JsonObject passenger = new JsonObject();
		JsonObject passengerMob = new JsonObject();
		passengerMob.addProperty(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:skeleton");
		passenger.add(MobConfigManager.FIELD_MOB, passengerMob);
		passenger.addProperty(MobConfigManager.FIELD_MAIN_HAND, "minecraft:bow");

		JsonObject mount = new JsonObject();
		JsonObject mountMob = new JsonObject();
		mountMob.addProperty(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:spider");
		mount.add(MobConfigManager.FIELD_MOB, mountMob);

		JsonObject jockey = new JsonObject();
		jockey.addProperty(MobConfigManager.FIELD_ENABLED, true);
		jockey.add(MobConfigManager.FIELD_JOCKEY_PASSENGER, passenger);
		jockey.add(MobConfigManager.FIELD_JOCKEY_MOUNT, mount);

		variant.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules().spawnWeight(10.0d).mobJockey(jockey).build()
		);
		return variant;
	}
}




package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigCreeper {
	private MobConfigCreeper() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_STATS, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_GOALS, true);
		root.addProperty(MobConfigManager.FIELD_MOB_VARIANT, true);

		JsonObject creeper = new JsonObject();
		creeper.addProperty(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true);
		creeper.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALING, true);
		JsonObject difficultyScale = new JsonObject();
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.1d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPLOSION_POWER, 0.1d);
		creeper.add(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale);
		JsonObject regionalDifficultyScale = new JsonObject();
		regionalDifficultyScale.addProperty(MobConfigManager.FIELD_ENABLED, true);
		creeper.add(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale);

		JsonObject defaultGroup = buildCreeperDefaultGroup();
		JsonObject chargedVariant = buildChargedCreeperVariant();
		creeper.add(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup);
		creeper.add(MobConfigManager.FIELD_CHARGED_CREEPER, chargedVariant);

		root.add(MobConfigManager.FILE_CREEPER, creeper);
		return root;
	}

	private static JsonObject buildCreeperDefaultGroup() {
		JsonObject group = new JsonObject();
		group.add(MobConfigManager.FIELD_MOB_STATS, buildCreeperStatsDefaults(3.0d, 0.27d, 0.10d, 30.0d, 7));
		group.add(MobConfigManager.FIELD_SPAWN_RULES, MobConfigManager.mobSpawnRules().spawnWeight(90.0d).build());
		group.add(MobConfigManager.FIELD_MOB_BEHAVIOR, buildCreeperBehaviorDefaults(0.4d, 0.4d));
		group.add(MobConfigManager.FIELD_MOB_GOALS, buildCreeperGoalsDefaults());
		MobConfigManager.ensureMobSchema(group, false);
		return group;
	}

	private static JsonObject buildChargedCreeperVariant() {
		JsonObject variant = new JsonObject();
		variant.add(
			MobConfigManager.FIELD_MOB_STATS,
			buildCreeperStatsDefaults(5.0d, 0.30d, 0.20d, 25.0d, 11)
		);
		variant.add(MobConfigManager.FIELD_SPAWN_RULES, MobConfigManager.mobSpawnRules().spawnWeight(10.0d).build());
		variant.add(MobConfigManager.FIELD_MOB_BEHAVIOR, buildCreeperBehaviorDefaults(0.6d, 0.6d));
		variant.add(MobConfigManager.FIELD_MOB_GOALS, buildCreeperGoalsDefaults());
		MobConfigManager.ensureMobSchema(variant, false);
		return variant;
	}

	private static JsonObject buildCreeperStatsDefaults(
		double explosionPower,
		double movementSpeed,
		double knockbackResistance,
		double fuseLength,
		int experience
	) {
		JsonObject creeperStats = MobConfigManager.buildMobStatsDefaults(
			12.0d,
			1.0d,
			null,
			movementSpeed,
			null,
			null,
			knockbackResistance,
			1.0d,
			experience,
			null,
			null,
			null,
			null,
			null
		);
		creeperStats.addProperty(MobConfigManager.FIELD_EXPLOSION_POWER, explosionPower);
		creeperStats.addProperty(MobConfigManager.FIELD_FUSE_LENGTH, fuseLength);
		return creeperStats;
	}

	private static JsonObject buildCreeperBehaviorDefaults(double destructionChance, double griefPower) {
		return MobConfigManager.buildMobBehaviorDefaults(behavior -> behavior.add(
			MobConfigManager.FIELD_MOB_EXPLODE,
			MobConfigManager.buildMobExplodeDefaults(destructionChance, griefPower)
		));
	}

	private static JsonObject buildCreeperGoalsDefaults() {
		return MobConfigManager.buildMobGoalsDefaults(goals -> {
			MobConfigManager.addMobGoal(goals, "ranged-attack", true, 4, 100.0d, 20);
			MobConfigManager.addMobGoal(goals, "target-player", true, 2, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "hurt-by-target", true, 1, 100.0d, 0);
		});
	}
}

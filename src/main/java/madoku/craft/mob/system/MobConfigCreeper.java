package madoku.craft.mob.system;

import com.google.gson.JsonObject;

import madoku.craft.api.json.JSONFormatManager;

public final class MobConfigCreeper {
	private MobConfigCreeper() {
	}

	public static JsonObject buildDefaults() {
		JsonObject defaultGroup = buildCreeperDefaultGroup();
		JsonObject chargedVariant = buildChargedCreeperVariant();
		JsonObject creeper = JSONFormatManager.object()
			.put(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true)
			.put(MobConfigManager.FIELD_DIFFICULTY_SCALING, true)
			.object(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale -> difficultyScale
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.1d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPLOSION_POWER, 0.1d))
			.object(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale ->
				regionalDifficultyScale.put(MobConfigManager.FIELD_ENABLED, true))
			.put(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup)
			.put(MobConfigManager.FIELD_CHARGED_CREEPER, chargedVariant)
			.build();

		return JSONFormatManager.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_OVERRIDE_STATS, true)
			.put(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true)
			.put(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true)
			.put(MobConfigManager.FIELD_OVERRIDE_GOALS, true)
			.put(MobConfigManager.FIELD_MOB_VARIANT, true)
			.put(MobConfigManager.FILE_CREEPER, creeper)
			.build();
	}

	private static JsonObject buildCreeperDefaultGroup() {
		JsonObject group = JSONFormatManager.object()
			.put(MobConfigManager.FIELD_MOB_STATS, buildCreeperStatsDefaults(3.0d, 0.27d, 0.10d, 30.0d, 7))
			.put(MobConfigManager.FIELD_SPAWN_RULES, MobConfigManager.mobSpawnRules().spawnWeight(90.0d).build())
			.put(MobConfigManager.FIELD_MOB_BEHAVIOR, buildCreeperBehaviorDefaults(0.4d, 0.4d))
			.put(MobConfigManager.FIELD_MOB_GOALS, buildCreeperGoalsDefaults())
			.build();
		MobConfigManager.ensureMobSchema(group, false);
		return group;
	}

	private static JsonObject buildChargedCreeperVariant() {
		JsonObject variant = JSONFormatManager.object()
			.put(MobConfigManager.FIELD_MOB_STATS, buildCreeperStatsDefaults(5.0d, 0.30d, 0.20d, 25.0d, 11))
			.put(MobConfigManager.FIELD_SPAWN_RULES, MobConfigManager.mobSpawnRules().spawnWeight(10.0d).build())
			.put(MobConfigManager.FIELD_MOB_BEHAVIOR, buildCreeperBehaviorDefaults(0.6d, 0.6d))
			.put(MobConfigManager.FIELD_MOB_GOALS, buildCreeperGoalsDefaults())
			.build();
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
		JsonObject creeperStats = JSONFormatManager.object()
			.putAll(MobConfigManager.buildMobStatsDefaults(
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
			))
			.put(MobConfigManager.FIELD_EXPLOSION_POWER, explosionPower)
			.put(MobConfigManager.FIELD_FUSE_LENGTH, fuseLength)
			.build();
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


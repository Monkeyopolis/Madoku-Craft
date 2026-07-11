package madoku.craft.mob.system;

import com.google.gson.JsonObject;

import madoku.craft.api.json.JSONFormatManager;

public final class MobConfigDrowned {
	private MobConfigDrowned() {
	}

	public static JsonObject buildDefaults() {
		JsonObject defaultGroup = JSONFormatManager.object()
			.put(
				MobConfigManager.FIELD_MOB_STATS,
				buildDrownedMeleeMobStatsDefaults(
					20.0d,
					5.0d,
					0.24d,
					0.012d,
					1.0d,
					7,
					resolveDefaultMobDropsReference()
				)
			)
			.put(MobConfigManager.FIELD_SPAWN_RULES, buildDrownedSpawnRulesDefaults())
			.put(MobConfigManager.FIELD_MOB_BEHAVIOR, buildDrownedBehaviorDefaults())
			.put(MobConfigManager.FIELD_MOB_GOALS, buildDrownedGoalsDefaults())
			.put(MobConfigManager.FIELD_ADULT_GROUP, buildDrownedAgeOverride(false))
			.put(MobConfigManager.FIELD_BABY_GROUP, buildDrownedAgeOverride(true))
			.build();

		JsonObject drowned = JSONFormatManager.object()
			.put(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true)
			.put(MobConfigManager.FIELD_DIFFICULTY_SCALING, true)
			.put(MobConfigManager.FIELD_WEAPON_DAMAGE, false)
			.object(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale -> difficultyScale
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_RANGED_DAMAGE, 0.10d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_SWIMMING_SPEED, 0.10d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d))
			.object(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale ->
				regionalDifficultyScale.put(MobConfigManager.FIELD_ENABLED, true))
			.put(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup)
			.put("ranged-drowned", buildRangedDrownedVariantDefaults())
			.build();

		return JSONFormatManager.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_OVERRIDE_STATS, true)
			.put(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true)
			.put(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true)
			.put(MobConfigManager.FIELD_OVERRIDE_GOALS, true)
			.put(MobConfigManager.FIELD_MOB_BABY, true)
			.put(MobConfigManager.FIELD_MOB_VARIANT, true)
			.put(MobConfigManager.FILE_DROWNED, drowned)
			.build();
	}

	private static JsonObject buildDrownedMeleeMobStatsDefaults(
		Double health,
		Double damage,
		Double movementSpeed,
		Double swimmingSpeed,
		Double scale,
		int experienceDrop,
		String mobDropsReference
	) {
		return MobConfigManager.buildMobStatsDefaults(
			health,
			null,
			damage,
			movementSpeed,
			swimmingSpeed,
			null,
			null,
			scale,
			experienceDrop,
			null,
			null,
			null,
			null,
			mobDropsReference
		);
	}

	private static JsonObject buildDrownedSpawnRulesDefaults() {
		return MobConfigManager.mobSpawnRules()
			.spawnWeight(90.0d)
			.equipmentSet(buildDrownedEquipmentSetDefaults())
			.build();
	}

	private static JsonObject buildDrownedEquipmentSetDefaults() {
		return JSONFormatManager.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_MOB_EQUIPMENT, resolveDefaultMobEquipmentReference())
			.put(MobConfigManager.FIELD_EQUIPMENT_CHANCE, 10.0d)
			.build();
	}

	private static JsonObject buildRangedDrownedVariantDefaults() {
		return JSONFormatManager.object()
			.put(
				MobConfigManager.FIELD_MOB_STATS,
				buildRangedDrownedSharedMobStatsDefaults(resolveDefaultMobDropsReference(), "minecraft:trident")
			)
			.put(MobConfigManager.FIELD_MOB_BEHAVIOR, buildRangedDrownedBehaviorDefaults())
			.put(MobConfigManager.FIELD_MOB_GOALS, buildRangedDrownedGoalsDefaults())
			.put(MobConfigManager.FIELD_SPAWN_RULES, buildRangedDrownedSpawnRulesDefaults())
			.put(MobConfigManager.FIELD_ADULT_GROUP, buildRangedDrownedAgeOverride(false))
			.put(MobConfigManager.FIELD_BABY_GROUP, buildRangedDrownedAgeOverride(true))
			.build();
	}

	private static JsonObject buildDrownedGoalsDefaults() {
		return MobConfigManager.buildMobGoalsDefaults(goals -> {
			MobConfigManager.addMobGoal(goals, "hurt-by-target", true, 1, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "target-player", true, 2, 100.0d, 0);
		});
	}

	private static JsonObject buildDrownedBehaviorDefaults() {
		return MobConfigManager.buildMobBehaviorDefaults(behavior -> {
			behavior.addProperty(MobConfigManager.FIELD_CAN_PICK_UP_LOOT, true);
			behavior.addProperty(MobConfigManager.FIELD_CALLS_REINFORCEMENTS_WHEN_HURT, false);
		});
	}

	private static JsonObject buildRangedDrownedBehaviorDefaults() {
		return MobConfigManager.buildMobBehaviorDefaults(behavior -> {
			behavior.addProperty(MobConfigManager.FIELD_CAN_PICK_UP_LOOT, true);
			behavior.addProperty(MobConfigManager.FIELD_CALLS_REINFORCEMENTS_WHEN_HURT, false);
			behavior.addProperty(MobConfigManager.FIELD_TRIDENT_ATTACK, true);
		});
	}

	private static JsonObject buildRangedDrownedSharedMobStatsDefaults(String mobDropsReference, String weaponItemId) {
		return JSONFormatManager.object()
			.putAll(MobConfigManager.buildMobStatsDefaults(
			null,
			null,
			null,
			0.24d,
			0.012d,
			null,
			null,
			null,
			null,
			9.0d,
			0.8d,
			30.0d,
			15.0d,
			mobDropsReference
		))
			.put(MobConfigManager.FIELD_MOB_WEAPON, MobConfigManager.buildMobWeaponDefaults(weaponItemId))
			.build();
	}

	private static JsonObject buildRangedDrownedSpawnRulesDefaults() {
		return MobConfigManager.mobSpawnRules()
			.spawnWeight(10.0d)
			.equipmentSet(buildDrownedEquipmentSetDefaults())
			.build();
	}

	private static JsonObject buildRangedDrownedGoalsDefaults() {
		return MobConfigManager.buildMobGoalsDefaults(goals -> {
			MobConfigManager.addMobGoal(goals, "trident-attack", true, 3, 100.0d, 20);
			MobConfigManager.addMobGoal(goals, "hurt-by-target", true, 1, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "target-player", true, 2, 100.0d, 0);
		});
	}

	private static JsonObject buildRangedDrownedAgeOverride(boolean baby) {
		return JSONFormatManager.object()
			.put(MobConfigManager.FIELD_MOB_STATS, buildDrownedRangedAgeMobStatsDefaults(baby))
			.put(
				MobConfigManager.FIELD_SPAWN_RULES,
				MobConfigManager.mobSpawnRules().spawnWeight(baby ? 10.0d : 90.0d).build()
			)
			.build();
	}

	private static JsonObject buildDrownedRangedAgeMobStatsDefaults(boolean baby) {
		return MobConfigManager.buildMobStatsDefaults(
			baby ? 10.0d : 20.0d,
			null,
			baby ? 2.5d : 5.0d,
			null,
			null,
			null,
			null,
			baby ? null : 1.0d,
			baby ? 3 : 7,
			baby ? 4.5d : null,
			null,
			null,
			null,
			null
		);
	}

	private static JsonObject buildDrownedAgeOverride(boolean baby) {
		JSONFormatManager.ObjectBuilder root = JSONFormatManager.object()
			.put(
				MobConfigManager.FIELD_SPAWN_RULES,
				MobConfigManager.mobSpawnRules().spawnWeight(baby ? 10.0d : 90.0d).build()
			);
		if (baby) {
			root.put(
				MobConfigManager.FIELD_MOB_STATS,
				buildDrownedMeleeMobStatsDefaults(
					10.0d,
					2.5d,
					null,
					null,
					null,
					3,
					resolveDefaultMobDropsReference()
				)
			);
		}
		return root.build();
	}

	private static String resolveDefaultMobDropsReference() {
		return "minecraft-entities-drowned.json";
	}

	private static String resolveDefaultMobEquipmentReference() {
		return "minecraft-equipment-drowned.json";
	}
}


package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigDrowned {
	private MobConfigDrowned() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_STATS, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_GOALS, true);
		root.addProperty(MobConfigManager.FIELD_MOB_BABY, true);
		root.addProperty(MobConfigManager.FIELD_MOB_VARIANT, true);

		JsonObject drowned = new JsonObject();
		drowned.addProperty(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true);
		drowned.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALING, true);
		drowned.addProperty(MobConfigManager.FIELD_WEAPON_DAMAGE, false);

		JsonObject difficultyScale = new JsonObject();
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d);
		drowned.add(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale);

		JsonObject regionalDifficultyScale = new JsonObject();
		regionalDifficultyScale.addProperty(MobConfigManager.FIELD_ENABLED, true);
		drowned.add(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale);

		JsonObject defaultGroup = new JsonObject();
		defaultGroup.add(
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
		);
		defaultGroup.add(MobConfigManager.FIELD_SPAWN_RULES, buildDrownedSpawnRulesDefaults());
		defaultGroup.add(MobConfigManager.FIELD_MOB_BEHAVIOR, buildDrownedBehaviorDefaults());
		defaultGroup.add(MobConfigManager.FIELD_MOB_GOALS, buildDrownedGoalsDefaults());
		defaultGroup.add(MobConfigManager.FIELD_ADULT_GROUP, buildDrownedAgeOverride(false));
		defaultGroup.add(MobConfigManager.FIELD_BABY_GROUP, buildDrownedAgeOverride(true));
		drowned.add(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup);
		drowned.add("ranged-drowned", buildRangedDrownedVariantDefaults());

		root.add(MobConfigManager.FILE_DROWNED, drowned);
		return root;
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
		JsonObject equipmentSet = new JsonObject();
		equipmentSet.addProperty(MobConfigManager.FIELD_ENABLED, true);
		equipmentSet.addProperty(MobConfigManager.FIELD_MOB_EQUIPMENT, resolveDefaultMobEquipmentReference());
		equipmentSet.addProperty(MobConfigManager.FIELD_EQUIPMENT_CHANCE, 10.0d);
		return equipmentSet;
	}

	private static JsonObject buildRangedDrownedVariantDefaults() {
		JsonObject variant = new JsonObject();
		variant.add(
			MobConfigManager.FIELD_MOB_STATS,
			buildRangedDrownedSharedMobStatsDefaults(resolveDefaultMobDropsReference(), "minecraft:trident")
		);
		variant.add(MobConfigManager.FIELD_MOB_BEHAVIOR, buildRangedDrownedBehaviorDefaults());
		variant.add(MobConfigManager.FIELD_MOB_GOALS, buildRangedDrownedGoalsDefaults());
		variant.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			buildRangedDrownedSpawnRulesDefaults()
		);
		variant.add(MobConfigManager.FIELD_ADULT_GROUP, buildRangedDrownedAgeOverride(false));
		variant.add(MobConfigManager.FIELD_BABY_GROUP, buildRangedDrownedAgeOverride(true));
		return variant;
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
		JsonObject mobStats = MobConfigManager.buildMobStatsDefaults(
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
		);
		mobStats.add(
			MobConfigManager.FIELD_MOB_WEAPON,
			MobConfigManager.buildMobWeaponDefaults(weaponItemId)
		);
		return mobStats;
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
		JsonObject root = new JsonObject();
		root.add(
			MobConfigManager.FIELD_MOB_STATS,
			buildDrownedRangedAgeMobStatsDefaults(baby)
		);
		root.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules().spawnWeight(baby ? 10.0d : 90.0d).build()
		);
		return root;
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
		JsonObject root = new JsonObject();
		if (baby) {
			root.add(
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

		root.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules().spawnWeight(baby ? 10.0d : 90.0d).build()
		);
		return root;
	}

	private static String resolveDefaultMobDropsReference() {
		return "minecraft-entities-drowned.json";
	}

	private static String resolveDefaultMobEquipmentReference() {
		return "minecraft-equipment-drowned.json";
	}
}

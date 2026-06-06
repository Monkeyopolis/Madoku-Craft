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
			buildDrownedMobStatsDefaults(
				20.0d,
				5.0d,
				0.24d,
				0.012d,
				1.0d,
				7,
				9.0d,
				0.8d,
				30.0d,
				15.0d,
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

	private static JsonObject buildDrownedMobStatsDefaults(
		double health,
		double damage,
		double movementSpeed,
		double swimmingSpeed,
		double scale,
		int experienceDrop,
		double rangedDamage,
		double attackAccuracy,
		double attackInterval,
		double chargeUpTicks,
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
			rangedDamage,
			attackAccuracy,
			attackInterval,
			chargeUpTicks,
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

	private static JsonObject buildDrownedBehaviorDefaults() {
		JsonObject behavior = new JsonObject();
		behavior.addProperty(MobConfigManager.FIELD_CAN_BREAK_DOORS, true);
		behavior.addProperty(MobConfigManager.FIELD_CAN_PICK_UP_LOOT, true);
		behavior.addProperty("calls_reinforcements_when_hurt", false);
		behavior.addProperty("searching_for_land", true);
		behavior.addProperty("targeting_underwater", true);
		behavior.addProperty(MobConfigManager.FIELD_TRIDENT_ATTACK, true);
		behavior.addProperty(MobConfigManager.FIELD_TRIDENT_GROUND_CLEAR_TICKS, 300);
		return behavior;
	}

	private static JsonObject buildRangedDrownedVariantDefaults() {
		JsonObject variant = new JsonObject();
		variant.addProperty(MobConfigManager.FIELD_SHARED_COMPONENTS, true);

		variant.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules().spawnWeight(10.0d).build()
		);
		return variant;
	}

	private static JsonObject buildDrownedGoalsDefaults() {
		JsonObject goals = new JsonObject();
		goals.addProperty(MobConfigManager.FIELD_ENABLED, true);
		MobConfigManager.addMobGoal(goals, "float", true, 0, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "go_to_water", true, 1, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "swim_up", true, 2, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "trident_attack", true, 3, 100.0d, 20);
		MobConfigManager.addMobGoal(goals, "melee_attack", true, 4, 100.0d, 20);
		MobConfigManager.addMobGoal(goals, "wander", true, 5, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "look_at_player", true, 6, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "random_look_around", true, 7, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "break_turtle_egg", true, 1, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "hurt_by_target", true, 1, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "target_player", true, 2, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "target_villager", true, 3, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "target_wandering_trader", true, 3, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "target_snow_golem", true, 3, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "target_baby_turtle", true, 3, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "target_iron_golem", true, 3, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "target_axolotl", true, 3, 100.0d, 0);
		return goals;
	}

	private static JsonObject buildDrownedAgeOverride(boolean baby) {
		JsonObject root = new JsonObject();
		if (baby) {
			root.add(
				MobConfigManager.FIELD_MOB_STATS,
				MobConfigManager.buildMobStatsDefaults(
					10.0d,
					null,
					2.5d,
					null,
					null,
					null,
					null,
					null,
					3,
					4.5d,
					0.6d,
					null,
					null,
					null
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

package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigHusk {
	private MobConfigHusk() {
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

		JsonObject husk = new JsonObject();
		husk.addProperty(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true);
		husk.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALING, true);
		husk.addProperty(MobConfigManager.FIELD_WEAPON_DAMAGE, false);

		JsonObject difficultyScale = new JsonObject();
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d);
		husk.add(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale);

		JsonObject regionalDifficultyScale = new JsonObject();
		regionalDifficultyScale.addProperty(MobConfigManager.FIELD_ENABLED, true);
		husk.add(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale);

		JsonObject defaultGroup = new JsonObject();
		JsonObject huskStats = buildHuskMobStatsDefaults(
			28.0d,
			2.0d,
			7.0d,
			0.18d,
			0.4d,
			1.0d,
			7,
			resolveDefaultMobDropsReference()
		);
		huskStats.add(
			MobConfigManager.FIELD_MOB_EFFECT,
			MobConfigManager.buildMobEffectDefaults("minecraft:slowness", 15)
		);
		defaultGroup.add(MobConfigManager.FIELD_MOB_STATS, huskStats);
		defaultGroup.add(MobConfigManager.FIELD_SPAWN_RULES, buildHuskSpawnRulesDefaults());
		defaultGroup.add(MobConfigManager.FIELD_MOB_BEHAVIOR, buildHuskBehaviorDefaults(true));
		defaultGroup.add(MobConfigManager.FIELD_MOB_GOALS, buildHuskGoalsDefaults());
		defaultGroup.add(MobConfigManager.FIELD_ADULT_GROUP, buildHuskAgeOverride(false, 0.4, 7, 90.0d, null, null, null));
		defaultGroup.add(MobConfigManager.FIELD_BABY_GROUP, buildHuskAgeOverride(true, 0.2d, 3, 10.0d, 14.0d, 1.0d, 3.5d));
		husk.add(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup);

		root.add(MobConfigManager.FILE_HUSK, husk);
		return root;
	}

	private static JsonObject buildHuskMobStatsDefaults(
		double health,
		double armor,
		double damage,
		double movementSpeed,
		double knockbackResistance,
		double scale,
		int experienceDrop,
		String mobDropsReference
	) {
		return MobConfigManager.buildMobStatsDefaults(
			health,
			armor,
			damage,
			movementSpeed,
			null,
			null,
			knockbackResistance,
			scale,
			experienceDrop,
			null,
			null,
			null,
			null,
			mobDropsReference
		);
	}

	private static String resolveDefaultMobDropsReference() {
		return "minecraft-entities-husk.json";
	}

	private static String resolveDefaultMobEquipmentReference() {
		return "minecraft-equipment-husk.json";
	}

	private static JsonObject buildHuskSpawnRulesDefaults() {
		return MobConfigManager.mobSpawnRules()
			.spawnWeight(90.0d)
			.equipmentSet(buildHuskEquipmentSetDefaults())
			.build();
	}

	private static JsonObject buildHuskEquipmentSetDefaults() {
		JsonObject equipmentSet = new JsonObject();
		equipmentSet.addProperty(MobConfigManager.FIELD_ENABLED, true);
		equipmentSet.addProperty(MobConfigManager.FIELD_MOB_EQUIPMENT, resolveDefaultMobEquipmentReference());
		equipmentSet.addProperty(MobConfigManager.FIELD_EQUIPMENT_CHANCE, 10.0d);
		return equipmentSet;
	}

	private static JsonObject buildHuskBehaviorDefaults(boolean canPickUpLootDefault) {
		return MobConfigManager.buildMobBehaviorDefaults(behavior -> {
			behavior.addProperty(MobConfigManager.FIELD_CAN_PICK_UP_LOOT, canPickUpLootDefault);
			behavior.addProperty(MobConfigManager.FIELD_CALLS_REINFORCEMENTS_WHEN_HURT, false);
		});
	}

	private static JsonObject buildHuskGoalsDefaults() {
		return MobConfigManager.buildMobGoalsDefaults(goals -> {
			MobConfigManager.addMobGoal(goals, "hurt-by-target", true, 1, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "target-player", true, 2, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "melee-attack", true, 4, 100.0d, 20);
		});
	}

	private static JsonObject buildHuskAgeOverride(
		boolean baby,
		Double knockbackResistance,
		int experience,
		double spawnWeight,
		Double health,
		Double armor,
		Double damage
	) {
		JsonObject root = new JsonObject();
		root.add(
			MobConfigManager.FIELD_MOB_STATS,
			MobConfigManager.buildMobStatsDefaults(
				health,
				armor,
				damage,
				null,
				null,
				null,
				baby ? knockbackResistance : null,
				null,
				experience,
				null,
				null,
				null,
				null,
				null
			)
		);

		root.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules().spawnWeight(spawnWeight).build()
		);
		return root;
	}
}

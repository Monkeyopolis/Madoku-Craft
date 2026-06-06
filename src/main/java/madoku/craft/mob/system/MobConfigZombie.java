package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigZombie {
	private MobConfigZombie() {
	}

	public static JsonObject buildDefaults() {
		return buildZombieTypeDefaults(
			MobConfigManager.FILE_ZOMBIE,
			24.0d,
			12.0d,
			1.0d,
			6.0d,
			3.0d,
			0.21d,
			0.21d,
			1.0d,
			7
		);
	}

	static JsonObject buildZombieTypeDefaults(
		String mobKey,
		double adultHealth,
		double babyHealth,
		double armor,
		double adultDamage,
		double babyDamage,
		double adultSpeed,
		double babySpeed,
		double scale,
		int experience
	) {
		JsonObject root = new JsonObject();
		boolean zombieFile = MobConfigManager.FILE_ZOMBIE.equals(mobKey);
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_STATS, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_GOALS, true);
		root.addProperty(MobConfigManager.FIELD_MOB_BABY, true);
		root.addProperty(MobConfigManager.FIELD_MOB_VARIANT, zombieFile);

		JsonObject mob = new JsonObject();
		mob.addProperty(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true);
		mob.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALING, true);
		mob.addProperty(MobConfigManager.FIELD_WEAPON_DAMAGE, false);

		JsonObject difficultyScale = new JsonObject();
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d);
		mob.add(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale);

		JsonObject regionalDifficultyScale = new JsonObject();
		regionalDifficultyScale.addProperty(MobConfigManager.FIELD_ENABLED, true);
		mob.add(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale);

		JsonObject defaultGroup = new JsonObject();
		defaultGroup.add(
			MobConfigManager.FIELD_MOB_STATS,
			buildZombieMobStatsDefaults(
				adultHealth,
				armor,
				adultDamage,
				adultSpeed,
				armor > 0.0d ? 0.2d : 0.0d,
				scale,
				experience,
				resolveDefaultMobDropsReference(mobKey)
			)
		);
		defaultGroup.add(MobConfigManager.FIELD_SPAWN_RULES, buildZombieSpawnRulesDefaults(mobKey));
		defaultGroup.add(MobConfigManager.FIELD_MOB_BEHAVIOR, buildZombieBehaviorDefaults(false, false));
		defaultGroup.add(MobConfigManager.FIELD_MOB_GOALS, buildZombieGoalsDefaults());
		defaultGroup.add(MobConfigManager.FIELD_ADULT_GROUP, buildZombieAgeOverride(false, null, null, null, null, null, experience, 90.0d));
		defaultGroup.add(MobConfigManager.FIELD_BABY_GROUP, buildZombieAgeOverride(true, babyHealth, babyDamage, babySpeed, 0.0d, 0.0d, 3, 10.0d));
		mob.add(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup);
		if (zombieFile) {
			mob.add("zombie-jockey", buildZombieJockeyVariantDefaults());
			mob.add("zombie-villager", buildZombieVillagerVariantDefaults());
		}
		root.add(mobKey, mob);
		return root;
	}

	private static JsonObject buildZombieJockeyVariantDefaults() {
		JsonObject variant = new JsonObject();
		variant.addProperty(MobConfigManager.FIELD_SHARED_COMPONENTS, true);

		JsonObject jockey = new JsonObject();
		jockey.addProperty(MobConfigManager.FIELD_ENABLED, true);

		JsonObject passenger = new JsonObject();
		JsonObject passengerMob = new JsonObject();
		passengerMob.addProperty(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:zombie");
		passengerMob.addProperty(MobConfigManager.FIELD_BABY_GROUP, "minecraft:zombie");
		passenger.add(MobConfigManager.FIELD_MOB, passengerMob);
		passenger.addProperty(MobConfigManager.FIELD_MAIN_HAND, "minecraft:stone_spear");
		jockey.add(MobConfigManager.FIELD_JOCKEY_PASSENGER, passenger);

		JsonObject mount = new JsonObject();
		JsonObject mountMob = new JsonObject();
		mountMob.addProperty(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:zombie_horse");
		mountMob.addProperty(MobConfigManager.FIELD_BABY_GROUP, "minecraft:chicken");
		mount.add(MobConfigManager.FIELD_MOB, mountMob);
		jockey.add(MobConfigManager.FIELD_JOCKEY_MOUNT, mount);

		variant.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules().spawnWeight(10.0d).mobJockey(jockey).build()
		);
		return variant;
	}

	private static JsonObject buildZombieVillagerVariantDefaults() {
		JsonObject variant = new JsonObject();
		variant.addProperty(MobConfigManager.FIELD_SHARED_COMPONENTS, true);

		JsonObject alternativeMob = new JsonObject();
		alternativeMob.addProperty(MobConfigManager.FIELD_ENABLED, true);
		alternativeMob.addProperty(MobConfigManager.FIELD_MOB, "minecraft:zombie_villager");
		variant.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules().spawnWeight(10.0d).spawnAlternativeMob(alternativeMob).build()
		);
		return variant;
	}

	private static JsonObject buildZombieMobStatsDefaults(
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

	private static String resolveDefaultMobDropsReference(String mobKey) {
		return "minecraft-entities-zombie.json";
	}

	private static String resolveDefaultMobEquipmentReference(String mobKey) {
		return "minecraft-equipment-zombie.json";
	}

	private static JsonObject buildZombieSpawnRulesDefaults(String mobKey) {
		return MobConfigManager.mobSpawnRules()
			.spawnWeight(MobConfigManager.FILE_ZOMBIE.equals(mobKey) ? 80.0d : 90.0d)
			.equipmentSet(buildZombieEquipmentSetDefaults(mobKey))
			.build();
	}

	private static JsonObject buildZombieEquipmentSetDefaults(String mobKey) {
		JsonObject equipmentSet = new JsonObject();
		equipmentSet.addProperty(MobConfigManager.FIELD_ENABLED, true);
		equipmentSet.addProperty(MobConfigManager.FIELD_MOB_EQUIPMENT, resolveDefaultMobEquipmentReference(mobKey));
		equipmentSet.addProperty(MobConfigManager.FIELD_EQUIPMENT_CHANCE, 10.0d);
		return equipmentSet;
	}

	private static JsonObject buildZombieBehaviorDefaults(boolean canBreakDoorsDefault, boolean canPickUpLootDefault) {
		JsonObject behavior = new JsonObject();
		behavior.addProperty(MobConfigManager.FIELD_CAN_BREAK_DOORS, canBreakDoorsDefault);
		behavior.addProperty(MobConfigManager.FIELD_CAN_PICK_UP_LOOT, canPickUpLootDefault);
		behavior.addProperty("calls_reinforcements_when_hurt", false);
		return behavior;
	}

	private static JsonObject buildZombieGoalsDefaults() {
		JsonObject goals = new JsonObject();
		goals.addProperty(MobConfigManager.FIELD_ENABLED, true);
		MobConfigManager.addMobGoal(goals, "break_door", true, 1, 100.0d, 0);
		return goals;
	}

	private static JsonObject buildZombieAgeOverride(
		boolean baby,
		Double health,
		Double damage,
		Double movementSpeed,
		Double armor,
		Double knockbackResistance,
		int experience,
		double spawnWeight
	) {
		JsonObject root = new JsonObject();
		if (baby) {
			root.add(
				MobConfigManager.FIELD_MOB_STATS,
				MobConfigManager.buildMobStatsDefaults(
					health,
					armor,
					damage,
					movementSpeed,
					null,
					null,
					knockbackResistance,
					null,
					experience,
					null,
					null,
					null,
					null,
					null
				)
			);
		}

		root.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules().spawnWeight(spawnWeight).build()
		);

		if (baby) {
			JsonObject behavior = new JsonObject();
			behavior.addProperty("calls_reinforcements_when_hurt", false);
			root.add(MobConfigManager.FIELD_MOB_BEHAVIOR, behavior);
		}
		return root;
	}
}

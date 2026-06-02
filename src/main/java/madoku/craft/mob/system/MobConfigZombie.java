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
		defaultGroup.add(MobConfigManager.FIELD_ADULT_GROUP, buildZombieAgeOverride(false, null, null, null, null, experience, 90.0d));
		defaultGroup.add(MobConfigManager.FIELD_BABY_GROUP, buildZombieAgeOverride(true, babyHealth, babyDamage, babySpeed, 0.0d, 3, 10.0d));
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

		JsonObject spawnRules = new JsonObject();
		spawnRules.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, 10.0d);

		JsonObject jockey = new JsonObject();
		jockey.addProperty(MobConfigManager.FIELD_ENABLED, true);
		JsonObject jockeyMob = new JsonObject();
		jockeyMob.addProperty(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:zombie_horse");
		jockeyMob.addProperty(MobConfigManager.FIELD_BABY_GROUP, "minecraft:chicken");
		jockey.add(MobConfigManager.FIELD_MOB, jockeyMob);
		spawnRules.add(MobConfigManager.FIELD_MOB_JOCKEY, jockey);

		variant.add(MobConfigManager.FIELD_SPAWN_RULES, spawnRules);
		return variant;
	}

	private static JsonObject buildZombieVillagerVariantDefaults() {
		JsonObject variant = new JsonObject();
		variant.addProperty(MobConfigManager.FIELD_SHARED_COMPONENTS, true);

		JsonObject spawnRules = new JsonObject();
		spawnRules.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, 10.0d);

		JsonObject alternativeMob = new JsonObject();
		alternativeMob.addProperty(MobConfigManager.FIELD_ENABLED, true);
		alternativeMob.addProperty(MobConfigManager.FIELD_MOB, "minecraft:zombie_villager");
		spawnRules.add(MobConfigManager.FIELD_SPAWN_ALTERNATIVE_MOB, alternativeMob);

		variant.add(MobConfigManager.FIELD_SPAWN_RULES, spawnRules);
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
		JsonObject stats = new JsonObject();
		stats.addProperty(MobConfigManager.FIELD_HEALTH, health);
		stats.addProperty(MobConfigManager.FIELD_ARMOR, armor);
		stats.addProperty(MobConfigManager.FIELD_DAMAGE, damage);
		stats.addProperty(MobConfigManager.FIELD_MOVEMENT_SPEED, movementSpeed);
		stats.addProperty(MobConfigManager.FIELD_KNOCKBACK_RESISTANCE, knockbackResistance);
		stats.addProperty(MobConfigManager.FIELD_SCALE, scale);
		stats.addProperty(MobConfigManager.FIELD_EXPERIENCE_DROP, experienceDrop);
		stats.addProperty(MobConfigManager.FIELD_MOB_DROPS, mobDropsReference);
		return stats;
	}

	private static String resolveDefaultMobDropsReference(String mobKey) {
		if (MobConfigManager.FILE_DROWNED.equals(mobKey)) {
			return "minecraft-entities-drowned.json";
		}
		return "minecraft-entities-zombie.json";
	}

	private static String resolveDefaultMobEquipmentReference(String mobKey) {
		if (MobConfigManager.FILE_DROWNED.equals(mobKey)) {
			return "minecraft-equipment-drowned.json";
		}
		return "minecraft-equipment-zombie.json";
	}

	private static JsonObject buildZombieSpawnRulesDefaults(String mobKey) {
		JsonObject spawnRules = new JsonObject();
		spawnRules.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT,
			MobConfigManager.FILE_ZOMBIE.equals(mobKey) ? 80.0d : 90.0d);
		spawnRules.add(MobConfigManager.FIELD_EQUIPMENT_SET, buildZombieEquipmentSetDefaults(mobKey));
		return spawnRules;
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
		MobConfigManager.addBeeGoal(goals, "break_door", true, 1, 100.0d, 0);
		return goals;
	}

	private static JsonObject buildZombieAgeOverride(
		boolean baby,
		Double health,
		Double damage,
		Double movementSpeed,
		Double knockbackResistance,
		int experience,
		double spawnWeight
	) {
		JsonObject root = new JsonObject();
		JsonObject stats = new JsonObject();
		stats.addProperty(MobConfigManager.FIELD_EXPERIENCE_DROP, experience);
		if (baby) {
			if (health != null) {
				stats.addProperty(MobConfigManager.FIELD_HEALTH, health);
			}
			if (damage != null) {
				stats.addProperty(MobConfigManager.FIELD_DAMAGE, damage);
			}
			if (movementSpeed != null) {
				stats.addProperty(MobConfigManager.FIELD_MOVEMENT_SPEED, movementSpeed);
			}
			if (knockbackResistance != null) {
				stats.addProperty(MobConfigManager.FIELD_KNOCKBACK_RESISTANCE, knockbackResistance);
			}
		}
		root.add(MobConfigManager.FIELD_MOB_STATS, stats);

		JsonObject spawnRules = new JsonObject();
		spawnRules.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, spawnWeight);
		root.add(MobConfigManager.FIELD_SPAWN_RULES, spawnRules);

		if (baby) {
			JsonObject behavior = new JsonObject();
			behavior.addProperty("calls_reinforcements_when_hurt", false);
			root.add(MobConfigManager.FIELD_MOB_BEHAVIOR, behavior);
		}
		return root;
	}
}

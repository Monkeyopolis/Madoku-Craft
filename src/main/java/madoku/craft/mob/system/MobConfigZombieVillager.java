package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigZombieVillager {
	private MobConfigZombieVillager() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_STATS, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_GOALS, true);
		root.addProperty(MobConfigManager.FIELD_MOB_BABY, true);
		root.addProperty(MobConfigManager.FIELD_MOB_VARIANT, false);

		JsonObject zombieVillager = new JsonObject();
		zombieVillager.addProperty(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true);
		zombieVillager.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALING, true);
		zombieVillager.addProperty(MobConfigManager.FIELD_WEAPON_DAMAGE, false);

		JsonObject difficultyScale = new JsonObject();
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d);
		zombieVillager.add(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale);

		JsonObject regionalDifficultyScale = new JsonObject();
		regionalDifficultyScale.addProperty(MobConfigManager.FIELD_ENABLED, true);
		zombieVillager.add(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale);

		JsonObject defaultGroup = new JsonObject();
		defaultGroup.add(
			MobConfigManager.FIELD_MOB_STATS,
			buildZombieVillagerMobStatsDefaults(
				20.0d,
				0.0d,
				5.0d,
				0.24d,
				0.25d,
				1.0d,
				7,
				resolveDefaultMobDropsReference()
			)
		);
		defaultGroup.add(MobConfigManager.FIELD_SPAWN_RULES, buildZombieVillagerSpawnRulesDefaults());
		defaultGroup.add(MobConfigManager.FIELD_MOB_BEHAVIOR, buildZombieVillagerBehaviorDefaults(false, false));
		defaultGroup.add(MobConfigManager.FIELD_MOB_GOALS, buildZombieVillagerGoalsDefaults());
		defaultGroup.add(MobConfigManager.FIELD_ADULT_GROUP, buildZombieVillagerAgeOverride(false, null, null, null, 7, 95.0d));
		defaultGroup.add(MobConfigManager.FIELD_BABY_GROUP, buildZombieVillagerAgeOverride(true, 10.0d, 2.5d, 0.24d, 3, 5.0d));
		zombieVillager.add(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup);

		root.add(MobConfigManager.FILE_ZOMBIE_VILLAGER, zombieVillager);
		return root;
	}

	private static JsonObject buildZombieVillagerMobStatsDefaults(
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

	private static String resolveDefaultMobDropsReference() {
		return "minecraft-entities-zombie-villager.json";
	}

	private static String resolveDefaultMobEquipmentReference() {
		return "minecraft-equipment-zombie-villager.json";
	}

	private static JsonObject buildZombieVillagerSpawnRulesDefaults() {
		JsonObject spawnRules = new JsonObject();
		spawnRules.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, 100.0d);
		spawnRules.add(MobConfigManager.FIELD_EQUIPMENT_SET, buildZombieVillagerEquipmentSetDefaults());
		return spawnRules;
	}

	private static JsonObject buildZombieVillagerEquipmentSetDefaults() {
		JsonObject equipmentSet = new JsonObject();
		equipmentSet.addProperty(MobConfigManager.FIELD_ENABLED, true);
		equipmentSet.addProperty(MobConfigManager.FIELD_MOB_EQUIPMENT, resolveDefaultMobEquipmentReference());
		equipmentSet.addProperty(MobConfigManager.FIELD_EQUIPMENT_CHANCE, 10.0d);
		return equipmentSet;
	}

	private static JsonObject buildZombieVillagerBehaviorDefaults(boolean canBreakDoorsDefault, boolean canPickUpLootDefault) {
		JsonObject behavior = new JsonObject();
		behavior.addProperty(MobConfigManager.FIELD_CAN_BREAK_DOORS, canBreakDoorsDefault);
		behavior.addProperty(MobConfigManager.FIELD_CAN_PICK_UP_LOOT, canPickUpLootDefault);
		behavior.addProperty("calls_reinforcements_when_hurt", false);
		return behavior;
	}

	private static JsonObject buildZombieVillagerGoalsDefaults() {
		JsonObject goals = new JsonObject();
		goals.addProperty(MobConfigManager.FIELD_ENABLED, true);
		MobConfigManager.addBeeGoal(goals, "break_door", true, 1, 100.0d, 0);
		return goals;
	}

	private static JsonObject buildZombieVillagerAgeOverride(
		boolean baby,
		Double health,
		Double damage,
		Double movementSpeed,
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

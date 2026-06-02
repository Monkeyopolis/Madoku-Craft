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
		defaultGroup.add(
			MobConfigManager.FIELD_MOB_STATS,
			buildHuskMobStatsDefaults(
				28.0d,
				2.0d,
				7.0d,
				0.18d,
				0.4d,
				1.0d,
				7,
				resolveDefaultMobDropsReference()
			)
		);
		defaultGroup.add(MobConfigManager.FIELD_SPAWN_RULES, buildHuskSpawnRulesDefaults());
		defaultGroup.add(MobConfigManager.FIELD_MOB_BEHAVIOR, buildHuskBehaviorDefaults(true, true));
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
		return "minecraft-entities-husk.json";
	}

	private static String resolveDefaultMobEquipmentReference() {
		return "minecraft-equipment-husk.json";
	}

	private static JsonObject buildHuskSpawnRulesDefaults() {
		JsonObject spawnRules = new JsonObject();
		spawnRules.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, 90.0d);
		spawnRules.add(MobConfigManager.FIELD_EQUIPMENT_SET, buildHuskEquipmentSetDefaults());
		return spawnRules;
	}

	private static JsonObject buildHuskEquipmentSetDefaults() {
		JsonObject equipmentSet = new JsonObject();
		equipmentSet.addProperty(MobConfigManager.FIELD_ENABLED, true);
		equipmentSet.addProperty(MobConfigManager.FIELD_MOB_EQUIPMENT, resolveDefaultMobEquipmentReference());
		equipmentSet.addProperty(MobConfigManager.FIELD_EQUIPMENT_CHANCE, 10.0d);
		return equipmentSet;
	}

	private static JsonObject buildHuskBehaviorDefaults(boolean canBreakDoorsDefault, boolean canPickUpLootDefault) {
		JsonObject behavior = new JsonObject();
		behavior.addProperty(MobConfigManager.FIELD_CAN_BREAK_DOORS, canBreakDoorsDefault);
		behavior.addProperty(MobConfigManager.FIELD_CAN_PICK_UP_LOOT, canPickUpLootDefault);
		behavior.addProperty(MobConfigManager.FIELD_APPLIES_HUNGER_ON_HIT, true);
		behavior.addProperty("calls_reinforcements_when_hurt", false);
		return behavior;
	}

	private static JsonObject buildHuskGoalsDefaults() {
		JsonObject goals = new JsonObject();
		goals.addProperty(MobConfigManager.FIELD_ENABLED, true);
		MobConfigManager.addBeeGoal(goals, "break_door", true, 1, 100.0d, 0);
		return goals;
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
		JsonObject stats = new JsonObject();
		stats.addProperty(MobConfigManager.FIELD_EXPERIENCE_DROP, experience);
		if (health != null) {
			stats.addProperty(MobConfigManager.FIELD_HEALTH, health);
		}
		if (armor != null) {
			stats.addProperty(MobConfigManager.FIELD_ARMOR, armor);
		}
		if (damage != null) {
			stats.addProperty(MobConfigManager.FIELD_DAMAGE, damage);
		}
		if (baby && knockbackResistance != null) {
			stats.addProperty(MobConfigManager.FIELD_KNOCKBACK_RESISTANCE, knockbackResistance);
		}
		root.add(MobConfigManager.FIELD_MOB_STATS, stats);

		JsonObject spawnRules = new JsonObject();
		spawnRules.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, spawnWeight);
		root.add(MobConfigManager.FIELD_SPAWN_RULES, spawnRules);
		return root;
	}
}

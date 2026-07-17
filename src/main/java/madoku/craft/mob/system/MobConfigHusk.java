package madoku.craft.mob.system;

import com.google.gson.JsonObject;

import madoku.craft.api.json.JSONFormatManager;

public final class MobConfigHusk {
	private MobConfigHusk() {
	}

	public static JsonObject buildDefaults() {
		JsonObject huskStats = JSONFormatManager.object()
			.putAll(buildHuskMobStatsDefaults(
				28.0d,
				2.0d,
				7.0d,
				0.18d,
				0.4d,
				1.0d,
				7,
				resolveDefaultMobDropsReference()
			))
			.put(MobConfigManager.FIELD_MOB_EFFECT, MobConfigManager.buildMobEffectDefaults("minecraft:slowness", 15))
			.build();

		JsonObject defaultGroup = JSONFormatManager.object()
			.put(MobConfigManager.FIELD_MOB_STATS, huskStats)
			.put(MobConfigManager.FIELD_SPAWN_RULES, buildHuskSpawnRulesDefaults())
			.put(MobConfigManager.FIELD_MOB_BEHAVIOR, buildHuskBehaviorDefaults(true))
			.put(MobConfigManager.FIELD_MOB_GOALS, buildHuskGoalsDefaults())
			.put(MobConfigManager.FIELD_ADULT_GROUP, buildHuskAgeOverride(false, 0.4, 7, 90.0d, null, null, null))
			.put(MobConfigManager.FIELD_BABY_GROUP, buildHuskAgeOverride(true, 0.2d, 3, 10.0d, 14.0d, 1.0d, 3.5d))
			.build();

		JsonObject husk = JSONFormatManager.object()
			.put(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true)
			.put(MobConfigManager.FIELD_DIFFICULTY_SCALING, true)
			.put(MobConfigManager.FIELD_WEAPON_DAMAGE, false)
			.object(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale -> difficultyScale
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d))
			.object(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale ->
				regionalDifficultyScale.put(MobConfigManager.FIELD_ENABLED, true))
			.put(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup)
			.build();

		return JSONFormatManager.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_OVERRIDE_STATS, true)
			.put(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true)
			.put(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true)
			.put(MobConfigManager.FIELD_OVERRIDE_GOALS, true)
			.put(MobConfigManager.FIELD_MOB_BABY, true)
			.put(MobConfigManager.FIELD_MOB_VARIANT, true)
			.put(MobConfigManager.FILE_HUSK, husk)
			.build();
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
		return JSONFormatManager.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_MOB_EQUIPMENT, resolveDefaultMobEquipmentReference())
			.put(MobConfigManager.FIELD_EQUIPMENT_CHANCE, 10.0d)
			.build();
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
		JSONFormatManager.ObjectBuilder root = JSONFormatManager.object()
			.put(
				MobConfigManager.FIELD_SPAWN_RULES,
				MobConfigManager.mobSpawnRules().spawnWeight(spawnWeight).build()
			);
		if (baby) {
			root.put(
				MobConfigManager.FIELD_MOB_STATS,
				MobConfigManager.buildMobStatsDefaults(
					health,
					armor,
					damage,
					null,
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
		return root.build();
	}
}


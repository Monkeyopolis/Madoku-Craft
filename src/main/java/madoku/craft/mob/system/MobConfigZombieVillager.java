package madoku.craft.mob.system;

import com.google.gson.JsonObject;

import madoku.craft.config.JsonFormatBuilder;

public final class MobConfigZombieVillager {
	private MobConfigZombieVillager() {
	}

	public static JsonObject buildDefaults() {
		JsonObject defaultGroup = JsonFormatBuilder.object()
			.put(
				MobConfigManager.FIELD_MOB_STATS,
				buildZombieVillagerMobStatsDefaults(
					20.0d,
					null,
					5.0d,
					0.24d,
					null,
					1.0d,
					7,
					resolveDefaultMobDropsReference()
				)
			)
			.put(MobConfigManager.FIELD_SPAWN_RULES, buildZombieVillagerSpawnRulesDefaults())
			.put(MobConfigManager.FIELD_MOB_BEHAVIOR, buildZombieVillagerBehaviorDefaults(false))
			.put(MobConfigManager.FIELD_MOB_GOALS, buildZombieVillagerGoalsDefaults())
			.put(MobConfigManager.FIELD_ADULT_GROUP, buildZombieVillagerAgeOverride(false, null, null, null, 7, 90.0d))
			.put(MobConfigManager.FIELD_BABY_GROUP, buildZombieVillagerAgeOverride(true, 10.0d, 2.5d, 0.24d, 3, 10.0d))
			.build();

		JsonObject zombieVillager = JsonFormatBuilder.object()
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

		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_OVERRIDE_STATS, true)
			.put(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true)
			.put(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true)
			.put(MobConfigManager.FIELD_OVERRIDE_GOALS, true)
			.put(MobConfigManager.FIELD_MOB_BABY, true)
			.put(MobConfigManager.FIELD_MOB_VARIANT, false)
			.put(MobConfigManager.FILE_ZOMBIE_VILLAGER, zombieVillager)
			.build();
	}

	private static JsonObject buildZombieVillagerMobStatsDefaults(
		double health,
		Double armor,
		double damage,
		double movementSpeed,
		Double knockbackResistance,
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
		return "minecraft-entities-zombie-villager.json";
	}

	private static String resolveDefaultMobEquipmentReference() {
		return "minecraft-equipment-zombie-villager.json";
	}

	private static JsonObject buildZombieVillagerSpawnRulesDefaults() {
		return MobConfigManager.mobSpawnRules()
			.spawnWeight(100.0d)
			.equipmentSet(buildZombieVillagerEquipmentSetDefaults())
			.build();
	}

	private static JsonObject buildZombieVillagerEquipmentSetDefaults() {
		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_MOB_EQUIPMENT, resolveDefaultMobEquipmentReference())
			.put(MobConfigManager.FIELD_EQUIPMENT_CHANCE, 10.0d)
			.build();
	}

	private static JsonObject buildZombieVillagerBehaviorDefaults(boolean canPickUpLootDefault) {
		return MobConfigManager.buildMobBehaviorDefaults(behavior -> {
			behavior.addProperty(MobConfigManager.FIELD_CAN_PICK_UP_LOOT, canPickUpLootDefault);
			behavior.addProperty(MobConfigManager.FIELD_CALLS_REINFORCEMENTS_WHEN_HURT, false);
		});
	}

	private static JsonObject buildZombieVillagerGoalsDefaults() {
		return MobConfigManager.buildMobGoalsDefaults(goals -> {
			MobConfigManager.addMobGoal(goals, "hurt-by-target", true, 1, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "target-player", true, 2, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "melee-attack", true, 4, 100.0d, 20);
		});
	}

	private static JsonObject buildZombieVillagerAgeOverride(
		boolean baby,
		Double health,
		Double damage,
		Double movementSpeed,
		int experience,
		double spawnWeight
	) {
		JsonFormatBuilder.ObjectBuilder root = JsonFormatBuilder.object()
			.put(
				MobConfigManager.FIELD_SPAWN_RULES,
				MobConfigManager.mobSpawnRules().spawnWeight(spawnWeight).build()
			);
		if (baby) {
			root.put(
				MobConfigManager.FIELD_MOB_STATS,
				MobConfigManager.buildMobStatsDefaults(
					health,
					null,
					damage,
					movementSpeed,
					null,
					null,
					null,
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

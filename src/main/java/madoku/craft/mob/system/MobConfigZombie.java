package madoku.craft.mob.system;

import com.google.gson.JsonObject;

import madoku.craft.config.JsonFormatBuilder;

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
		boolean zombieFile = MobConfigManager.FILE_ZOMBIE.equals(mobKey);
		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_OVERRIDE_STATS, true)
			.put(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true)
			.put(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true)
			.put(MobConfigManager.FIELD_OVERRIDE_GOALS, true)
			.put(MobConfigManager.FIELD_MOB_BABY, true)
			.put(MobConfigManager.FIELD_MOB_VARIANT, zombieFile)
			.object(mobKey, mob -> {
				mob.put(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true)
					.put(MobConfigManager.FIELD_DIFFICULTY_SCALING, true)
					.put(MobConfigManager.FIELD_WEAPON_DAMAGE, false)
					.object(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale -> difficultyScale
						.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d)
						.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d)
						.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d)
						.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d))
					.object(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale ->
						regionalDifficultyScale.put(MobConfigManager.FIELD_ENABLED, true))
					.object(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup -> defaultGroup
						.put(
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
						)
						.put(MobConfigManager.FIELD_SPAWN_RULES, buildZombieSpawnRulesDefaults(mobKey))
						.put(MobConfigManager.FIELD_MOB_BEHAVIOR, buildZombieBehaviorDefaults(false))
						.put(MobConfigManager.FIELD_MOB_GOALS, buildZombieGoalsDefaults())
						.put(MobConfigManager.FIELD_ADULT_GROUP, buildZombieAgeOverride(false, null, null, null, null, null, experience, 90.0d))
						.put(MobConfigManager.FIELD_BABY_GROUP, buildZombieAgeOverride(true, babyHealth, babyDamage, babySpeed, 0.0d, 0.0d, 3, 10.0d))
					);
				if (zombieFile) {
					mob.put("zombie-jockey", buildZombieJockeyVariantDefaults());
					mob.put("zombie-villager", buildZombieVillagerVariantDefaults());
				}
			})
			.build();
	}

	private static JsonObject buildZombieJockeyVariantDefaults() {
		JsonObject jockey = JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.object(MobConfigManager.FIELD_JOCKEY_PASSENGER, passenger -> passenger
				.object(MobConfigManager.FIELD_MOB, passengerMob -> passengerMob
					.put(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:zombie")
					.put(MobConfigManager.FIELD_BABY_GROUP, "minecraft:zombie"))
				.put(MobConfigManager.FIELD_MAIN_HAND, "minecraft:stone_spear"))
			.object(MobConfigManager.FIELD_JOCKEY_MOUNT, mount -> mount
				.object(MobConfigManager.FIELD_MOB, mountMob -> mountMob
					.put(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:zombie_horse")
					.put(MobConfigManager.FIELD_BABY_GROUP, "minecraft:chicken")))
			.build();
		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_SHARED_COMPONENTS, true)
			.put(
				MobConfigManager.FIELD_SPAWN_RULES,
				MobConfigManager.mobSpawnRules().spawnWeight(10.0d).mobJockey(jockey).build()
			)
			.build();
	}

	private static JsonObject buildZombieVillagerVariantDefaults() {
		JsonObject alternativeMob = JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_MOB, "minecraft:zombie_villager")
			.build();
		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_SHARED_COMPONENTS, true)
			.put(
				MobConfigManager.FIELD_SPAWN_RULES,
				MobConfigManager.mobSpawnRules().spawnWeight(10.0d).spawnAlternativeMob(alternativeMob).build()
			)
			.build();
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
		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_MOB_EQUIPMENT, resolveDefaultMobEquipmentReference(mobKey))
			.put(MobConfigManager.FIELD_EQUIPMENT_CHANCE, 10.0d)
			.build();
	}

	private static JsonObject buildZombieBehaviorDefaults(boolean canPickUpLootDefault) {
		return MobConfigManager.buildMobBehaviorDefaults(behavior -> {
			behavior.addProperty(MobConfigManager.FIELD_CAN_PICK_UP_LOOT, canPickUpLootDefault);
			behavior.addProperty(MobConfigManager.FIELD_CALLS_REINFORCEMENTS_WHEN_HURT, false);
		});
	}

	private static JsonObject buildZombieGoalsDefaults() {
		return MobConfigManager.buildMobGoalsDefaults(goals -> {
			MobConfigManager.addMobGoal(goals, "hurt-by-target", true, 1, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "target-player", true, 2, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "melee-attack", true, 4, 100.0d, 20);
		});
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
			root.put(
				MobConfigManager.FIELD_MOB_BEHAVIOR,
				MobConfigManager.buildMobBehaviorDefaults(behavior ->
					behavior.addProperty(MobConfigManager.FIELD_CALLS_REINFORCEMENTS_WHEN_HURT, false)
				)
			);
		}
		return root.build();
	}
}

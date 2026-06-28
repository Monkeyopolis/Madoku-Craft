package madoku.craft.mob.system;

import com.google.gson.JsonObject;

import madoku.craft.config.JsonFormatBuilder;

public final class MobConfigSkeleton {
	private static final String DEFAULT_MOB_DROPS = "minecraft-entities-skeleton.json";

	private MobConfigSkeleton() {
	}

	public static JsonObject buildDefaults() {
		JsonObject difficultyScale = JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d)
			.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d)
			.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_RANGED_DAMAGE, 0.10d)
			.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d)
			.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_SWIMMING_SPEED, 0.10d)
			.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d)
			.build();

		JsonObject skeleton = JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true)
			.put(MobConfigManager.FIELD_DIFFICULTY_SCALING, true)
			.put(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale)
			.put(
				MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING,
				JsonFormatBuilder.object().put(MobConfigManager.FIELD_ENABLED, true).build()
			)
			.put(
				MobConfigManager.FIELD_DEFAULT_GROUP,
				JsonFormatBuilder.object()
					.put(MobConfigManager.FIELD_SPAWN_WEIGHT, 80.0d)
					.put(MobConfigManager.FIELD_MOB_STATS, buildSkeletonMobStatsDefaults())
					.put(
						MobConfigManager.FIELD_SPAWN_RULES,
						MobConfigManager.mobSpawnRules()
							.spawnWeight(80.0d)
							.equipmentSet(buildSkeletonEquipmentSetDefaults())
							.build()
					)
					.put(
						MobConfigManager.FIELD_MOB_BEHAVIOR,
						MobConfigManager.buildMobBehaviorDefaults(behavior -> behavior.addProperty(MobConfigManager.FIELD_BOW_ATTACK, true))
					)
					.put(
						MobConfigManager.FIELD_MOB_GOALS,
						MobConfigManager.buildMobGoalsDefaults(goals -> {
							MobConfigManager.addMobGoal(goals, "hurt-by-target", true, 1, 100.0d, 0);
							MobConfigManager.addMobGoal(goals, "target-player", true, 2, 100.0d, 0);
							MobConfigManager.addMobGoal(goals, "ranged-attack", true, 4, 100.0d, 20);
						})
					)
					.build()
			)
			.put("melee-skeleton", buildMeleeSkeletonVariantDefaults())
			.put("skeleton-jockey", buildSkeletonJockeyVariantDefaults())
			.build();

		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_OVERRIDE_STATS, true)
			.put(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true)
			.put(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true)
			.put(MobConfigManager.FIELD_OVERRIDE_GOALS, true)
			.put(MobConfigManager.FIELD_WEAPON_DAMAGE, false)
			.put(MobConfigManager.FIELD_MOB_VARIANT, true)
			.put(MobConfigManager.FILE_SKELETON, skeleton)
			.build();
	}

	private static JsonObject buildSkeletonMobStatsDefaults() {
		JsonObject mobStats = MobConfigManager.buildMobStatsDefaults(
			16.0d,
			null,
			null,
			0.24d,
			null,
			null,
			null,
			1.0d,
			7,
			5.0d,
			0.7d,
			20.0d,
			10.0d,
			DEFAULT_MOB_DROPS
		);
		return JsonFormatBuilder.object()
			.putAll(mobStats)
			.put(MobConfigManager.FIELD_MOB_WEAPON, MobConfigManager.buildMobWeaponDefaults("minecraft:bow"))
			.build();
	}

	private static JsonObject buildMeleeSkeletonVariantDefaults() {
		JsonObject stats = MobConfigManager.buildMobStatsDefaults(
			20.0d,
			null,
			5.0d,
			0.24d,
			null,
			null,
			null,
			1.0d,
			7,
			null,
			null,
			null,
			null,
			DEFAULT_MOB_DROPS
		);
		stats = JsonFormatBuilder.object()
			.putAll(stats)
			.put(MobConfigManager.FIELD_MOB_WEAPON, MobConfigManager.buildMobWeaponDefaults("empty"))
			.put(MobConfigManager.FIELD_TRUE_DAMAGE, 1.0d)
			.build();
		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_MOB_STATS, stats)
			.put(
				MobConfigManager.FIELD_SPAWN_RULES,
				MobConfigManager.mobSpawnRules()
					.spawnWeight(10.0d)
					.equipmentSet(buildSkeletonEquipmentSetDefaults())
					.build()
			)
			.put(
				MobConfigManager.FIELD_MOB_GOALS,
				MobConfigManager.buildMobGoalsDefaults(goals -> {
					MobConfigManager.addMobGoal(goals, "hurt-by-target", true, 1, 100.0d, 0);
					MobConfigManager.addMobGoal(goals, "target-player", true, 2, 100.0d, 0);
					MobConfigManager.addMobGoal(goals, "melee-attack", true, 4, 100.0d, 20);
				})
			)
			.build();
	}

	private static JsonObject buildSkeletonEquipmentSetDefaults() {
		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_MOB_EQUIPMENT, "minecraft-equipment-skeleton.json")
			.put(MobConfigManager.FIELD_EQUIPMENT_CHANCE, 10.0d)
			.build();
	}

	private static JsonObject buildSkeletonJockeyVariantDefaults() {
		JsonObject jockey = JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.object(MobConfigManager.FIELD_JOCKEY_PASSENGER, passenger -> passenger
				.object(MobConfigManager.FIELD_MOB, passengerMob -> passengerMob
					.put(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:skeleton"))
				.put(MobConfigManager.FIELD_MAIN_HAND, "minecraft:bow"))
			.object(MobConfigManager.FIELD_JOCKEY_MOUNT, mount -> mount
				.object(MobConfigManager.FIELD_MOB, mountMob -> mountMob
					.put(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:spider")))
			.build();
		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_SHARED_COMPONENTS, true)
			.put(
				MobConfigManager.FIELD_SPAWN_RULES,
				MobConfigManager.mobSpawnRules().spawnWeight(10.0d).mobJockey(jockey).build()
			)
			.build();
	}
}


package madoku.craft.mob.system;

import com.google.gson.JsonObject;

import madoku.craft.config.JsonFormatBuilder;

public final class MobConfigWitherSkeleton {
	private static final String DEFAULT_MOB_DROPS = "minecraft-entities-wither-skeleton.json";

	private MobConfigWitherSkeleton() {
	}

	public static JsonObject buildDefaults() {
		JsonObject defaultGroup = JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_SPAWN_WEIGHT, 90.0d)
			.put(MobConfigManager.FIELD_MOB_STATS, buildWitherSkeletonStatsDefaults())
			.put(
				MobConfigManager.FIELD_SPAWN_RULES,
				MobConfigManager.mobSpawnRules()
					.spawnWeight(100.0d)
					.equipmentSet(buildWitherSkeletonEquipmentSetDefaults())
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
			.build();
		MobConfigManager.ensureMobSchema(defaultGroup, false);

		JsonObject witherSkeleton = JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true)
			.put(MobConfigManager.FIELD_DIFFICULTY_SCALING, true)
			.object(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale -> difficultyScale
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_RANGED_DAMAGE, 0.10d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d))
			.object(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale ->
				regionalDifficultyScale.put(MobConfigManager.FIELD_ENABLED, true))
			.put(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup)
			.put("melee-wither-skeleton", buildMeleeWitherSkeletonVariantDefaults())
			.build();

		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_OVERRIDE_STATS, true)
			.put(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true)
			.put(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true)
			.put(MobConfigManager.FIELD_OVERRIDE_GOALS, true)
			.put(MobConfigManager.FIELD_WEAPON_DAMAGE, false)
			.put(MobConfigManager.FIELD_MOB_VARIANT, true)
			.put(MobConfigManager.FILE_WITHER_SKELETON, witherSkeleton)
			.build();
	}

	private static JsonObject buildWitherSkeletonStatsDefaults() {
		return JsonFormatBuilder.object()
			.putAll(MobConfigManager.buildMobStatsDefaults(
				20.0d,
				null,
				7.0d,
				0.25d,
				null,
				null,
				null,
				1.0d,
				11,
				6.0d,
				0.7d,
				20.0d,
				10.0d,
				DEFAULT_MOB_DROPS
			))
			.put(MobConfigManager.FIELD_MOB_WEAPON, MobConfigManager.buildMobWeaponDefaults("minecraft:bow"))
			.put(MobConfigManager.FIELD_MOB_EFFECT, MobConfigManager.buildMobEffectDefaults("minecraft:wither", 15))
			.build();
	}

	private static JsonObject buildWitherSkeletonEquipmentSetDefaults() {
		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_MOB_EQUIPMENT, "minecraft-equipment-wither-skeleton.json")
			.put(MobConfigManager.FIELD_EQUIPMENT_CHANCE, 10.0d)
			.build();
	}

	private static JsonObject buildMeleeWitherSkeletonVariantDefaults() {
		JsonObject stats = JsonFormatBuilder.object()
			.putAll(MobConfigManager.buildMobStatsDefaults(
				20.0d,
				null,
				7.0d,
				0.25d,
				null,
				null,
				null,
				1.0d,
				11,
				null,
				null,
				null,
				null,
				DEFAULT_MOB_DROPS
			))
			.put(MobConfigManager.FIELD_MOB_WEAPON, MobConfigManager.buildMobWeaponDefaults("minecraft:netherite_sword"))
			.put(MobConfigManager.FIELD_MOB_EFFECT, MobConfigManager.buildMobEffectDefaults("minecraft:wither", 15))
			.build();
		return JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_SPAWN_WEIGHT, 10.0d)
			.put(MobConfigManager.FIELD_MOB_STATS, stats)
			.put(
				MobConfigManager.FIELD_SPAWN_RULES,
				MobConfigManager.mobSpawnRules()
					.spawnWeight(100.0d)
					.equipmentSet(buildWitherSkeletonEquipmentSetDefaults())
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
}

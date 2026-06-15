package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigWitherSkeleton {
	private static final String DEFAULT_MOB_DROPS = "minecraft-entities-wither-skeleton.json";

	private MobConfigWitherSkeleton() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_STATS, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_GOALS, true);
		root.addProperty(MobConfigManager.FIELD_WEAPON_DAMAGE, false);
		root.addProperty(MobConfigManager.FIELD_MOB_VARIANT, true);

		JsonObject witherSkeleton = new JsonObject();
		witherSkeleton.addProperty(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true);
		witherSkeleton.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALING, true);

		JsonObject difficultyScale = new JsonObject();
		MobConfigManager.addDifficultyScaleEntry(difficultyScale, MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d);
		MobConfigManager.addDifficultyScaleEntry(difficultyScale, MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d);
		MobConfigManager.addDifficultyScaleEntry(difficultyScale, MobConfigManager.FIELD_DIFFICULTY_SCALE_RANGED_DAMAGE, 0.10d);
		MobConfigManager.addDifficultyScaleEntry(difficultyScale, MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d);
		MobConfigManager.addDifficultyScaleEntry(difficultyScale, MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d);
		witherSkeleton.add(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale);

		JsonObject regionalDifficultyScale = new JsonObject();
		regionalDifficultyScale.addProperty(MobConfigManager.FIELD_ENABLED, true);
		witherSkeleton.add(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale);

		JsonObject defaultGroup = new JsonObject();
		defaultGroup.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, 90.0d);
		defaultGroup.add(
			MobConfigManager.FIELD_MOB_STATS,
			buildWitherSkeletonStatsDefaults()
		);
		defaultGroup.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules()
				.spawnWeight(100.0d)
				.equipmentSet(buildWitherSkeletonEquipmentSetDefaults())
				.build()
		);
		defaultGroup.add(
			MobConfigManager.FIELD_MOB_BEHAVIOR,
			MobConfigManager.buildMobBehaviorDefaults(behavior -> behavior.addProperty(MobConfigManager.FIELD_BOW_ATTACK, true))
		);
		defaultGroup.add(
			MobConfigManager.FIELD_MOB_GOALS,
			MobConfigManager.buildMobGoalsDefaults(goals -> {
				MobConfigManager.addMobGoal(goals, "hurt-by-target", true, 1, 100.0d, 0);
				MobConfigManager.addMobGoal(goals, "target-player", true, 2, 100.0d, 0);
				MobConfigManager.addMobGoal(goals, "ranged-attack", true, 4, 100.0d, 20);
			})
		);
		MobConfigManager.ensureMobSchema(defaultGroup, false);
		witherSkeleton.add(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup);
		witherSkeleton.add("melee-wither-skeleton", buildMeleeWitherSkeletonVariantDefaults());

		root.add(MobConfigManager.FILE_WITHER_SKELETON, witherSkeleton);
		return root;
	}

	private static JsonObject buildWitherSkeletonStatsDefaults() {
		JsonObject mobStats = MobConfigManager.buildMobStatsDefaults(
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
		);
		mobStats.add(
			MobConfigManager.FIELD_MOB_WEAPON,
			MobConfigManager.buildMobWeaponDefaults("minecraft:bow")
		);
		mobStats.add(
			MobConfigManager.FIELD_MOB_EFFECT,
			MobConfigManager.buildMobEffectDefaults("minecraft:wither", 15)
		);
		return mobStats;
	}

	private static JsonObject buildWitherSkeletonEquipmentSetDefaults() {
		JsonObject equipmentSet = new JsonObject();
		equipmentSet.addProperty(MobConfigManager.FIELD_ENABLED, true);
		equipmentSet.addProperty(MobConfigManager.FIELD_MOB_EQUIPMENT, "minecraft-equipment-wither-skeleton.json");
		equipmentSet.addProperty(MobConfigManager.FIELD_EQUIPMENT_CHANCE, 10.0d);
		return equipmentSet;
	}

	private static JsonObject buildMeleeWitherSkeletonVariantDefaults() {
		JsonObject variant = new JsonObject();
		variant.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, 10.0d);

		JsonObject stats = MobConfigManager.buildMobStatsDefaults(
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
		);
		stats.add(
			MobConfigManager.FIELD_MOB_WEAPON,
			MobConfigManager.buildMobWeaponDefaults("minecraft:netherite_sword")
		);
		stats.add(
			MobConfigManager.FIELD_MOB_EFFECT,
			MobConfigManager.buildMobEffectDefaults("minecraft:wither", 15)
		);
		variant.add(MobConfigManager.FIELD_MOB_STATS, stats);
		variant.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules()
				.spawnWeight(10.0d)
				.equipmentSet(buildWitherSkeletonEquipmentSetDefaults())
				.build()
		);
		variant.add(
			MobConfigManager.FIELD_MOB_GOALS,
			MobConfigManager.buildMobGoalsDefaults(goals -> {
				MobConfigManager.addMobGoal(goals, "hurt-by-target", true, 1, 100.0d, 0);
				MobConfigManager.addMobGoal(goals, "target-player", true, 2, 100.0d, 0);
				MobConfigManager.addMobGoal(goals, "melee-attack", true, 4, 100.0d, 20);
			})
		);
		return variant;
	}
}

package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigParched {
	private static final String DEFAULT_MOB_DROPS = "minecraft-entities-parched.json";

	private MobConfigParched() {
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

		JsonObject parched = new JsonObject();
		parched.addProperty(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true);
		parched.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALING, true);

		JsonObject difficultyScale = new JsonObject();
		MobConfigManager.addDifficultyScaleEntry(difficultyScale, MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d);
		MobConfigManager.addDifficultyScaleEntry(difficultyScale, MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d);
		MobConfigManager.addDifficultyScaleEntry(difficultyScale, MobConfigManager.FIELD_DIFFICULTY_SCALE_RANGED_DAMAGE, 0.10d);
		MobConfigManager.addDifficultyScaleEntry(difficultyScale, MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d);
		MobConfigManager.addDifficultyScaleEntry(difficultyScale, MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d);
		parched.add(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale);

		JsonObject regionalDifficultyScale = new JsonObject();
		regionalDifficultyScale.addProperty(MobConfigManager.FIELD_ENABLED, true);
		parched.add(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale);

		JsonObject defaultGroup = new JsonObject();
		defaultGroup.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, 90.0d);
		defaultGroup.add(
			MobConfigManager.FIELD_MOB_STATS,
			buildParchedMobStatsDefaults()
		);
		defaultGroup.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules()
				.spawnWeight(90.0d)
				.equipmentSet(buildParchedEquipmentSetDefaults())
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
		parched.add(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup);
		parched.add("parched-jockey", buildParchedJockeyVariantDefaults());

		root.add(MobConfigManager.FILE_PARCHED, parched);
		return root;
	}

	private static JsonObject buildParchedMobStatsDefaults() {
		JsonObject mobStats = MobConfigManager.buildMobStatsDefaults(
			12.0d,
			null,
			2.0d,
			0.27d,
			null,
			null,
			null,
			1.0d,
			7,
			4.0d,
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
			MobConfigManager.buildMobEffectDefaults("minecraft:slowness", 15)
		);
		return mobStats;
	}

	private static JsonObject buildParchedEquipmentSetDefaults() {
		JsonObject equipmentSet = new JsonObject();
		equipmentSet.addProperty(MobConfigManager.FIELD_ENABLED, true);
		equipmentSet.addProperty(MobConfigManager.FIELD_MOB_EQUIPMENT, "minecraft-equipment-parched.json");
		equipmentSet.addProperty(MobConfigManager.FIELD_EQUIPMENT_CHANCE, 10.0d);
		return equipmentSet;
	}

	private static JsonObject buildParchedJockeyVariantDefaults() {
		JsonObject variant = new JsonObject();
		variant.addProperty(MobConfigManager.FIELD_SHARED_COMPONENTS, true);

		JsonObject passenger = new JsonObject();
		JsonObject passengerMob = new JsonObject();
		passengerMob.addProperty(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:parched");
		passenger.add(MobConfigManager.FIELD_MOB, passengerMob);
		passenger.addProperty(MobConfigManager.FIELD_MAIN_HAND, "minecraft:bow");

		JsonObject mount = new JsonObject();
		JsonObject mountMob = new JsonObject();
		mountMob.addProperty(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:spider");
		mount.add(MobConfigManager.FIELD_MOB, mountMob);

		JsonObject jockey = new JsonObject();
		jockey.addProperty(MobConfigManager.FIELD_ENABLED, true);
		jockey.add(MobConfigManager.FIELD_JOCKEY_PASSENGER, passenger);
		jockey.add(MobConfigManager.FIELD_JOCKEY_MOUNT, mount);

		variant.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules().spawnWeight(10.0d).mobJockey(jockey).build()
		);
		return variant;
	}
}

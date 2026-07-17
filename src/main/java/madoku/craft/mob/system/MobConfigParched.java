package madoku.craft.mob.system;

import com.google.gson.JsonObject;

import madoku.craft.api.json.JSONFormatManager;

public final class MobConfigParched {
	private static final String DEFAULT_MOB_DROPS = "minecraft-entities-parched.json";

	private MobConfigParched() {
	}

	public static JsonObject buildDefaults() {
		JsonObject defaultGroup = JSONFormatManager.object()
			.put(MobConfigManager.FIELD_SPAWN_WEIGHT, 90.0d)
			.put(MobConfigManager.FIELD_MOB_STATS, buildParchedMobStatsDefaults())
			.put(
				MobConfigManager.FIELD_SPAWN_RULES,
				MobConfigManager.mobSpawnRules()
					.spawnWeight(90.0d)
					.equipmentSet(buildParchedEquipmentSetDefaults())
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

		JsonObject parched = JSONFormatManager.object()
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
			.put("parched-jockey", buildParchedJockeyVariantDefaults())
			.build();

		return JSONFormatManager.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_OVERRIDE_STATS, true)
			.put(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true)
			.put(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true)
			.put(MobConfigManager.FIELD_OVERRIDE_GOALS, true)
			.put(MobConfigManager.FIELD_WEAPON_DAMAGE, false)
			.put(MobConfigManager.FIELD_MOB_VARIANT, true)
			.put(MobConfigManager.FILE_PARCHED, parched)
			.build();
	}

	private static JsonObject buildParchedMobStatsDefaults() {
		return JSONFormatManager.object()
			.putAll(MobConfigManager.buildMobStatsDefaults(
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
			))
			.put(MobConfigManager.FIELD_MOB_WEAPON, MobConfigManager.buildMobWeaponDefaults("minecraft:bow"))
			.put(MobConfigManager.FIELD_MOB_EFFECT, MobConfigManager.buildMobEffectDefaults("minecraft:slowness", 15))
			.build();
	}

	private static JsonObject buildParchedEquipmentSetDefaults() {
		return JSONFormatManager.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.put(MobConfigManager.FIELD_MOB_EQUIPMENT, "minecraft-equipment-parched.json")
			.put(MobConfigManager.FIELD_EQUIPMENT_CHANCE, 10.0d)
			.build();
	}

	private static JsonObject buildParchedJockeyVariantDefaults() {
		JsonObject jockey = JSONFormatManager.object()
			.put(MobConfigManager.FIELD_ENABLED, true)
			.object(MobConfigManager.FIELD_JOCKEY_PASSENGER, passenger -> passenger
				.object(MobConfigManager.FIELD_MOB, passengerMob -> passengerMob
					.put(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:parched"))
				.put(MobConfigManager.FIELD_MAIN_HAND, "minecraft:bow"))
			.object(MobConfigManager.FIELD_JOCKEY_MOUNT, mount -> mount
				.object(MobConfigManager.FIELD_MOB, mountMob -> mountMob
					.put(MobConfigManager.FIELD_ADULT_GROUP, "minecraft:spider")))
			.build();
		return JSONFormatManager.object()
			.put(MobConfigManager.FIELD_SHARED_COMPONENTS, true)
			.put(
				MobConfigManager.FIELD_SPAWN_RULES,
				MobConfigManager.mobSpawnRules().spawnWeight(10.0d).mobJockey(jockey).build()
			)
			.build();
	}
}


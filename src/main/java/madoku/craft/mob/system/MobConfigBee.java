package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigBee {
	private MobConfigBee() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_STATS, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_GOALS, true);
		root.addProperty(MobConfigManager.FIELD_MOB_VARIANT, false);
		root.addProperty(MobConfigManager.FIELD_MOB_BABY, true);

		JsonObject bee = new JsonObject();
		bee.addProperty(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true);
		bee.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALING, true);
		JsonObject difficultyScale = new JsonObject();
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_FLYING_SPEED, 0.10d);
		difficultyScale.addProperty(MobConfigManager.FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP, 0.25d);
		bee.add(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale);
		JsonObject regionalDifficultyScale = new JsonObject();
		regionalDifficultyScale.addProperty(MobConfigManager.FIELD_ENABLED, true);
		bee.add(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING, regionalDifficultyScale);

		JsonObject defaultGroup = new JsonObject();
		defaultGroup.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, 100.0d);
		defaultGroup.add(MobConfigManager.FIELD_MOB_STATS, buildSharedBeeStatsDefaults());
		defaultGroup.add(MobConfigManager.FIELD_MOB_BEHAVIOR, buildSharedBeeBehaviorDefaults());
		defaultGroup.add(MobConfigManager.FIELD_MOB_GOALS, buildSharedBeeGoalsDefaults());
		defaultGroup.add(MobConfigManager.FIELD_ADULT_GROUP, buildBeeAgeOverrides(false));
		defaultGroup.add(MobConfigManager.FIELD_BABY_GROUP, buildBeeAgeOverrides(true));
		bee.add(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup);

		root.add(MobConfigManager.FILE_BEE, bee);
		return root;
	}

	private static JsonObject buildSharedBeeStatsDefaults() {
		JsonObject stats = new JsonObject();
		stats.addProperty(MobConfigManager.FIELD_HEALTH, 10.0d);
		stats.addProperty(MobConfigManager.FIELD_DAMAGE, 2.0d);
		stats.addProperty(MobConfigManager.FIELD_MOVEMENT_SPEED, 0.30d);
		stats.addProperty("flying_speed", 0.60d);
		stats.addProperty(MobConfigManager.FIELD_SCALE, 0.5d);
		stats.addProperty(MobConfigManager.FIELD_MOB_DROPS, "minecraft-entities-bee.json");
		return stats;
	}

	private static JsonObject buildSharedBeeBehaviorDefaults() {
		JsonObject behavior = new JsonObject();
		JsonObject pollination = MobConfigManager.getOrCreateObject(behavior, "pollination");
		pollination.addProperty(MobConfigManager.FIELD_ENABLED, false);
		pollination.addProperty("flower_search_radius", 8);
		pollination.addProperty("min_pollination_ticks", 400);
		pollination.addProperty("max_pollination_ticks", 800);
		pollination.addProperty("max_crops_growable", 10);
		pollination.addProperty("arrival_threshold", 0.1d);
		pollination.addProperty("position_change_chance", 25);
		pollination.addProperty("speed_modifier", 0.5d);
		pollination.addProperty("hover_height_within_flower", 0.6d);
		pollination.addProperty("hover_pos_offset", 0.33333334d);

		JsonObject pollinateCrops = MobConfigManager.getOrCreateObject(behavior, "pollinate-crops");
		pollinateCrops.addProperty(MobConfigManager.FIELD_ENABLED, true);
		pollinateCrops.addProperty("nectar_total_charges", 10);
		pollinateCrops.addProperty("search_duration_ticks", 1200);
		pollinateCrops.addProperty("search_radius_horizontal", 12);
		pollinateCrops.addProperty("search_radius_vertical", 4);
		pollinateCrops.addProperty("crop_reach_distance_sqr", 1.0d);
		pollinateCrops.addProperty("crop_reservation_ttl_ticks", 50);
		pollinateCrops.addProperty("move_speed_modifier", 1.0d);
		pollinateCrops.addProperty("arrival_threshold", 0.1d);
		pollinateCrops.addProperty("position_change_chance", 25);
		pollinateCrops.addProperty("hover_height_within_crop", 0.6d);
		pollinateCrops.addProperty("hover_pos_offset", 0.33333334d);
		pollinateCrops.addProperty("charge_interval_ticks", 20);
		pollinateCrops.addProperty("charges_spend_divisor", 2);
		pollinateCrops.addProperty("growth_percent_per_charge", 2.0d);

		JsonObject hive = MobConfigManager.getOrCreateObject(behavior, "hive");
		hive.addProperty(MobConfigManager.FIELD_ENABLED, true);
		hive.addProperty("hive_search_distance", 20);
		hive.addProperty("pathfind_to_hive_when_closer_than", 16);
		hive.addProperty("hive_close_enough_distance", 2);
		hive.addProperty("too_far_distance", 48);
		hive.addProperty("ticks_without_nectar_before_going_home", 3600);
		hive.addProperty("locate_hive_cooldown_ticks", 200);
		hive.addProperty("locate_flower_cooldown_ticks", 200);
		hive.addProperty("locate_flower_cooldown_min_ticks", 20);
		hive.addProperty("locate_flower_cooldown_max_ticks", 60);
		hive.addProperty("ticks_before_going_to_known_flower", 600);
		hive.addProperty("return_if_environment_demands_hive", true);
		hive.addProperty("max_travelling_ticks", 2400);
		hive.addProperty("max_blacklisted_hive_targets", 3);
		hive.addProperty("ticks_before_hive_drop", 60);

		JsonObject aggressionRoot = MobConfigManager.getOrCreateObject(behavior, "aggression");
		aggressionRoot.addProperty(MobConfigManager.FIELD_ENABLED, true);
		aggressionRoot.addProperty("retaliate_when_hurt", true);
		aggressionRoot.addProperty("persistent_anger_time_min_seconds", 20);
		aggressionRoot.addProperty("persistent_anger_time_max_seconds", 39);
		aggressionRoot.addProperty("min_attack_distance", 4);
		aggressionRoot.addProperty("target_players_when_angry", true);

		JsonObject stinging = MobConfigManager.getOrCreateObject(behavior, "stinging");
		stinging.addProperty(MobConfigManager.FIELD_ENABLED, true);
		stinging.addProperty("sting_on_target", true);
		stinging.addProperty("self_destruct_after_sting", true);
		stinging.addProperty("sting_death_countdown_ticks", 1200);
		stinging.addProperty("poison_seconds_normal", 10);
		stinging.addProperty("poison_seconds_hard", 18);
		stinging.addProperty("poison_amplifier", 0);

		JsonObject breeding = MobConfigManager.getOrCreateObject(behavior, "breeding");
		breeding.addProperty(MobConfigManager.FIELD_ENABLED, true);
		breeding.addProperty("favorite_food", "minecraft:flower");
		breeding.addProperty("baby_spawn_count_min", 1);
		breeding.addProperty("baby_spawn_count_max", 1);
		breeding.addProperty("breed_cooldown_ticks", 6000);

		JsonObject wandering = MobConfigManager.getOrCreateObject(behavior, "wandering");
		wandering.addProperty(MobConfigManager.FIELD_ENABLED, true);
		wandering.addProperty("default_wander_distance_reduction", 16);
		wandering.addProperty("restricted_wander_distance_reduction", 24);
		wandering.addProperty("wander_trigger_chance", 10);
		wandering.addProperty("wander_horizontal_range", 8);
		wandering.addProperty("wander_vertical_range", 7);
		return behavior;
	}

	private static JsonObject buildSharedBeeGoalsDefaults() {
		JsonObject goals = new JsonObject();
		goals.addProperty(MobConfigManager.FIELD_ENABLED, true);
		MobConfigManager.addMobGoal(goals, "bee_attack", true, 0, 100.0d, 0);
		goals.getAsJsonObject("bee_attack").addProperty("speed_modifier", 1.4d);
		goals.getAsJsonObject("bee_attack").addProperty("long_memory", true);
		MobConfigManager.addMobGoal(goals, "enter_hive", true, 1, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "breed", true, 2, 100.0d, 0);
		goals.getAsJsonObject("breed").addProperty("speed_modifier", 1.0d);
		MobConfigManager.addMobGoal(goals, "tempt_flower", true, 3, 100.0d, 0);
		goals.getAsJsonObject("tempt_flower").addProperty("speed_modifier", 1.25d);
		MobConfigManager.addMobGoal(goals, "validate_hive", true, 3, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "validate_flower", true, 3, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "pollinate", true, 4, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "follow_parent", true, 5, 100.0d, 0);
		goals.getAsJsonObject("follow_parent").addProperty("speed_modifier", 1.25d);
		MobConfigManager.addMobGoal(goals, "locate_hive", true, 5, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "go_to_hive", true, 5, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "go_to_known_flower", true, 6, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "grow_crop", true, 7, 100.0d, 0);
		goals.getAsJsonObject("grow_crop").addProperty("growth_chance", 30);
		MobConfigManager.addMobGoal(goals, "wander", true, 8, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "float", true, 9, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "hurt_by_target", true, 1, 100.0d, 0);
		goals.getAsJsonObject("hurt_by_target").addProperty("alert_others", true);
		MobConfigManager.addMobGoal(goals, "become_angry_target", true, 2, 100.0d, 0);
		MobConfigManager.addMobGoal(goals, "reset_universal_anger_target", true, 3, 100.0d, 0);
		return goals;
	}

	private static JsonObject buildBeeAgeOverrides(boolean baby) {
		JsonObject group = new JsonObject();
		JsonObject stats = new JsonObject();
		if (baby) {
			stats.addProperty(MobConfigManager.FIELD_HEALTH, 5.0d);
			stats.addProperty(MobConfigManager.FIELD_MOVEMENT_SPEED, 0.25d);
			stats.addProperty("flying_speed", 0.45d);
		}
		stats.addProperty(MobConfigManager.FIELD_EXPERIENCE_DROP, baby ? 1 : 3);
		group.add(MobConfigManager.FIELD_MOB_STATS, stats);

		JsonObject spawnRules = new JsonObject();
		spawnRules.addProperty(MobConfigManager.FIELD_SPAWN_WEIGHT, baby ? 20.0d : 80.0d);
		group.add(MobConfigManager.FIELD_SPAWN_RULES, spawnRules);

		if (baby) {
			JsonObject behavior = new JsonObject();
			JsonObject aggression = MobConfigManager.getOrCreateObject(behavior, "aggression");
			aggression.addProperty(MobConfigManager.FIELD_ENABLED, false);
			aggression.addProperty("retaliate_when_hurt", false);
			aggression.addProperty("target_players_when_angry", false);
			JsonObject stinging = MobConfigManager.getOrCreateObject(behavior, "stinging");
			stinging.addProperty(MobConfigManager.FIELD_ENABLED, false);
			stinging.addProperty("sting_on_target", false);
			group.add(MobConfigManager.FIELD_MOB_BEHAVIOR, behavior);

			JsonObject goals = new JsonObject();
			MobConfigManager.addMobGoal(goals, "bee_attack", false, 0, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "hurt_by_target", false, 1, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "become_angry_target", false, 2, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "reset_universal_anger_target", false, 3, 100.0d, 0);
			group.add(MobConfigManager.FIELD_MOB_GOALS, goals);
		}
		return group;
	}
}


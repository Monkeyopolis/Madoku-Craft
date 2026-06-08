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
		return MobConfigManager.buildMobStatsDefaults(
			10.0d,
			null,
			2.0d,
			0.30d,
			null,
			0.60d,
			null,
			0.5d,
			null,
			null,
			null,
			null,
			null,
			"minecraft-entities-bee.json"
		);
	}

	private static JsonObject buildSharedBeeBehaviorDefaults() {
		return MobConfigManager.buildMobBehaviorDefaults(behavior -> {
			JsonObject pollination = MobConfigManager.getOrCreateObject(behavior, "pollination");
			pollination.addProperty(MobConfigManager.FIELD_ENABLED, false);
			pollination.addProperty("flower-search-radius", 8);
			pollination.addProperty("min-pollination-ticks", 400);
			pollination.addProperty("max-pollination-ticks", 800);
			pollination.addProperty("max-crops-growable", 10);
			pollination.addProperty("arrival-threshold", 0.1d);
			pollination.addProperty("position-change-chance", 25);
			pollination.addProperty("speed-modifier", 0.5d);
			pollination.addProperty("hover-height-within-flower", 0.6d);
			pollination.addProperty("hover-pos-offset", 0.33333334d);

			JsonObject pollinateCrops = MobConfigManager.getOrCreateObject(behavior, MobConfigManager.FIELD_POLLINATE_CROPS);
			pollinateCrops.addProperty(MobConfigManager.FIELD_ENABLED, true);
			pollinateCrops.addProperty("nectar-total-charges", 10);
			pollinateCrops.addProperty("search-duration-ticks", 1200);
			pollinateCrops.addProperty("search-radius-horizontal", 12);
			pollinateCrops.addProperty("search-radius-vertical", 4);
			pollinateCrops.addProperty("crop-reach-distance-sqr", 1.0d);
			pollinateCrops.addProperty("crop-reservation-ttl-ticks", 50);
			pollinateCrops.addProperty("move-speed-modifier", 1.0d);
			pollinateCrops.addProperty("arrival-threshold", 0.1d);
			pollinateCrops.addProperty("position-change-chance", 25);
			pollinateCrops.addProperty("hover-height-within-crop", 0.6d);
			pollinateCrops.addProperty("hover-pos-offset", 0.33333334d);
			pollinateCrops.addProperty("charge-interval-ticks", 20);
			pollinateCrops.addProperty("charges-spend-divisor", 2);
			pollinateCrops.addProperty("growth-percent-per-charge", 2.0d);

			JsonObject hive = MobConfigManager.getOrCreateObject(behavior, "hive");
			hive.addProperty(MobConfigManager.FIELD_ENABLED, true);
			hive.addProperty("hive-search-distance", 20);
			hive.addProperty("pathfind-to-hive-when-closer-than", 16);
			hive.addProperty("hive-close-enough-distance", 2);
			hive.addProperty("too-far-distance", 48);
			hive.addProperty("ticks-without-nectar-before-going-home", 3600);
			hive.addProperty("locate-hive-cooldown-ticks", 200);
			hive.addProperty("locate-flower-cooldown-ticks", 200);
			hive.addProperty("locate-flower-cooldown-min-ticks", 20);
			hive.addProperty("locate-flower-cooldown-max-ticks", 60);
			hive.addProperty("ticks-before-going-to-known-flower", 600);
			hive.addProperty("return-if-environment-demands-hive", true);
			hive.addProperty("max-travelling-ticks", 2400);
			hive.addProperty("max-blacklisted-hive-targets", 3);
			hive.addProperty("ticks-before-hive-drop", 60);

			JsonObject aggressionRoot = MobConfigManager.getOrCreateObject(behavior, "aggression");
			aggressionRoot.addProperty(MobConfigManager.FIELD_ENABLED, true);
			aggressionRoot.addProperty(MobConfigManager.FIELD_RETALIATE_WHEN_HURT, true);
			aggressionRoot.addProperty("persistent-anger-time-min-seconds", 20);
			aggressionRoot.addProperty("persistent-anger-time-max-seconds", 39);
			aggressionRoot.addProperty("min-attack-distance", 4);
			aggressionRoot.addProperty("target-players-when-angry", true);

			JsonObject stinging = MobConfigManager.getOrCreateObject(behavior, "stinging");
			stinging.addProperty(MobConfigManager.FIELD_ENABLED, true);
			stinging.addProperty("sting-on-target", true);
			stinging.addProperty("self-destruct-after-sting", true);
			stinging.addProperty("sting-death-countdown-ticks", 1200);
			stinging.addProperty("poison-seconds-normal", 10);
			stinging.addProperty("poison-seconds-hard", 18);
			stinging.addProperty("poison-amplifier", 0);

			JsonObject breeding = MobConfigManager.getOrCreateObject(behavior, "breeding");
			breeding.addProperty(MobConfigManager.FIELD_ENABLED, true);
			breeding.addProperty("favorite-food", "minecraft:flower");
			breeding.addProperty("baby-spawn-count-min", 1);
			breeding.addProperty("baby-spawn-count-max", 1);
			breeding.addProperty("breed-cooldown-ticks", 6000);

			JsonObject wandering = MobConfigManager.getOrCreateObject(behavior, "wandering");
			wandering.addProperty(MobConfigManager.FIELD_ENABLED, true);
			wandering.addProperty("default-wander-distance-reduction", 16);
			wandering.addProperty("restricted-wander-distance-reduction", 24);
			wandering.addProperty("wander-trigger-chance", 10);
			wandering.addProperty("wander-horizontal-range", 8);
			wandering.addProperty("wander-vertical-range", 7);
		});
	}

	private static JsonObject buildSharedBeeGoalsDefaults() {
		return MobConfigManager.buildMobGoalsDefaults(goals -> {
			MobConfigManager.addMobGoal(goals, "breed", true, 2, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "follow-parent", true, 5, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "hurt-by-target", true, 1, 100.0d, 0);
			MobConfigManager.addMobGoal(goals, "become-angry-target", true, 2, 100.0d, 0);
		});
	}

	private static JsonObject buildBeeAgeOverrides(boolean baby) {
		JsonObject group = new JsonObject();
		group.add(
			MobConfigManager.FIELD_MOB_STATS,
			MobConfigManager.buildMobStatsDefaults(
				baby ? 5.0d : null,
				null,
				null,
				baby ? 0.25d : null,
				null,
				baby ? 0.45d : null,
				null,
				null,
				baby ? 1 : 3,
				null,
				null,
				null,
				null,
				null
			)
		);

		group.add(
			MobConfigManager.FIELD_SPAWN_RULES,
			MobConfigManager.mobSpawnRules().spawnWeight(baby ? 20.0d : 80.0d).build()
		);

		if (baby) {
			JsonObject behavior = new JsonObject();
			JsonObject aggression = MobConfigManager.getOrCreateObject(behavior, "aggression");
			aggression.addProperty(MobConfigManager.FIELD_ENABLED, false);
			aggression.addProperty(MobConfigManager.FIELD_RETALIATE_WHEN_HURT, false);
			aggression.addProperty("target-players-when-angry", false);
			JsonObject stinging = MobConfigManager.getOrCreateObject(behavior, "stinging");
			stinging.addProperty(MobConfigManager.FIELD_ENABLED, false);
			stinging.addProperty("sting-on-target", false);
			group.add(MobConfigManager.FIELD_MOB_BEHAVIOR, behavior);

			group.add(MobConfigManager.FIELD_MOB_GOALS, new JsonObject());
		}
		return group;
	}
}


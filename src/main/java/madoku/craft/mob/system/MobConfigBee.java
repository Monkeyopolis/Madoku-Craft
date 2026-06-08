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
		JsonObject stats = MobConfigManager.buildMobStatsDefaults(
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
		stats.add(
			MobConfigManager.FIELD_MOB_EFFECT,
			MobConfigManager.buildMobEffectDefaults("minecraft:poison", 60)
		);
		return stats;
	}

	private static JsonObject buildSharedBeeBehaviorDefaults() {
		return MobConfigManager.buildMobBehaviorDefaults(behavior -> {
			JsonObject pollinateCrops = MobConfigManager.getOrCreateObject(behavior, MobConfigManager.FIELD_POLLINATE_CROPS);
			pollinateCrops.addProperty(MobConfigManager.FIELD_ENABLED, true);
			pollinateCrops.addProperty("nectar-total-charges", 10);
			pollinateCrops.addProperty("search-duration-ticks", 1200);
			pollinateCrops.addProperty("search-radius-horizontal", 12);
			pollinateCrops.addProperty("search-radius-vertical", 4);
			pollinateCrops.addProperty("crop-reach-distance-sqr", 0.5d);
			pollinateCrops.addProperty("crop-reservation-ttl-ticks", 50);
			pollinateCrops.addProperty("move-speed-modifier", 1.0d);
			pollinateCrops.addProperty("arrival-threshold", 0.1d);
			pollinateCrops.addProperty("position-change-chance", 10);
			pollinateCrops.addProperty("hover-height-within-crop", 0.5d);
			pollinateCrops.addProperty("hover-pos-offset", 0.33333334d);
			pollinateCrops.addProperty("charge-interval-ticks", 20);
			pollinateCrops.addProperty("charges-spend-divisor", 2);
			pollinateCrops.addProperty("growth-percent-per-charge", 2.0d);
		});
	}

	private static JsonObject buildSharedBeeGoalsDefaults() {
		return MobConfigManager.buildMobGoalsDefaults(goals -> {
			MobConfigManager.addMobGoal(goals, "breed", true, 2, 100.0d, 0);
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
			group.add(MobConfigManager.FIELD_MOB_GOALS, new JsonObject());
		}
		return group;
	}
}

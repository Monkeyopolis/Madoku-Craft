package madoku.craft.mob.system;

import com.google.gson.JsonObject;

import madoku.craft.config.JsonFormatBuilder;

public final class MobConfigBee {
	private MobConfigBee() {
	}

	public static JsonObject buildDefaults() {
		JsonObject defaultGroup = JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_SPAWN_WEIGHT, 100.0d)
			.put(MobConfigManager.FIELD_MOB_STATS, buildSharedBeeStatsDefaults())
			.put(MobConfigManager.FIELD_MOB_BEHAVIOR, buildSharedBeeBehaviorDefaults())
			.put(MobConfigManager.FIELD_MOB_GOALS, buildSharedBeeGoalsDefaults())
			.put(MobConfigManager.FIELD_ADULT_GROUP, buildBeeAgeOverrides(false))
			.put(MobConfigManager.FIELD_BABY_GROUP, buildBeeAgeOverrides(true))
			.build();

		JsonObject bee = JsonFormatBuilder.object()
			.put(MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true)
			.put(MobConfigManager.FIELD_DIFFICULTY_SCALING, true)
			.object(MobConfigManager.FIELD_DIFFICULTY_SCALE, difficultyScale -> difficultyScale
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_HEALTH, 0.25d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_DAMAGE, 0.10d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED, 0.10d)
				.put(MobConfigManager.FIELD_DIFFICULTY_SCALE_FLYING_SPEED, 0.10d)
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
			.put(MobConfigManager.FIELD_MOB_VARIANT, false)
			.put(MobConfigManager.FIELD_MOB_BABY, true)
			.put(MobConfigManager.FILE_BEE, bee)
			.build();
	}

	private static JsonObject buildSharedBeeStatsDefaults() {
		return JsonFormatBuilder.object()
			.putAll(MobConfigManager.buildMobStatsDefaults(
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
			))
			.put(MobConfigManager.FIELD_MOB_EFFECT, MobConfigManager.buildMobEffectDefaults("minecraft:poison", 60))
			.build();
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
		JsonObject group = JsonFormatBuilder.object()
			.put(
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
			)
			.put(
				MobConfigManager.FIELD_SPAWN_RULES,
				MobConfigManager.mobSpawnRules().spawnWeight(baby ? 20.0d : 80.0d).build()
			)
			.build();

		if (baby) {
			group.add(MobConfigManager.FIELD_MOB_GOALS, new JsonObject());
		}
		return group;
	}
}

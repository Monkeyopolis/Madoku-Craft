package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigWitherSkeleton {
	private MobConfigWitherSkeleton() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_STATS, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_GOALS, true);
		root.addProperty(MobConfigManager.FIELD_MOB_VARIANT, false);

		JsonObject witherSkeleton = new JsonObject();
		JsonObject defaultGroup = new JsonObject();
		defaultGroup.add(
			MobConfigManager.FIELD_MOB_STATS,
			buildWitherSkeletonStatsDefaults()
		);
		defaultGroup.add(MobConfigManager.FIELD_SPAWN_RULES, MobConfigManager.buildMobSpawnRulesDefaults());
		MobConfigManager.ensureMobSchema(defaultGroup, false);
		witherSkeleton.add(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup);

		root.add(MobConfigManager.FILE_WITHER_SKELETON, witherSkeleton);
		return root;
	}

	private static JsonObject buildWitherSkeletonStatsDefaults() {
		JsonObject mobStats = MobConfigManager.buildMobStatsDefaults(20.0d, 0.0d, 7.0d, 0.25d, 0.0d, 1.0d, 11);
		mobStats.addProperty(MobConfigManager.FIELD_RANGED_DAMAGE, 6.0d);
		mobStats.addProperty(MobConfigManager.FIELD_ATTACK_INTERVAL, 20.0d);
		mobStats.addProperty(MobConfigManager.FIELD_ATTACK_ACCURACY, 0.7d);
		mobStats.addProperty(MobConfigManager.FIELD_CHARGE_INTERVAL, 10.0d);
		return mobStats;
	}
}

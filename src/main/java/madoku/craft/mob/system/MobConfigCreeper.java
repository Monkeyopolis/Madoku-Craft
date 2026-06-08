package madoku.craft.mob.system;

import com.google.gson.JsonObject;

public final class MobConfigCreeper {
	private MobConfigCreeper() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(MobConfigManager.FIELD_ENABLED, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_STATS, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);
		root.addProperty(MobConfigManager.FIELD_OVERRIDE_GOALS, true);
		root.addProperty(MobConfigManager.FIELD_MOB_VARIANT, true);

		JsonObject creeper = new JsonObject();
		JsonObject defaultGroup = buildCreeperDefaultGroup();
		JsonObject chargedVariant = buildChargedCreeperVariant();
		creeper.add(MobConfigManager.FIELD_DEFAULT_GROUP, defaultGroup);
		creeper.add(MobConfigManager.FIELD_CHARGED_CREEPER, chargedVariant);

		root.add(MobConfigManager.FILE_CREEPER, creeper);
		return root;
	}

	private static JsonObject buildCreeperDefaultGroup() {
		JsonObject group = new JsonObject();
		group.add(MobConfigManager.FIELD_MOB_STATS, buildCreeperStatsDefaults(3.0d, 0.25d, 0.10d, 0.4d, 30.0d, 7));
		group.add(MobConfigManager.FIELD_SPAWN_RULES, MobConfigManager.mobSpawnRules().spawnWeight(95.0d).build());
		MobConfigManager.ensureMobSchema(group, false);
		return group;
	}

	private static JsonObject buildChargedCreeperVariant() {
		JsonObject variant = new JsonObject();
		variant.addProperty(MobConfigManager.FIELD_SHARED_COMPONENTS, true);
		variant.add(
			MobConfigManager.FIELD_MOB_STATS,
			buildCreeperStatsDefaults(5.0d, 0.30d, 0.20d, 0.6d, 24.0d, 11)
		);
		variant.add(MobConfigManager.FIELD_SPAWN_RULES, MobConfigManager.mobSpawnRules().spawnWeight(5.0d).build());
		MobConfigManager.ensureMobSchema(variant, false);
		return variant;
	}

	private static JsonObject buildCreeperStatsDefaults(
		double explosionPower,
		double movementSpeed,
		double knockbackResistance,
		double explosionDestructionChance,
		double fuseLength,
		int experience
	) {
		JsonObject creeperStats = MobConfigManager.buildMobStatsDefaults(
			12.0d,
			1.0d,
			null,
			movementSpeed,
			null,
			null,
			knockbackResistance,
			1.0d,
			experience,
			null,
			null,
			null,
			null,
			null
		);
		creeperStats.addProperty(MobConfigManager.FIELD_GRIEF_POWER_MULTIPLIER, 0.5d);
		creeperStats.addProperty(MobConfigManager.FIELD_EXPLOSION_POWER, explosionPower);
		creeperStats.addProperty(MobConfigManager.FIELD_EXPLOSION_DESTRUCTION_CHANCE, explosionDestructionChance);
		creeperStats.addProperty(MobConfigManager.FIELD_FUSE_LENGTH, fuseLength);
		return creeperStats;
	}
}

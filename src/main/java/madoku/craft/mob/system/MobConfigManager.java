package madoku.craft.mob.system;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class MobConfigManager {
	public static final String FIELD_ENABLED = "enabled";

	public static final String FIELD_HEALTH = "health";
	public static final String FIELD_ARMOR = "armor";
	public static final String FIELD_DAMAGE = "damage";
	public static final String FIELD_MOVEMENT_SPEED = "movement-speed";
	public static final String FIELD_SWIMMING_SPEED = "swimming-speed";
	public static final String FIELD_FLYING_SPEED = "flying-speed";
	public static final String FIELD_KNOCKBACK_RESISTANCE = "knockback-resistance";
	public static final String FIELD_SCALE = "scale";
	public static final String FIELD_EXPERIENCE_DROP = "experience-drop";

	public static final String FILE_CREEPER = "creeper";
	public static final String FILE_SKELETON = "skeleton";
	public static final String FILE_STRAY = "stray";
	public static final String FILE_BOGGED = "bogged";
	public static final String FILE_PARCHED = "parched";
	public static final String FILE_SPIDER = "spider";
	public static final String FILE_CAVE_SPIDER = "cave-spider";
	public static final String FILE_ZOMBIE = "zombie";
	public static final String FILE_HUSK = "husk";
	public static final String FILE_DROWNED = "drowned";
	public static final String FILE_ZOMBIE_VILLAGER = "zombie-villager";
	public static final String FILE_WITHER_SKELETON = "wither-skeleton";
	public static final String FILE_HAG = "hag";
	public static final String FILE_BEE = "bee";

	public static final String FIELD_CAN_PICK_UP_LOOT = "can-pick-up-loot";
	public static final String FIELD_TRIDENT_ATTACK = "trident-attack";
	public static final String FIELD_SPAWN_WEIGHT = "spawn-weight";
	public static final String FIELD_RANGED_DAMAGE = "ranged-damage";
	public static final String FIELD_ATTACK_INTERVAL = "attack-interval";
	public static final String FIELD_ATTACK_ACCURACY = "attack-accuracy";
	public static final String FIELD_CHARGE_INTERVAL = "charge-interval";
	public static final String FIELD_SPAWN_RULES = "mob-spawn-rules";
	public static final String FIELD_MOB_STATS = "mob-stats";
	public static final String FIELD_MOB_WEAPON = "mob-weapon";
	public static final String FIELD_MOB_EFFECT = "mob-effect";
	public static final String FIELD_MOB_DROPS = "mob-drops";
	public static final String FIELD_ITEM = "item";
	public static final String FIELD_EFFECT = "effect";
	public static final String FIELD_DURATION = "duration";
	public static final String FIELD_WEAPON_DAMAGE = "weapon-damage";
	public static final String FIELD_MOB_BEHAVIOR = "mob-behavior";
	public static final String FIELD_MOB_GOALS = "mob-goals";
	public static final String FIELD_RETALIATE_WHEN_HURT = "retaliate-when-hurt";
	public static final String FIELD_CALLS_REINFORCEMENTS_WHEN_HURT = "calls-reinforcements-when-hurt";
	public static final String FIELD_POLLINATE_CROPS = "pollinate-crops";
	public static final String FIELD_CUSTOM_MOB_DROPS = "custom-mob-drops";
	public static final String FIELD_EQUIPMENT_SET = "equipment-set";
	public static final String FIELD_MOB_EQUIPMENT = "mob-equipment";
	public static final String FIELD_EQUIPMENT_CHANCE = "equipment-chance";
	public static final String FIELD_MOB_JOCKEY = "mob-jockey";
	public static final String FIELD_JOCKEY_PASSENGER = "passenger";
	public static final String FIELD_JOCKEY_MOUNT = "mount";
	public static final String FIELD_MAIN_HAND = "main-hand";
	public static final String FIELD_SPAWN_ALTERNATIVE_MOB = "spawn-alternative-mob";
	public static final String FIELD_MOB = "mob";
	public static final String FIELD_OVERRIDE_STATS = "override-stats";
	public static final String FIELD_OVERRIDE_SPAWN_RULES = "override-spawn-rules";
	public static final String FIELD_OVERRIDE_BEHAVIOR = "override-behavior";
	public static final String FIELD_OVERRIDE_GOALS = "override-goals";
	public static final String FIELD_MOB_VARIANT = "mob-variant";
	public static final String FIELD_SHARED_COMPONENTS = "shared-components";
	public static final String FIELD_MOB_BABY = "mob-baby";
	public static final String FIELD_DEFAULT_GROUP = "default";
	public static final String FIELD_BABY_GROUP = "baby";
	public static final String FIELD_ADULT_GROUP = "adult";
	public static final String FIELD_DIFFICULTY_SCALING = "difficulty-scaling";
	public static final String FIELD_DIFFICULTY_SCALE = "difficulty-scale";
	public static final String FIELD_REGIONAL_DIFFICULTY_SCALING = "regional-difficulty-scaling";
	public static final String FIELD_DIFFICULTY_SCALE_HEALTH = "health";
	public static final String FIELD_DIFFICULTY_SCALE_DAMAGE = "damage";
	public static final String FIELD_DIFFICULTY_SCALE_MOVEMENT_SPEED = "movement_speed";
	public static final String FIELD_DIFFICULTY_SCALE_FLYING_SPEED = "flying_speed";
	public static final String FIELD_DIFFICULTY_SCALE_EXPERIENCE_DROP = "experience_drop";
	public static final String FIELD_DIFFICULTY_SCALE_SCALE = "scale";
	public static final String FIELD_PARTIAL_SET = "partial-set";
	public static final String FIELD_HALF_SET = "half-set";
	public static final String FIELD_FULL_SET = "full-set";
	public static final String FIELD_HELMET = "helmet";
	public static final String FIELD_CHESTPLATE = "chestplate";
	public static final String FIELD_LEGGINGS = "leggings";
	public static final String FIELD_BOOTS = "boots";

	public static final String FIELD_GRIEF_POWER_MULTIPLIER = "grief-power-multiplier";
	public static final String FIELD_CREEPER = "creeper";
	public static final String FIELD_CHARGED_CREEPER = "charged_creeper";
	public static final String FIELD_EXPLOSION_POWER = "explosion-power";
	public static final String FIELD_EXPLOSION_DESTRUCTION_CHANCE = "explosion-destruction-chance";
	public static final String FIELD_FUSE_LENGTH = "fuse-length";

	public static final List<String> MOB_STATS_OPTIONAL_ENTRIES = List.of(
		FIELD_HEALTH,
		FIELD_ARMOR,
		FIELD_DAMAGE,
		FIELD_MOVEMENT_SPEED,
		FIELD_SWIMMING_SPEED,
		FIELD_FLYING_SPEED,
		FIELD_KNOCKBACK_RESISTANCE,
		FIELD_SCALE,
		FIELD_EXPERIENCE_DROP,
		FIELD_RANGED_DAMAGE,
		FIELD_ATTACK_INTERVAL,
		FIELD_ATTACK_ACCURACY,
		FIELD_CHARGE_INTERVAL,
		FIELD_MOB_DROPS,
		FIELD_MOB_WEAPON,
		FIELD_MOB_EFFECT,
		FIELD_GRIEF_POWER_MULTIPLIER,
		FIELD_EXPLOSION_POWER,
		FIELD_EXPLOSION_DESTRUCTION_CHANCE,
		FIELD_FUSE_LENGTH
	);

	public static final List<String> MOB_SPAWN_RULES_OPTIONAL_ENTRIES = List.of(
		FIELD_SPAWN_WEIGHT,
		FIELD_EQUIPMENT_SET,
		FIELD_MOB_JOCKEY,
		FIELD_SPAWN_ALTERNATIVE_MOB
	);

	public static final List<String> MOB_BEHAVIOR_OPTIONAL_ENTRIES = List.of(
		FIELD_CAN_PICK_UP_LOOT,
		FIELD_RETALIATE_WHEN_HURT,
		FIELD_CALLS_REINFORCEMENTS_WHEN_HURT,
		FIELD_TRIDENT_ATTACK,
		FIELD_POLLINATE_CROPS
	);

	public static final List<String> MOB_GOALS_OPTIONAL_ENTRIES = List.of(
		"breed",
		"follow-parent",
		"become-angry-target",
		"hurt-by-target",
		"trident-attack",
		"melee-attack",
		"ranged-attack",
		"target-player"
	);

	private static final Map<String, JsonObject> DEFAULT_FILE_DEFAULTS = buildDefaults();

	private MobConfigManager() {
	}

	public static JsonObject buildMobSystemDefaults() {
		JsonObject defaults = new JsonObject();
		defaults.addProperty(FIELD_ENABLED, true);
		return defaults;
	}

	public static Map<String, JsonObject> buildDefaultMobFileDefaults() {
		Map<String, JsonObject> copy = new LinkedHashMap<>();
		for (Map.Entry<String, JsonObject> entry : DEFAULT_FILE_DEFAULTS.entrySet()) {
			copy.put(entry.getKey(), entry.getValue().deepCopy());
		}
		return copy;
	}

	public static JsonObject buildDynamicMobDefaults(String fileKey) {
		if (fileKey == null) {
			return buildUniversalOnlyDefaults();
		}
		JsonObject known = DEFAULT_FILE_DEFAULTS.get(fileKey.trim().toLowerCase());
		if (known != null) {
			return known.deepCopy();
		}
		return buildUniversalOnlyDefaults();
	}

	private static Map<String, JsonObject> buildDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		defaults.put(FILE_CREEPER, MobConfigCreeper.buildDefaults());
		defaults.put(FILE_SKELETON, MobConfigSkeleton.buildDefaults());
		defaults.put(FILE_STRAY, MobConfigStray.buildDefaults());
		defaults.put(FILE_BOGGED, MobConfigBogged.buildDefaults());
		defaults.put(FILE_PARCHED, MobConfigParched.buildDefaults());
		defaults.put(FILE_SPIDER, MobConfigSpider.buildDefaults());
		defaults.put(FILE_CAVE_SPIDER, MobConfigCaveSpider.buildDefaults());
		defaults.put(FILE_ZOMBIE, MobConfigZombie.buildDefaults());
		defaults.put(FILE_HUSK, MobConfigHusk.buildDefaults());
		defaults.put(FILE_DROWNED, MobConfigDrowned.buildDefaults());
		defaults.put(FILE_ZOMBIE_VILLAGER, MobConfigZombieVillager.buildDefaults());
		defaults.put(FILE_WITHER_SKELETON, MobConfigWitherSkeleton.buildDefaults());
		defaults.put(FILE_HAG, MobConfigHag.buildDefaults());
		defaults.put(FILE_BEE, MobConfigBee.buildDefaults());
		return defaults;
	}

	private static JsonObject buildUniversalOnlyDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		root.add(FIELD_MOB_STATS, new JsonObject());
		root.add(FIELD_SPAWN_RULES, buildMobSpawnRulesDefaults());
		root.add(FIELD_MOB_BEHAVIOR, buildMobBehaviorDefaults());
		root.add(FIELD_MOB_GOALS, buildMobGoalsDefaults());
		return root;
	}

	static JsonObject buildMobStatsDefaults(
		Double health,
		Double armor,
		Double damage,
		Double movementSpeed,
		Double knockbackResistance,
		Double scale,
		Integer experienceDrop
	) {
		return buildMobStatsDefaults(
			health,
			armor,
			damage,
			movementSpeed,
			null,
			null,
			knockbackResistance,
			scale,
			experienceDrop,
			null,
			null,
			null,
			null,
			null
		);
	}

	static JsonObject buildMobStatsDefaults(
		Double health,
		Double armor,
		Double damage,
		Double movementSpeed,
		Double swimmingSpeed,
		Double flyingSpeed,
		Double knockbackResistance,
		Double scale,
		Integer experienceDrop,
		Double rangedDamage,
		Double attackAccuracy,
		Double attackInterval,
		Double chargeUpTicks,
		String mobDropsReference
	) {
		JsonObject root = new JsonObject();
		if (hasConfiguredDoubleValue(health)) {
			root.addProperty(FIELD_HEALTH, health);
		}
		if (hasConfiguredDoubleValue(armor)) {
			root.addProperty(FIELD_ARMOR, armor);
		}
		if (hasConfiguredDoubleValue(damage)) {
			root.addProperty(FIELD_DAMAGE, damage);
		}
		if (hasConfiguredDoubleValue(movementSpeed)) {
			root.addProperty(FIELD_MOVEMENT_SPEED, movementSpeed);
		}
		if (hasConfiguredDoubleValue(swimmingSpeed)) {
			root.addProperty(FIELD_SWIMMING_SPEED, swimmingSpeed);
		}
		if (hasConfiguredDoubleValue(flyingSpeed)) {
			root.addProperty(FIELD_FLYING_SPEED, flyingSpeed);
		}
		if (hasConfiguredDoubleValue(knockbackResistance)) {
			root.addProperty(FIELD_KNOCKBACK_RESISTANCE, knockbackResistance);
		}
		if (hasConfiguredDoubleValue(scale)) {
			root.addProperty(FIELD_SCALE, scale);
		}
		if (experienceDrop != null) {
			root.addProperty(FIELD_EXPERIENCE_DROP, experienceDrop);
		}
		if (hasConfiguredDoubleValue(rangedDamage)) {
			root.addProperty(FIELD_RANGED_DAMAGE, rangedDamage);
		}
		if (hasConfiguredDoubleValue(attackAccuracy)) {
			root.addProperty(FIELD_ATTACK_ACCURACY, attackAccuracy);
		}
		if (hasConfiguredDoubleValue(attackInterval)) {
			root.addProperty(FIELD_ATTACK_INTERVAL, attackInterval);
		}
		if (hasConfiguredDoubleValue(chargeUpTicks)) {
			root.addProperty(FIELD_CHARGE_INTERVAL, chargeUpTicks);
		}
		if (mobDropsReference != null && !mobDropsReference.isBlank()) {
			root.addProperty(FIELD_MOB_DROPS, mobDropsReference);
		}
		return root;
	}

	static JsonObject buildMobWeaponDefaults(String itemId) {
		JsonObject root = new JsonObject();
		if (itemId == null || itemId.isBlank()) {
			return root;
		}
		JsonObject weapon = new JsonObject();
		weapon.addProperty(FIELD_ITEM, itemId);
		root.add(FIELD_MOB_WEAPON, weapon);
		return root;
	}

	static JsonObject buildMobEffectDefaults(String effectId, int durationSeconds) {
		if (effectId == null || effectId.isBlank() || durationSeconds <= 0) {
			return new JsonObject();
		}
		JsonObject effect = new JsonObject();
		effect.addProperty(FIELD_EFFECT, effectId);
		effect.addProperty(FIELD_DURATION, durationSeconds);
		return effect;
	}

	private static boolean hasConfiguredDoubleValue(Double value) {
		return value != null && Double.isFinite(value);
	}

	static JsonObject buildSkeletonDefaults(
		String mobKey,
		double health,
		double armor,
		double damage,
		double movementSpeed,
		double knockbackResistance,
		double scale,
		int experience,
		double rangedDamage
	) {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		JsonObject mob = new JsonObject();
		mob.add(FIELD_SPAWN_RULES, buildMobSpawnRulesDefaults());
		JsonObject mobStats = buildMobStatsDefaults(
			health,
			armor,
			damage,
			movementSpeed,
			null,
			null,
			knockbackResistance,
			scale,
			experience,
			rangedDamage,
			0.7d,
			20.0d,
			10.0d,
			null
		);
		mob.add(FIELD_MOB_STATS, mobStats);
		ensureMobSchema(mob, false);
		root.add(mobKey, mob);
		return root;
	}

	static void ensureMobSchema(JsonObject mobRoot, boolean canPickUpLootDefault) {
		if (mobRoot == null) {
			return;
		}
		getOrCreateObject(mobRoot, FIELD_MOB_STATS);
		getOrCreateObject(mobRoot, FIELD_SPAWN_RULES);
		getOrCreateObject(mobRoot, FIELD_MOB_BEHAVIOR);
		getOrCreateObject(mobRoot, FIELD_MOB_GOALS);
	}

	static JsonObject buildMobSpawnRulesDefaults() {
		return mobSpawnRules().build();
	}

	static MobSpawnRulesBuilder mobSpawnRules() {
		return new MobSpawnRulesBuilder();
	}

	static final class MobSpawnRulesBuilder {
		private final JsonObject root = new JsonObject();

		MobSpawnRulesBuilder spawnWeight(Double value) {
			addOptionalDouble(root, FIELD_SPAWN_WEIGHT, value);
			return this;
		}

		MobSpawnRulesBuilder equipmentSet(JsonObject value) {
			addOptionalObject(root, FIELD_EQUIPMENT_SET, value);
			return this;
		}

		MobSpawnRulesBuilder mobJockey(JsonObject value) {
			addOptionalObject(root, FIELD_MOB_JOCKEY, value);
			return this;
		}

		MobSpawnRulesBuilder spawnAlternativeMob(JsonObject value) {
			addOptionalObject(root, FIELD_SPAWN_ALTERNATIVE_MOB, value);
			return this;
		}

		JsonObject build() {
			return root;
		}
	}

	private static void addOptionalDouble(JsonObject target, String key, Double value) {
		if (target == null || key == null || key.isBlank() || value == null) {
			return;
		}
		target.addProperty(key, value);
	}

	private static void addOptionalObject(JsonObject target, String key, JsonObject value) {
		if (target == null || key == null || key.isBlank() || value == null || value.entrySet().isEmpty()) {
			return;
		}
		target.add(key, value.deepCopy());
	}

	static JsonObject buildMobBehaviorDefaults(Consumer<JsonObject> configurator) {
		JsonObject root = new JsonObject();
		if (configurator != null) {
			configurator.accept(root);
		}
		return root;
	}

	static JsonObject buildMobBehaviorDefaults() {
		return buildMobBehaviorDefaults(root -> {});
	}

	static JsonObject buildMobGoalsDefaults() {
		return buildMobGoalsDefaults(root -> {});
	}

	static JsonObject buildMobGoalsDefaults(Consumer<JsonObject> configurator) {
		JsonObject root = new JsonObject();
		if (configurator != null) {
			configurator.accept(root);
		}
		return root;
	}

	public static List<String> getOptionalEntriesForContainer(String containerKey) {
		if (FIELD_MOB_STATS.equals(containerKey)) {
			return MOB_STATS_OPTIONAL_ENTRIES;
		}
		if (FIELD_SPAWN_RULES.equals(containerKey)) {
			return MOB_SPAWN_RULES_OPTIONAL_ENTRIES;
		}
		if (FIELD_MOB_BEHAVIOR.equals(containerKey)) {
			return MOB_BEHAVIOR_OPTIONAL_ENTRIES;
		}
		if (FIELD_MOB_GOALS.equals(containerKey)) {
			return MOB_GOALS_OPTIONAL_ENTRIES;
		}
		return List.of();
	}

	public static boolean isOptionalEntryForContainer(String containerKey, String entryKey) {
		if (entryKey == null || entryKey.isBlank()) {
			return false;
		}
		return getOptionalEntriesForContainer(containerKey).contains(entryKey);
	}

	static void addMobGoal(JsonObject goalsRoot, String goalKey, boolean enabled, int priority, double weight, int cooldownTicks) {
		if (goalsRoot == null || goalKey == null || goalKey.isBlank()) {
			return;
		}
		JsonObject goal = new JsonObject();
		goal.addProperty(FIELD_ENABLED, enabled);
		goal.addProperty("priority", priority);
		goal.addProperty("weight", weight);
		goal.addProperty("cooldown-ticks", cooldownTicks);
		goalsRoot.add(goalKey, goal);
	}

	static void mergeMissing(JsonObject target, JsonObject defaults) {
		if (target == null || defaults == null) {
			return;
		}
		for (Map.Entry<String, com.google.gson.JsonElement> entry : defaults.entrySet()) {
			String key = entry.getKey();
			com.google.gson.JsonElement value = entry.getValue();
			if (!target.has(key)) {
				target.add(key, value.deepCopy());
				continue;
			}
			if (target.get(key).isJsonObject() && value.isJsonObject()) {
				mergeMissing(target.getAsJsonObject(key), value.getAsJsonObject());
			}
		}
	}


	static JsonObject getOrCreateObject(JsonObject parent, String key) {
		if (parent == null || key == null || key.isBlank()) {
			return new JsonObject();
		}
		if (parent.has(key) && parent.get(key).isJsonObject()) {
			return parent.getAsJsonObject(key);
		}
		JsonObject created = new JsonObject();
		parent.add(key, created);
		return created;
	}
}

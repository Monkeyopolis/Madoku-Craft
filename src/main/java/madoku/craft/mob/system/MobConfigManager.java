package madoku.craft.mob.system;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MobConfigManager {
	public static final String FIELD_ENABLED = "enabled";

	public static final String FIELD_HEALTH = "health";
	public static final String FIELD_ARMOR = "armor";
	public static final String FIELD_DAMAGE = "damage";
	public static final String FIELD_MOVEMENT_SPEED = "movement_speed";
	public static final String FIELD_SWIMMING_SPEED = "swimming_speed";
	public static final String FIELD_KNOCKBACK_RESISTANCE = "knockback_resistance";
	public static final String FIELD_SCALE = "scale";
	public static final String FIELD_EXPERIENCE_DROP = "experience_drop";

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

	public static final String FIELD_CAN_BREAK_DOORS = "can_break_doors";
	public static final String FIELD_CAN_PICK_UP_LOOT = "can_pick_up_loot";
	public static final String FIELD_TRIDENT_ATTACK = "trident-attack";
	public static final String FIELD_TRIDENT_GROUND_CLEAR_TICKS = "trident_ground_clear_ticks";
	public static final String FIELD_SPAWN_WEIGHT = "spawn_weight";
	public static final String FIELD_ARMOR_SPAWN_WEIGHT = "armor_spawn_weight";
	public static final String FIELD_NO_ARMOR_SPAWN_WEIGHT = "no_armor_spawn_weight";
	public static final String FIELD_ARMOR_NETHERITE_WEIGHT = "armor_netherite_weight";
	public static final String FIELD_ARMOR_DIAMOND_WEIGHT = "armor_diamond_weight";
	public static final String FIELD_ARMOR_GOLD_WEIGHT = "armor_gold_weight";
	public static final String FIELD_ARMOR_IRON_WEIGHT = "armor_iron_weight";
	public static final String FIELD_ARMOR_COPPER_WEIGHT = "armor_copper_weight";
	public static final String FIELD_ARMOR_LEATHER_WEIGHT = "armor_leather_weight";
	public static final String FIELD_ARMOR_HELMET_ONLY_WEIGHT = "armor_helmet_only_weight";
	public static final String FIELD_ARMOR_HELMET_BOOTS_WEIGHT = "armor_helmet_boots_weight";
	public static final String FIELD_ARMOR_FULL_SET_WEIGHT = "armor_full_set_weight";

	public static final String FIELD_SPIDER_SPAWN_WEIGHT = "spider_spawn_weight";
	public static final String FIELD_CAVE_SPIDER_SPAWN_WEIGHT = "cave_spider_spawn_weight";
	public static final String FIELD_SPIDER_JOCKEY_SPAWN_WEIGHT = "spider_jockey_spawn_weight";

	public static final String FIELD_WITH_BOW_SPAWN_WEIGHT = "with_bow_spawn_weight";
	public static final String FIELD_WITHOUT_BOW_SPAWN_WEIGHT = "without_bow_spawn_weight";
	public static final String FIELD_WITHER_SWORD_SPAWN_WEIGHT = "wither_sword_spawn_weight";
	public static final String FIELD_WITHER_BOW_SPAWN_WEIGHT = "wither_bow_spawn_weight";
	public static final String FIELD_REGULAR_SPAWN_WEIGHT = "regular_spawn_weight";
	public static final String FIELD_RANGED_DAMAGE = "ranged_damage";
	public static final String FIELD_ATTACK_INTERVAL = "attack-interval";
	public static final String FIELD_ATTACK_ACCURACY = "attack-accuracy";
	public static final String FIELD_CHARGE_UP_TICKS = "charge-upticks";
	public static final String FIELD_ATTACK_INTERVAL_LEGACY = "attack_interval";
	public static final String FIELD_ATTACK_ACCURACY_LEGACY = "attack_accuracy";
	public static final String FIELD_CHARGE_UP_TICKS_LEGACY = "charge_up_ticks";
	public static final String FIELD_SPAWN_RULES = "mob-spawn-rules";
	public static final String FIELD_ARMOR_SET = "armor-set";
	public static final String FIELD_ARMOR_RARITY = "armor-rarity";
	public static final String FIELD_MOB_STATS = "mob-stats";
	public static final String FIELD_MOB_DROPS = "mob-drops";
	public static final String FIELD_WEAPON_DAMAGE = "weapon-damage";
	public static final String FIELD_MOB_BEHAVIOR = "mob-behavior";
	public static final String FIELD_MOB_GOALS = "mob-goals";
	public static final String FIELD_APPLIES_HUNGER_ON_HIT = "applies_hunger_on_hit";
	public static final String FIELD_CUSTOM_MOB_DROPS = "custom-mob-drops";
	public static final String FIELD_EQUIPMENT_SET = "equipment-set";
	public static final String FIELD_MOB_EQUIPMENT = "mob-equipment";
	public static final String FIELD_EQUIPMENT_CHANCE = "equipment-chance";
	public static final String FIELD_MOB_JOCKEY = "mob-jockey";
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

	public static final String FIELD_GRIEF_POWER_MULTIPLIER = "grief_power_multiplier";
	public static final String FIELD_EXPLOSION_DESTRUCTION_DIFFICULTY_STEP = "explosion_destruction_difficulty_step";
	public static final String FIELD_CREEPER = "creeper";
	public static final String FIELD_CHARGED_CREEPER = "charged_creeper";
	public static final String FIELD_EXPLOSION_POWER = "explosion_power";
	public static final String FIELD_EXPLOSION_DESTRUCTION_CHANCE = "explosion_destruction_chance";
	public static final String FIELD_FUSE_LENGTH = "fuse_length";

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
		root.add(FIELD_MOB_STATS, buildMobStatsDefaults(null, null, null, null, null, null, null));
		root.add(FIELD_SPAWN_RULES, buildMobSpawnRulesDefaults());
		root.add(FIELD_MOB_BEHAVIOR, buildMobBehaviorDefaults(false, false));
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
		JsonObject root = new JsonObject();
		root.add(FIELD_HEALTH, null);
		root.add(FIELD_ARMOR, null);
		root.add(FIELD_DAMAGE, null);
		root.add(FIELD_MOVEMENT_SPEED, null);
		root.add(FIELD_KNOCKBACK_RESISTANCE, null);
		root.add(FIELD_SCALE, null);
		root.add(FIELD_EXPERIENCE_DROP, null);
		root.add(FIELD_RANGED_DAMAGE, null);
		root.add(FIELD_ATTACK_INTERVAL, null);
		root.add(FIELD_ATTACK_ACCURACY, null);
		root.add(FIELD_CHARGE_UP_TICKS, null);
		root.add(FIELD_MOB_DROPS, null);
		if (isNonZero(health)) {
			root.addProperty(FIELD_HEALTH, health);
		}
		if (isNonZero(armor)) {
			root.addProperty(FIELD_ARMOR, armor);
		}
		if (isNonZero(damage)) {
			root.addProperty(FIELD_DAMAGE, damage);
		}
		if (isNonZero(movementSpeed)) {
			root.addProperty(FIELD_MOVEMENT_SPEED, movementSpeed);
		}
		if (isNonZero(knockbackResistance)) {
			root.addProperty(FIELD_KNOCKBACK_RESISTANCE, knockbackResistance);
		}
		if (isNonZero(scale)) {
			root.addProperty(FIELD_SCALE, scale);
		}
		if (experienceDrop != null) {
			root.addProperty(FIELD_EXPERIENCE_DROP, experienceDrop);
		}
		return root;
	}

	private static boolean isNonZero(Double value) {
		return value != null && Double.isFinite(value) && Double.compare(value, 0.0D) != 0;
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
		JsonObject spawnRules = getOrCreateObject(mob, FIELD_SPAWN_RULES);
		spawnRules.addProperty(FIELD_WITH_BOW_SPAWN_WEIGHT, 95.0d);
		spawnRules.addProperty(FIELD_WITHOUT_BOW_SPAWN_WEIGHT, 5.0d);
		spawnRules.addProperty(FIELD_SPIDER_JOCKEY_SPAWN_WEIGHT, 5.0d);
		spawnRules.addProperty(FIELD_REGULAR_SPAWN_WEIGHT, 95.0d);
		addArmorSpawnDefaults(mob);
		JsonObject mobStats = buildMobStatsDefaults(health, armor, damage, movementSpeed, knockbackResistance, scale, experience);
		mobStats.addProperty(FIELD_RANGED_DAMAGE, rangedDamage);
		mobStats.addProperty(FIELD_ATTACK_INTERVAL, 20.0d);
		mobStats.addProperty(FIELD_ATTACK_ACCURACY, 0.7d);
		mobStats.addProperty(FIELD_CHARGE_UP_TICKS, 10.0d);
		mob.add(FIELD_MOB_STATS, mobStats);
		ensureMobSchema(mob, false, false);
		root.add(mobKey, mob);
		return root;
	}

	static void ensureMobSchema(JsonObject mobRoot, boolean canBreakDoorsDefault, boolean canPickUpLootDefault) {
		if (mobRoot == null) {
			return;
		}
		JsonObject stats = getOrCreateObject(mobRoot, FIELD_MOB_STATS);
		mergeMissing(stats, buildMobStatsDefaults(null, null, null, null, null, null, null));

		JsonObject spawnRules = getOrCreateObject(mobRoot, FIELD_SPAWN_RULES);
		mergeMissing(spawnRules, buildMobSpawnRulesDefaults());

		JsonObject behavior = getOrCreateObject(mobRoot, FIELD_MOB_BEHAVIOR);
		mergeMissing(behavior, buildMobBehaviorDefaults(canBreakDoorsDefault, canPickUpLootDefault));

		JsonObject goals = getOrCreateObject(mobRoot, FIELD_MOB_GOALS);
		mergeMissing(goals, buildMobGoalsDefaults());
	}

	static JsonObject buildMobSpawnRulesDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_SPAWN_WEIGHT, 100.0d);
		root.addProperty(FIELD_WITH_BOW_SPAWN_WEIGHT, 95.0d);
		root.addProperty(FIELD_WITHOUT_BOW_SPAWN_WEIGHT, 5.0d);
		root.addProperty(FIELD_WITHER_SWORD_SPAWN_WEIGHT, 90.0d);
		root.addProperty(FIELD_WITHER_BOW_SPAWN_WEIGHT, 10.0d);
		root.addProperty(FIELD_REGULAR_SPAWN_WEIGHT, 95.0d);
		root.addProperty(FIELD_SPIDER_SPAWN_WEIGHT, 90.0d);
		root.addProperty(FIELD_CAVE_SPIDER_SPAWN_WEIGHT, 5.0d);
		root.addProperty(FIELD_SPIDER_JOCKEY_SPAWN_WEIGHT, 5.0d);
		root.addProperty(FIELD_EXPLOSION_DESTRUCTION_DIFFICULTY_STEP, 0.2d);
		root.addProperty(FIELD_ARMOR_SPAWN_WEIGHT, 10.0d);
		root.addProperty(FIELD_NO_ARMOR_SPAWN_WEIGHT, 90.0d);

		JsonObject armorRarity = new JsonObject();
		armorRarity.addProperty(FIELD_ARMOR_NETHERITE_WEIGHT, 1.0d);
		armorRarity.addProperty(FIELD_ARMOR_DIAMOND_WEIGHT, 5.0d);
		armorRarity.addProperty(FIELD_ARMOR_GOLD_WEIGHT, 10.0d);
		armorRarity.addProperty(FIELD_ARMOR_IRON_WEIGHT, 17.0d);
		armorRarity.addProperty(FIELD_ARMOR_COPPER_WEIGHT, 28.0d);
		armorRarity.addProperty(FIELD_ARMOR_LEATHER_WEIGHT, 39.0d);
		root.add(FIELD_ARMOR_RARITY, armorRarity);

		JsonObject armorSet = new JsonObject();
		armorSet.addProperty(FIELD_ARMOR_HELMET_ONLY_WEIGHT, 60.0d);
		armorSet.addProperty(FIELD_ARMOR_HELMET_BOOTS_WEIGHT, 30.0d);
		armorSet.addProperty(FIELD_ARMOR_FULL_SET_WEIGHT, 10.0d);
		root.add(FIELD_ARMOR_SET, armorSet);
		return root;
	}

	static JsonObject buildMobBehaviorDefaults(boolean canBreakDoorsDefault, boolean canPickUpLootDefault) {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_CAN_BREAK_DOORS, canBreakDoorsDefault);
		root.addProperty(FIELD_CAN_PICK_UP_LOOT, canPickUpLootDefault);
		root.addProperty("retaliate_when_hurt", true);
		root.addProperty("target_players", true);
		root.addProperty("ignore_creative_players", true);
		root.addProperty("ignore_spectator_players", true);
		root.addProperty("despawn_when_far_away", true);
		root.addProperty("despawn_distance", 128.0d);
		root.addProperty("idle_sound_interval_ticks", 160);
		root.addProperty("ambient_sound_enabled", true);
		return root;
	}

	static JsonObject buildMobGoalsDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty("enabled", true);
		addGenericGoal(root, "wander", true, 6, 100.0d, 0);
		addGenericGoal(root, "look_around", true, 7, 100.0d, 0);
		addGenericGoal(root, "target_player", true, 2, 100.0d, 0);
		addGenericGoal(root, "melee_attack", true, 3, 100.0d, 20);
		addGenericGoal(root, "ranged_attack", true, 3, 100.0d, 20);
		addGenericGoal(root, "flee_sun", false, 4, 100.0d, 0);
		addGenericGoal(root, "float", true, 0, 100.0d, 0);
		return root;
	}

	static void addMobGoal(JsonObject goalsRoot, String goalKey, boolean enabled, int priority, double weight, int cooldownTicks) {
		if (goalsRoot == null || goalKey == null || goalKey.isBlank()) {
			return;
		}
		JsonObject goal = new JsonObject();
		goal.addProperty(FIELD_ENABLED, enabled);
		goal.addProperty("priority", priority);
		goal.addProperty("weight", weight);
		goal.addProperty("cooldown_ticks", cooldownTicks);
		goalsRoot.add(goalKey, goal);
	}

	private static void addGenericGoal(JsonObject goalsRoot, String goalKey, boolean enabled, int priority, double weight, int cooldownTicks) {
		addMobGoal(goalsRoot, goalKey, enabled, priority, weight, cooldownTicks);
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

	static void applyIronArmorDefaults(JsonObject root) {
		if (root == null) {
			return;
		}
		JsonObject spawnRules = getOrCreateObject(root, FIELD_SPAWN_RULES);
		JsonObject armorRarity = getOrCreateObject(spawnRules, FIELD_ARMOR_RARITY);
		armorRarity.addProperty(FIELD_ARMOR_NETHERITE_WEIGHT, 0.0d);
		armorRarity.addProperty(FIELD_ARMOR_DIAMOND_WEIGHT, 0.0d);
		armorRarity.addProperty(FIELD_ARMOR_GOLD_WEIGHT, 0.0d);
		armorRarity.addProperty(FIELD_ARMOR_IRON_WEIGHT, 100.0d);
		armorRarity.addProperty(FIELD_ARMOR_COPPER_WEIGHT, 0.0d);
		armorRarity.addProperty(FIELD_ARMOR_LEATHER_WEIGHT, 0.0d);
	}

	static void addArmorSpawnDefaults(JsonObject root) {
		if (root == null) {
			return;
		}
		JsonObject spawnRules = getOrCreateObject(root, FIELD_SPAWN_RULES);
		spawnRules.addProperty(FIELD_ARMOR_SPAWN_WEIGHT, 10.0d);
		spawnRules.addProperty(FIELD_NO_ARMOR_SPAWN_WEIGHT, 90.0d);
		JsonObject armorRarity = getOrCreateObject(spawnRules, FIELD_ARMOR_RARITY);
		armorRarity.addProperty(FIELD_ARMOR_NETHERITE_WEIGHT, 1.0d);
		armorRarity.addProperty(FIELD_ARMOR_DIAMOND_WEIGHT, 5.0d);
		armorRarity.addProperty(FIELD_ARMOR_GOLD_WEIGHT, 10.0d);
		armorRarity.addProperty(FIELD_ARMOR_IRON_WEIGHT, 17.0d);
		armorRarity.addProperty(FIELD_ARMOR_COPPER_WEIGHT, 28.0d);
		armorRarity.addProperty(FIELD_ARMOR_LEATHER_WEIGHT, 39.0d);
		JsonObject armorSet = getOrCreateObject(spawnRules, FIELD_ARMOR_SET);
		armorSet.addProperty(FIELD_ARMOR_HELMET_ONLY_WEIGHT, 60.0d);
		armorSet.addProperty(FIELD_ARMOR_HELMET_BOOTS_WEIGHT, 30.0d);
		armorSet.addProperty(FIELD_ARMOR_FULL_SET_WEIGHT, 10.0d);
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




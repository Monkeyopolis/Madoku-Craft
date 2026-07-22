package madoku.craft.mob.system;

import com.google.gson.JsonObject;

import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.JSONTypeManager;
import madoku.craft.api.json.MadokuJSONManager;
import madoku.craft.loot.system.EquipmentConfigManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class MobConfigManager {
	public static final String CONFIG_ROOT = "madoku-craft";
	public static final String MOBS_SYSTEM_FOLDER = "madoku-craft-mobs";
	public static final String ENTITIES_SYSTEM_FOLDER = "madoku-entities";
	public static final String WORLD_DIFFICULTY_SYSTEM_FOLDER = "madoku-world-difficulty";
	public static final String REGIONAL_DIFFICULTY_SYSTEM_FOLDER = "madoku-regional-difficulty";
	public static final String MOBS_SETTINGS_FILE = "madoku-mobs";
	public static final String ENTITIES_SETTINGS_FILE = "madoku-entities";
	public static final String WORLD_DIFFICULTY_SETTINGS_FILE = "madoku-world-difficulty";
	public static final String REGIONAL_DIFFICULTY_SETTINGS_FILE = "madoku-regional-difficulty";
	public static final String ENTITY_FILES_FOLDER = "mobs";
	public static final String REGIONAL_SCALING_FOLDER = "scaling";
	public static final String REGIONAL_STRUCTURES_FILE = "structures";
	public static final String REGIONAL_TIME_FILE = "time";
	public static final String REGIONAL_BIOMES_FILE = "biomes";

	public static final String FIELD_OVERRIDE_COMPONENTS = "override-components";
	public static final String FIELD_OVERRIDE_BEHAVIORS = "override-behaviors";
	public static final String FIELD_ENTITY = "entity";
	public static final String FIELD_MOB_ID = "mob-id";
	public static final String FIELD_WORLD_DIFFICULTY_SCALING = "world-difficulty-scaling";
	public static final String FIELD_REGIONAL_DIFFICULTY_SCALING_NEW = "regional-difficulty-scaling";
	public static final String FIELD_BIOME_LIST = "biome-list";
	public static final String FIELD_STRUCTURE_LIST = "structure-list";
	public static final String FIELD_DAY_LIST = "day-list";
	public static final String FIELD_DAY_COUNT = "day-count";
	public static final String FIELD_ADJUSTMENT = "adjustment";
	public static final String FIELD_TYPE = "type";
	public static final String FIELD_VALUE = "value";
	public static final String TYPE_PERCENTAGE = "percentage";
	public static final String TYPE_FLAT = "flat";
	private static volatile boolean initialized;
	private static volatile RuntimeConfig runtimeConfig = RuntimeConfig.disabled();

	public static final String FIELD_ENABLED = "enabled";

	public static final String FIELD_HEALTH = "health";
	public static final String FIELD_ARMOR = "armor";
	public static final String FIELD_DAMAGE = "damage";
	public static final String FIELD_TRUE_DAMAGE = "true-damage";
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
	public static final String FIELD_BOW_ATTACK = "bow-attack";
	public static final String FIELD_SPAWN_WEIGHT = "spawn-weight";
	public static final String FIELD_RANGED_DAMAGE = "ranged-damage";
	public static final String FIELD_EXPLOSION_POWER = "explosion-power";
	public static final String FIELD_ATTACK_INTERVAL = "attack-interval";
	public static final String FIELD_ATTACK_ACCURACY = "attack-accuracy";
	public static final String FIELD_CHARGE_INTERVAL = "charge-interval";
	public static final String FIELD_SPAWN_RULES = "mob-spawn-rules";
	public static final String FIELD_MOB_COMPONENTS = "mob-components";
	public static final String FIELD_MOB_WEAPON = "mob-weapon";
	public static final String FIELD_MOB_EFFECT = "mob-effect";
	public static final String FIELD_MOB_DROPS = "mob-drops";
	public static final String FIELD_ITEM = "item";
	public static final String FIELD_EFFECT = "effect";
	public static final String FIELD_DURATION = "duration";
	public static final String FIELD_WEAPON_DAMAGE = "weapon-damage";
	public static final String FIELD_MOB_BEHAVIORS = "mob-behaviors";
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
	public static final String FIELD_OVERRIDE_SPAWN_RULES = "override-spawn-rules";
	public static final String FIELD_OVERRIDE_GOALS = "override-goals";
	public static final String FIELD_MOB_VARIANT = "mob-variant";
	public static final String FIELD_MOB_BABY = "mob-baby";
	public static final String FIELD_DEFAULT_GROUP = "default";
	public static final String FIELD_BABY_GROUP = "baby";
	public static final String FIELD_ADULT_GROUP = "adult";
	public static final String FIELD_PARTIAL_SET = "partial-set";
	public static final String FIELD_HALF_SET = "half-set";
	public static final String FIELD_FULL_SET = "full-set";
	public static final String FIELD_HELMET = "helmet";
	public static final String FIELD_CHESTPLATE = "chestplate";
	public static final String FIELD_LEGGINGS = "leggings";
	public static final String FIELD_BOOTS = "boots";

	public static final String FIELD_CREEPER = "creeper";
	public static final String FIELD_CHARGED_CREEPER = "charged-creeper";
	public static final String FIELD_MOB_EXPLODE = "mob-explode";
	public static final String FIELD_DESTRUCTION_CHANCE = "destruction-chance";
	public static final String FIELD_GREIF_POWER = "greif-power";
	public static final String FIELD_FUSE_LENGTH = "fuse-length";

	public static final List<String> MOB_STATS_OPTIONAL_ENTRIES = List.of(
		FIELD_HEALTH,
		FIELD_ARMOR,
		FIELD_DAMAGE,
		FIELD_TRUE_DAMAGE,
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
		FIELD_EXPLOSION_POWER,
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
		FIELD_BOW_ATTACK,
		FIELD_POLLINATE_CROPS,
		FIELD_MOB_EXPLODE
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

	private static final Map<String, JsonObject> DEFAULT_FILE_DEFAULTS = buildNewEntityDefaults();

	private MobConfigManager() {
	}

	public static synchronized void initialize() {
		if (initialized) {
			return;
		}
		try {
			Path root = MadokuJSONManager.getOrCreateGlobalRootDirectory();
			Path mobsDirectory = ensureDirectory(root.resolve(MOBS_SYSTEM_FOLDER));
			JSONFormatManager.ensureManagedFile(
				mobsDirectory.resolve(MOBS_SETTINGS_FILE + ".json"),
				buildMobSystemDefaults(),
				JSONTypeManager.STATIC_CONFIG,
				null
			);
			JSONFormatManager.ensureManagedFile(
				ensureDirectory(root.resolve(ENTITIES_SYSTEM_FOLDER)).resolve(ENTITIES_SETTINGS_FILE + ".json"),
				buildEntitySystemDefaults(),
				JSONTypeManager.STATIC_CONFIG,
				null
			);
			JSONFormatManager.ensureManagedFile(
				ensureDirectory(root.resolve(WORLD_DIFFICULTY_SYSTEM_FOLDER)).resolve(WORLD_DIFFICULTY_SETTINGS_FILE + ".json"),
				buildWorldDifficultyDefaults(),
				JSONTypeManager.STATIC_CONFIG,
				null
			);
			Path regionalDirectory = ensureDirectory(root.resolve(REGIONAL_DIFFICULTY_SYSTEM_FOLDER));
			JSONFormatManager.ensureManagedFile(
				regionalDirectory.resolve(REGIONAL_DIFFICULTY_SETTINGS_FILE + ".json"),
				buildRegionalDifficultyDefaults(),
				JSONTypeManager.STATIC_CONFIG,
				null
			);
			JSONFormatManager.ensureManagedFile(
				regionalDirectory.resolve(REGIONAL_STRUCTURES_FILE + ".json"),
				buildStructuresDefaults(),
				JSONTypeManager.DYNAMIC_CONFIG,
				null
			);
			JSONFormatManager.ensureManagedFile(
				regionalDirectory.resolve(REGIONAL_TIME_FILE + ".json"),
				buildTimeDefaults(),
				JSONTypeManager.DYNAMIC_CONFIG,
				null
			);
			JSONFormatManager.ensureManagedFile(
				regionalDirectory.resolve(REGIONAL_BIOMES_FILE + ".json"),
				buildBiomesDefaults(),
				JSONTypeManager.DYNAMIC_CONFIG,
				null
			);
			JSONFormatManager.ensureManagedFolder(
				ensureDirectory(mobsDirectory.resolve(ENTITY_FILES_FOLDER)),
				buildNewEntityDefaults(),
				MobConfigManager::buildNewDynamicEntityDefaults,
				(fileKey, ignored) -> true,
				(key, value) -> value
			);
			JSONFormatManager.ensureManagedFolder(
				ensureDirectory(regionalDirectory.resolve(REGIONAL_SCALING_FOLDER)),
				Map.of(),
				MobConfigManager::buildNewDynamicScalingDefaults,
				(fileKey, ignored) -> true,
				(key, value) -> value
			);
			loadRuntimeConfig();
			initialized = true;
		} catch (IOException | RuntimeException exception) {
			runtimeConfig = RuntimeConfig.disabled();
			initialized = false;
		}
	}

	public static synchronized void reset() {
		initialized = false;
		runtimeConfig = RuntimeConfig.disabled();
	}

	public static boolean isEnabled() {
		return runtimeConfig.enabled();
	}

	static Map<String, JsonObject> getRuntimeMobFiles() {
		return runtimeConfig.files();
	}

	private static void loadRuntimeConfig() throws IOException {
		Path rootDirectory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(MOBS_SYSTEM_FOLDER);
		Path settingsFile = rootDirectory.resolve(MOBS_SETTINGS_FILE + ".json");
		JsonObject settingsRoot = JSONFormatManager.ensureManagedFile(settingsFile, buildMobSystemDefaults());
		boolean enabled = readBoolean(settingsRoot, FIELD_ENABLED, true);
		Path mobsDirectory = rootDirectory.resolve(ENTITY_FILES_FOLDER);
		Map<String, JsonObject> files = new LinkedHashMap<>();
		if (Files.isDirectory(mobsDirectory)) {
			try (var stream = Files.list(mobsDirectory)) {
				for (Path file : stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
					String fileKey = file.getFileName().toString().substring(0, file.getFileName().toString().length() - 5).toLowerCase();
					files.put(fileKey, JSONFormatManager.readManagedDocument(file).data());
				}
			}
		}
		runtimeConfig = enabled ? new RuntimeConfig(true, Map.copyOf(files)) : RuntimeConfig.disabled();
		EquipmentConfigManager.reloadConfig();
	}

	private static Path ensureDirectory(Path path) throws IOException {
		Files.createDirectories(path);
		return path;
	}

	public static JsonObject buildEntitySystemDefaults() {
		return JSONFormatManager.object().put(FIELD_ENABLED, true).build();
	}

	public static JsonObject buildWorldDifficultyDefaults() {
		return JSONFormatManager.object()
			.put(FIELD_ENABLED, true)
			.object(FIELD_WORLD_DIFFICULTY_SCALING, root -> root
				.put(FIELD_HEALTH, buildScalingRule(TYPE_PERCENTAGE, 0.25D))
				.put(FIELD_DAMAGE, buildScalingRule(TYPE_PERCENTAGE, 0.10D))
				.put(FIELD_MOVEMENT_SPEED, buildScalingRule(TYPE_PERCENTAGE, 0.10D))
				.put(FIELD_EXPERIENCE_DROP, buildScalingRule(TYPE_PERCENTAGE, 0.25D))
				.put(FIELD_FLYING_SPEED, buildScalingRule(TYPE_PERCENTAGE, 0.10D))
				.put(FIELD_EXPLOSION_POWER, buildScalingRule(TYPE_PERCENTAGE, 0.10D))
				.put(FIELD_RANGED_DAMAGE, buildScalingRule(TYPE_PERCENTAGE, 0.10D))
				.put(FIELD_SWIMMING_SPEED, buildScalingRule(TYPE_PERCENTAGE, 0.10D)))
			.build();
	}

	public static JsonObject buildRegionalDifficultyDefaults() {
		return JSONFormatManager.object()
			.put(FIELD_ENABLED, true)
			.object(FIELD_REGIONAL_DIFFICULTY_SCALING_NEW, root -> root
				.put(FIELD_HEALTH, buildScalingRule(TYPE_PERCENTAGE, 0.20D))
				.put(FIELD_MOVEMENT_SPEED, buildScalingRule(TYPE_PERCENTAGE, 0.02D))
				.put(FIELD_ARMOR, buildScalingRule(TYPE_PERCENTAGE, 0.02D))
				.put(FIELD_KNOCKBACK_RESISTANCE, buildScalingRule(TYPE_FLAT, 0.02D))
				.put(FIELD_EXPERIENCE_DROP, buildScalingRule(TYPE_PERCENTAGE, 0.10D)))
			.build();
	}

	private static JsonObject buildScalingRule(String type, double value) {
		return JSONFormatManager.object().put(FIELD_TYPE, type).put(FIELD_VALUE, value).build();
	}

	private static Map<String, JsonObject> buildNewEntityDefaults() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		for (String key : List.of(FILE_BEE, FILE_BOGGED, FILE_CAVE_SPIDER, FILE_CREEPER, FILE_DROWNED,
			FILE_HAG, FILE_HUSK, FILE_PARCHED, FILE_SKELETON, FILE_SPIDER, FILE_STRAY,
			FILE_WITHER_SKELETON, FILE_ZOMBIE, FILE_ZOMBIE_VILLAGER)) {
			defaults.put(key, buildNewDynamicEntityDefaults(key));
		}
		return defaults;
	}

	public static JsonObject buildNewDynamicEntityDefaults(String fileKey) {
		String key = normalizeFileKey(fileKey);
		String mobId = key.contains(":")
			? key
			: "hag".equals(key) ? "madoku-craft:hag" : "minecraft:" + key.replace('-', '_');
		return JSONFormatManager.object()
			.put(FIELD_ENABLED, true)
			.put(FIELD_OVERRIDE_SPAWN_RULES, true)
			.put(FIELD_OVERRIDE_COMPONENTS, true)
			.put(FIELD_OVERRIDE_BEHAVIORS, true)
			.put(FIELD_OVERRIDE_GOALS, true)
			.put(FIELD_MOB_ID, mobId)
			.object(FIELD_ENTITY, entity -> entity
				.put(FIELD_CUSTOM_MOB_DROPS, true)
				.put(FIELD_WORLD_DIFFICULTY_SCALING, true)
				.put(FIELD_REGIONAL_DIFFICULTY_SCALING_NEW, true)
				.object(key, variant -> variant
					.object(FIELD_SPAWN_RULES, ignored -> { })
					.object(FIELD_MOB_COMPONENTS, ignored -> { })
					.object(FIELD_MOB_BEHAVIORS, ignored -> { })
					.object(FIELD_MOB_GOALS, ignored -> { })))
			.build();
	}

	private static JsonObject buildNewDynamicScalingDefaults(String fileKey) {
		return RegionalDifficultyConfigManager.buildDynamicMobScalingDefaults(fileKey);
	}

	public static JsonObject buildStructuresDefaults() {
		return RegionalDifficultyStructuresManager.buildDefaults();
	}

	public static JsonObject buildBiomesDefaults() {
		return RegionalDifficultyBiomesManager.buildDefaults();
	}

	public static JsonObject buildTimeDefaults() {
		return RegionalDifficultyTimeManager.buildDefaults();
	}

	private static String normalizeFileKey(String fileKey) {
		return fileKey == null ? "" : fileKey.trim().toLowerCase();
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		try {
			return root != null && root.has(key) ? root.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	public static JsonObject buildMobSystemDefaults() {
		return JSONFormatManager.object()
			.put(FIELD_ENABLED, true)
			.build();
	}

	public static Map<String, JsonObject> buildDefaultMobFileDefaults() {
		Map<String, JsonObject> copy = new LinkedHashMap<>();
		for (Map.Entry<String, JsonObject> entry : DEFAULT_FILE_DEFAULTS.entrySet()) {
			copy.put(entry.getKey(), entry.getValue().deepCopy());
		}
		return copy;
	}

	public static JsonObject buildDynamicMobDefaults(String fileKey) {
		return buildNewDynamicEntityDefaults(fileKey);
	}

	private static JsonObject buildUniversalOnlyDefaults() {
		return JSONFormatManager.object()
			.put(FIELD_ENABLED, true)
			.object(FIELD_MOB_COMPONENTS, builder -> {
			})
			.put(FIELD_SPAWN_RULES, buildMobSpawnRulesDefaults())
			.put(FIELD_MOB_BEHAVIORS, buildMobBehaviorDefaults())
			.put(FIELD_MOB_GOALS, buildMobGoalsDefaults())
			.build();
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
		JSONFormatManager.ObjectBuilder root = JSONFormatManager.object();
		if (hasConfiguredDoubleValue(health)) {
			root.put(FIELD_HEALTH, health);
		}
		if (hasConfiguredDoubleValue(armor)) {
			root.put(FIELD_ARMOR, armor);
		}
		if (hasConfiguredDoubleValue(damage)) {
			root.put(FIELD_DAMAGE, damage);
		}
		if (hasConfiguredDoubleValue(movementSpeed)) {
			root.put(FIELD_MOVEMENT_SPEED, movementSpeed);
		}
		if (hasConfiguredDoubleValue(swimmingSpeed)) {
			root.put(FIELD_SWIMMING_SPEED, swimmingSpeed);
		}
		if (hasConfiguredDoubleValue(flyingSpeed)) {
			root.put(FIELD_FLYING_SPEED, flyingSpeed);
		}
		if (hasConfiguredDoubleValue(knockbackResistance)) {
			root.put(FIELD_KNOCKBACK_RESISTANCE, knockbackResistance);
		}
		if (hasConfiguredDoubleValue(scale)) {
			root.put(FIELD_SCALE, scale);
		}
		if (experienceDrop != null) {
			root.put(FIELD_EXPERIENCE_DROP, experienceDrop);
		}
		if (hasConfiguredDoubleValue(rangedDamage)) {
			root.put(FIELD_RANGED_DAMAGE, rangedDamage);
		}
		if (hasConfiguredDoubleValue(attackAccuracy)) {
			root.put(FIELD_ATTACK_ACCURACY, attackAccuracy);
		}
		if (hasConfiguredDoubleValue(attackInterval)) {
			root.put(FIELD_ATTACK_INTERVAL, attackInterval);
		}
		if (hasConfiguredDoubleValue(chargeUpTicks)) {
			root.put(FIELD_CHARGE_INTERVAL, chargeUpTicks);
		}
		if (mobDropsReference != null && !mobDropsReference.isBlank()) {
			root.put(FIELD_MOB_DROPS, mobDropsReference);
		}
		return root.build();
	}

	static JsonObject buildMobWeaponDefaults(String itemId) {
		JSONFormatManager.ObjectBuilder weapon = JSONFormatManager.object();
		if (itemId != null && !itemId.isBlank()) {
			weapon.put(FIELD_ITEM, itemId);
		}
		return weapon.build();
	}

	static JsonObject buildMobEffectDefaults(String effectId, int durationSeconds) {
		if (effectId == null || effectId.isBlank() || durationSeconds <= 0) {
			return new JsonObject();
		}
		return JSONFormatManager.object()
			.put(FIELD_EFFECT, effectId)
			.put(FIELD_DURATION, durationSeconds)
			.build();
	}

	private static boolean hasConfiguredDoubleValue(Double value) {
		return value != null && Double.isFinite(value);
	}

	static void addDifficultyScaleEntry(JsonObject root, String field, Double value) {
		if (root == null || field == null || field.isBlank() || value == null || !Double.isFinite(value)) {
			return;
		}
		root.addProperty(field, roundDifficultyScaleValue(value));
	}

	static double roundDifficultyScaleValue(double value) {
		if (!Double.isFinite(value)) {
			return value;
		}
		double step = isWholeNumber(value) ? 0.05d : 0.005d;
		return Math.round(value / step) * step;
	}

	private static boolean isWholeNumber(double value) {
		return Math.abs(value - Math.rint(value)) <= 1.0E-9D;
	}

	static void ensureMobSchema(JsonObject mobRoot, boolean canPickUpLootDefault) {
		if (mobRoot == null) {
			return;
		}
		getOrCreateObject(mobRoot, FIELD_MOB_COMPONENTS);
		getOrCreateObject(mobRoot, FIELD_SPAWN_RULES);
		getOrCreateObject(mobRoot, FIELD_MOB_BEHAVIORS);
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

	static JsonObject buildMobExplodeDefaults(Double destructionChance, Double griefPower) {
		JsonObject mobExplode = new JsonObject();
		mobExplode.addProperty(FIELD_ENABLED, true);
		if (hasConfiguredDoubleValue(destructionChance)) {
			mobExplode.addProperty(FIELD_DESTRUCTION_CHANCE, destructionChance);
		}
		if (hasConfiguredDoubleValue(griefPower)) {
			mobExplode.addProperty(FIELD_GREIF_POWER, griefPower);
		}
		return mobExplode;
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
		if (FIELD_MOB_COMPONENTS.equals(containerKey)) {
			return MOB_STATS_OPTIONAL_ENTRIES;
		}
		if (FIELD_SPAWN_RULES.equals(containerKey)) {
			return MOB_SPAWN_RULES_OPTIONAL_ENTRIES;
		}
		if (FIELD_MOB_BEHAVIORS.equals(containerKey)) {
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

	private record RuntimeConfig(boolean enabled, Map<String, JsonObject> files) {
		private static RuntimeConfig disabled() {
			return new RuntimeConfig(false, Map.of());
		}
	}
}


package madoku.craft.ecosystem.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MadokuEcosystemConfig {
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_NATURAL_GROWTH_ENABLED = "natural-growth-enabled";
	public static final String FIELD_NATURAL_EROSION_ENABLED = "natural-erosion-enabled";

	public static final String FIELD_NATURAL_DIRT_GROWTH = "natural-dirt-growth";
	public static final String FIELD_NATURAL_GRASS_GROWTH = "natural-grass-growth";
	public static final String FIELD_NATURAL_TREE_GROWTH = "natural-tree-growth";
	public static final String FIELD_MIN_GROWTH_TIME = "min-growth-time";
	public static final String FIELD_MAX_GROWTH_TIME = "max-growth-time";

	public static final String FIELD_SEASON_SPRING = "spring";
	public static final String FIELD_SEASON_SUMMER = "summer";
	public static final String FIELD_SEASON_FALL = "fall";
	public static final String FIELD_SEASON_WINTER = "winter";

	public static final String FIELD_TREE_OAK = "oak";
	public static final String FIELD_TREE_SPRUCE = "spruce";
	public static final String FIELD_TREE_BIRCH = "birch";
	public static final String FIELD_TREE_JUNGLE = "jungle";
	public static final String FIELD_TREE_MANGROVE = "mangrove";
	public static final String FIELD_TREE_ACACIA = "acacia";
	public static final String FIELD_TREE_DARK_OAK = "dark_oak";
	public static final String FIELD_TREE_PALE_OAK = "pale_oak";
	public static final String FIELD_TREE_CHERRY = "cherry";

	public static final String FIELD_WATER_EROSION_RADIUS = "water-erosion-radius";
	public static final String FIELD_BLOCK_EROSION_MUD = "block-erosion-mud";
	public static final String FIELD_BLOCK_EROSION_RED_SAND = "block-erosion-red-sand";
	public static final String FIELD_BLOCK_EROSION_SAND = "block-erosion-sand";

	private static final String FIELD_SOURCE_BLOCKS = "source-blocks";
	private static final String FIELD_TARGET_BLOCK = "target-block";
	private static final String FIELD_REQUIRED_BIOME_IDS = "required-biome-ids";
	private static final String FIELD_REQUIRED_BIOME_TAGS = "required-biome-tags";
	private static final String FIELD_GROWTH_DAYS = "growth-days";

	private MadokuEcosystemConfig() {
	}

	public static Settings defaults() {
		return new Settings(
			new SystemSettings(true, true),
			new NaturalGrowthSettings(
				new DayRange(1, 3),
				new DayRange(3, 5),
				new DayRange(3, 5),
				new DayRange(7, 9),
				new DayRange(1, 3),
				new DayRange(3, 5),
				new DayRange(3, 5),
				new DayRange(7, 9),
				new TreeGrowthProfile(new DayRange(3, 9), new DayRange(7, 13), new DayRange(11, 17), new DayRange(11, 17)),
				new TreeGrowthProfile(new DayRange(11, 17), new DayRange(11, 17), new DayRange(7, 13), new DayRange(3, 9)),
				new TreeGrowthProfile(new DayRange(7, 13), new DayRange(7, 13), new DayRange(7, 13), new DayRange(7, 13)),
				new TreeGrowthProfile(new DayRange(11, 17), new DayRange(3, 9), new DayRange(11, 17), new DayRange(11, 17)),
				new TreeGrowthProfile(new DayRange(11, 17), new DayRange(3, 9), new DayRange(11, 17), new DayRange(11, 17)),
				new TreeGrowthProfile(new DayRange(7, 13), new DayRange(11, 17), new DayRange(7, 13), new DayRange(11, 17)),
				new TreeGrowthProfile(new DayRange(7, 13), new DayRange(7, 13), new DayRange(11, 17), new DayRange(11, 17)),
				new TreeGrowthProfile(new DayRange(11, 17), new DayRange(11, 17), new DayRange(7, 13), new DayRange(7, 13)),
				new TreeGrowthProfile(new DayRange(3, 9), new DayRange(11, 17), new DayRange(3, 9), new DayRange(11, 17))
			),
			new NaturalErosionSettings(
				2,
				new ErosionRule(
					true,
					List.of("minecraft:grass_block", "minecraft:dirt", "minecraft:rooted_dirt", "minecraft:dirt_path", "minecraft:podzol", "minecraft:mycelium", "minecraft:coarse_dirt"),
					"minecraft:mud",
					List.of("minecraft:swamp", "minecraft:mangrove_swamp"),
					List.of("minecraft:is_jungle"),
					new DayRange(7, 11)
				),
				new ErosionRule(
					true,
					List.of("minecraft:grass_block", "minecraft:dirt", "minecraft:rooted_dirt", "minecraft:dirt_path", "minecraft:podzol", "minecraft:mycelium", "minecraft:coarse_dirt"),
					"minecraft:red_sand",
					List.of(),
					List.of("minecraft:is_badlands"),
					new DayRange(7, 11)
				),
				new ErosionRule(
					true,
					List.of("minecraft:grass_block", "minecraft:dirt", "minecraft:rooted_dirt", "minecraft:dirt_path", "minecraft:podzol", "minecraft:mycelium", "minecraft:coarse_dirt"),
					"minecraft:sand",
					List.of(),
					List.of(),
					new DayRange(7, 11)
				)
			)
		);
	}

	public static JsonObject buildSystemDefaultsJson() {
		return toSystemJson(defaults().system());
	}

	public static JsonObject buildNaturalGrowthDefaultsJson() {
		return toNaturalGrowthJson(defaults().naturalGrowth());
	}

	public static JsonObject buildNaturalErosionDefaultsJson() {
		return toNaturalErosionJson(defaults().naturalErosion());
	}

	public static SystemSettings systemFromJson(JsonObject source) {
		SystemSettings fallback = defaults().system();
		if (source == null) {
			return fallback;
		}
		return new SystemSettings(
			readBoolean(source, FIELD_NATURAL_GROWTH_ENABLED, fallback.naturalGrowthEnabled()),
			readBoolean(source, FIELD_NATURAL_EROSION_ENABLED, fallback.naturalErosionEnabled())
		);
	}

	public static NaturalGrowthSettings naturalGrowthFromJson(JsonObject source) {
		NaturalGrowthSettings fallback = defaults().naturalGrowth();
		if (source == null) {
			return fallback;
		}

		JsonObject dirtRoot = readObject(source, FIELD_NATURAL_DIRT_GROWTH);
		JsonObject grassRoot = readObject(source, FIELD_NATURAL_GRASS_GROWTH);
		JsonObject treeRoot = readObject(source, FIELD_NATURAL_TREE_GROWTH);
		return new NaturalGrowthSettings(
			readRange(dirtRoot, FIELD_SEASON_SPRING, fallback.dirtSpringGrowthDays()),
			readRange(dirtRoot, FIELD_SEASON_SUMMER, fallback.dirtSummerGrowthDays()),
			readRange(dirtRoot, FIELD_SEASON_FALL, fallback.dirtFallGrowthDays()),
			readRange(dirtRoot, FIELD_SEASON_WINTER, fallback.dirtWinterGrowthDays()),
			readRange(grassRoot, FIELD_SEASON_SPRING, fallback.grassSpringGrowthDays()),
			readRange(grassRoot, FIELD_SEASON_SUMMER, fallback.grassSummerGrowthDays()),
			readRange(grassRoot, FIELD_SEASON_FALL, fallback.grassFallGrowthDays()),
			readRange(grassRoot, FIELD_SEASON_WINTER, fallback.grassWinterGrowthDays()),
			readTreeProfile(treeRoot, FIELD_TREE_OAK, fallback.treeOakGrowth()),
			readTreeProfile(treeRoot, FIELD_TREE_SPRUCE, fallback.treeSpruceGrowth()),
			readTreeProfile(treeRoot, FIELD_TREE_BIRCH, fallback.treeBirchGrowth()),
			readTreeProfile(treeRoot, FIELD_TREE_JUNGLE, fallback.treeJungleGrowth()),
			readTreeProfile(treeRoot, FIELD_TREE_MANGROVE, fallback.treeMangroveGrowth()),
			readTreeProfile(treeRoot, FIELD_TREE_ACACIA, fallback.treeAcaciaGrowth()),
			readTreeProfile(treeRoot, FIELD_TREE_DARK_OAK, fallback.treeDarkOakGrowth()),
			readTreeProfile(treeRoot, FIELD_TREE_PALE_OAK, fallback.treePaleOakGrowth()),
			readTreeProfile(treeRoot, FIELD_TREE_CHERRY, fallback.treeCherryGrowth())
		);
	}

	public static NaturalErosionSettings naturalErosionFromJson(JsonObject source) {
		NaturalErosionSettings fallback = defaults().naturalErosion();
		if (source == null) {
			return fallback;
		}
		return new NaturalErosionSettings(
			Math.max(0, readInt(source, FIELD_WATER_EROSION_RADIUS, fallback.waterErosionRadius())),
			readRule(source, FIELD_BLOCK_EROSION_MUD, fallback.blockErosionMud()),
			readRule(source, FIELD_BLOCK_EROSION_RED_SAND, fallback.blockErosionRedSand()),
			readRule(source, FIELD_BLOCK_EROSION_SAND, fallback.blockErosionSand())
		);
	}

	public static JsonObject toSystemJson(SystemSettings settings) {
		SystemSettings value = settings == null ? defaults().system() : settings;
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_NATURAL_GROWTH_ENABLED, value.naturalGrowthEnabled());
		root.addProperty(FIELD_NATURAL_EROSION_ENABLED, value.naturalErosionEnabled());
		return root;
	}

	public static JsonObject toNaturalGrowthJson(NaturalGrowthSettings settings) {
		NaturalGrowthSettings value = settings == null ? defaults().naturalGrowth() : settings;
		JsonObject root = new JsonObject();

		JsonObject dirtRoot = new JsonObject();
		dirtRoot.add(FIELD_SEASON_SPRING, toRangeJson(value.dirtSpringGrowthDays()));
		dirtRoot.add(FIELD_SEASON_SUMMER, toRangeJson(value.dirtSummerGrowthDays()));
		dirtRoot.add(FIELD_SEASON_FALL, toRangeJson(value.dirtFallGrowthDays()));
		dirtRoot.add(FIELD_SEASON_WINTER, toRangeJson(value.dirtWinterGrowthDays()));
		root.add(FIELD_NATURAL_DIRT_GROWTH, dirtRoot);

		JsonObject grassRoot = new JsonObject();
		grassRoot.add(FIELD_SEASON_SPRING, toRangeJson(value.grassSpringGrowthDays()));
		grassRoot.add(FIELD_SEASON_SUMMER, toRangeJson(value.grassSummerGrowthDays()));
		grassRoot.add(FIELD_SEASON_FALL, toRangeJson(value.grassFallGrowthDays()));
		grassRoot.add(FIELD_SEASON_WINTER, toRangeJson(value.grassWinterGrowthDays()));
		root.add(FIELD_NATURAL_GRASS_GROWTH, grassRoot);

		JsonObject treeRoot = new JsonObject();
		treeRoot.add(FIELD_TREE_OAK, toTreeProfileJson(value.treeOakGrowth()));
		treeRoot.add(FIELD_TREE_SPRUCE, toTreeProfileJson(value.treeSpruceGrowth()));
		treeRoot.add(FIELD_TREE_BIRCH, toTreeProfileJson(value.treeBirchGrowth()));
		treeRoot.add(FIELD_TREE_JUNGLE, toTreeProfileJson(value.treeJungleGrowth()));
		treeRoot.add(FIELD_TREE_MANGROVE, toTreeProfileJson(value.treeMangroveGrowth()));
		treeRoot.add(FIELD_TREE_ACACIA, toTreeProfileJson(value.treeAcaciaGrowth()));
		treeRoot.add(FIELD_TREE_DARK_OAK, toTreeProfileJson(value.treeDarkOakGrowth()));
		treeRoot.add(FIELD_TREE_PALE_OAK, toTreeProfileJson(value.treePaleOakGrowth()));
		treeRoot.add(FIELD_TREE_CHERRY, toTreeProfileJson(value.treeCherryGrowth()));
		root.add(FIELD_NATURAL_TREE_GROWTH, treeRoot);
		return root;
	}

	public static JsonObject toNaturalErosionJson(NaturalErosionSettings settings) {
		NaturalErosionSettings value = settings == null ? defaults().naturalErosion() : settings;
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_WATER_EROSION_RADIUS, value.waterErosionRadius());
		root.add(FIELD_BLOCK_EROSION_MUD, toRuleJson(value.blockErosionMud()));
		root.add(FIELD_BLOCK_EROSION_RED_SAND, toRuleJson(value.blockErosionRedSand()));
		root.add(FIELD_BLOCK_EROSION_SAND, toRuleJson(value.blockErosionSand()));
		return root;
	}

	public static List<NamedErosionRule> erosionRulesInPriority(NaturalErosionSettings settings) {
		NaturalErosionSettings value = settings == null ? defaults().naturalErosion() : settings;
		List<NamedErosionRule> rules = new ArrayList<>();
		rules.add(new NamedErosionRule(FIELD_BLOCK_EROSION_MUD, value.blockErosionMud()));
		rules.add(new NamedErosionRule(FIELD_BLOCK_EROSION_RED_SAND, value.blockErosionRedSand()));
		rules.add(new NamedErosionRule(FIELD_BLOCK_EROSION_SAND, value.blockErosionSand()));
		return rules;
	}

	private static TreeGrowthProfile readTreeProfile(JsonObject root, String treeKey, TreeGrowthProfile fallback) {
		JsonObject treeRoot = readObject(root, treeKey);
		return new TreeGrowthProfile(
			readRange(treeRoot, FIELD_SEASON_SPRING, fallback.spring()),
			readRange(treeRoot, FIELD_SEASON_SUMMER, fallback.summer()),
			readRange(treeRoot, FIELD_SEASON_FALL, fallback.fall()),
			readRange(treeRoot, FIELD_SEASON_WINTER, fallback.winter())
		);
	}

	private static JsonObject toTreeProfileJson(TreeGrowthProfile profile) {
		TreeGrowthProfile value = profile == null ? defaults().naturalGrowth().treeOakGrowth() : profile;
		JsonObject root = new JsonObject();
		root.add(FIELD_SEASON_SPRING, toRangeJson(value.spring()));
		root.add(FIELD_SEASON_SUMMER, toRangeJson(value.summer()));
		root.add(FIELD_SEASON_FALL, toRangeJson(value.fall()));
		root.add(FIELD_SEASON_WINTER, toRangeJson(value.winter()));
		return root;
	}

	private static ErosionRule readRule(JsonObject root, String key, ErosionRule fallback) {
		JsonObject source = readObject(root, key);
		return new ErosionRule(
			readBoolean(source, FIELD_ENABLED, fallback.enabled()),
			readStringArray(source, FIELD_SOURCE_BLOCKS, fallback.sourceBlocks()),
			readString(source, FIELD_TARGET_BLOCK, fallback.targetBlock()),
			readStringArray(source, FIELD_REQUIRED_BIOME_IDS, fallback.requiredBiomeIds()),
			readStringArray(source, FIELD_REQUIRED_BIOME_TAGS, fallback.requiredBiomeTags()),
			readRange(source, FIELD_GROWTH_DAYS, fallback.growthDays())
		);
	}

	private static JsonObject toRuleJson(ErosionRule rule) {
		ErosionRule value = rule == null ? defaults().naturalErosion().blockErosionSand() : rule;
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, value.enabled());
		root.add(FIELD_SOURCE_BLOCKS, toStringArray(value.sourceBlocks()));
		root.addProperty(FIELD_TARGET_BLOCK, value.targetBlock());
		root.add(FIELD_REQUIRED_BIOME_IDS, toStringArray(value.requiredBiomeIds()));
		root.add(FIELD_REQUIRED_BIOME_TAGS, toStringArray(value.requiredBiomeTags()));
		root.add(FIELD_GROWTH_DAYS, toRangeJson(value.growthDays()));
		return root;
	}

	private static DayRange readRange(JsonObject object, String key, DayRange fallback) {
		JsonObject rangeRoot = readObject(object, key);
		int min = readInt(rangeRoot, FIELD_MIN_GROWTH_TIME, fallback.minDays());
		int max = readInt(rangeRoot, FIELD_MAX_GROWTH_TIME, fallback.maxDays());
		return new DayRange(min, max);
	}

	private static JsonObject toRangeJson(DayRange range) {
		DayRange safe = range == null ? new DayRange(1, 1) : range;
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_MIN_GROWTH_TIME, safe.minDays());
		root.addProperty(FIELD_MAX_GROWTH_TIME, safe.maxDays());
		return root;
	}

	private static List<String> readStringArray(JsonObject object, String key, List<String> fallback) {
		JsonElement element = object == null ? null : object.get(key);
		if (!(element instanceof JsonArray array)) {
			return normalizeList(fallback);
		}
		List<String> values = new ArrayList<>();
		for (JsonElement entry : array) {
			if (entry == null || !entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
				continue;
			}
			values.add(entry.getAsString());
		}
		List<String> normalized = normalizeList(values);
		return normalized.isEmpty() ? normalizeList(fallback) : normalized;
	}

	private static JsonArray toStringArray(List<String> values) {
		JsonArray array = new JsonArray();
		for (String value : normalizeList(values)) {
			array.add(value);
		}
		return array;
	}

	private static List<String> normalizeList(List<String> source) {
		Set<String> values = new LinkedHashSet<>();
		if (source != null) {
			for (String value : source) {
				String normalized = normalize(value);
				if (!normalized.isBlank()) {
					values.add(normalized);
				}
			}
		}
		return List.copyOf(values);
	}

	private static String readString(JsonObject object, String key, String fallback) {
		if (object == null || key == null || key.isBlank()) {
			return normalize(fallback);
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return normalize(fallback);
		}
		return normalize(element.getAsString());
	}

	private static String normalize(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().toLowerCase();
	}

	private static JsonObject readObject(JsonObject object, String key) {
		if (object == null || key == null || key.isBlank()) {
			return new JsonObject();
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonObject()) {
			return new JsonObject();
		}
		return element.getAsJsonObject();
	}

	private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		try {
			return element.getAsBoolean();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static int readInt(JsonObject object, String key, int fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsInt();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	public record DayRange(int minDays, int maxDays) {
		public DayRange {
			int safeMin = Math.max(1, minDays);
			int safeMax = Math.max(1, maxDays);
			if (safeMax < safeMin) {
				safeMax = safeMin;
			}
			minDays = safeMin;
			maxDays = safeMax;
		}
	}

	public record TreeGrowthProfile(DayRange spring, DayRange summer, DayRange fall, DayRange winter) {
		public TreeGrowthProfile {
			spring = spring == null ? new DayRange(7, 13) : spring;
			summer = summer == null ? new DayRange(7, 13) : summer;
			fall = fall == null ? new DayRange(7, 13) : fall;
			winter = winter == null ? new DayRange(7, 13) : winter;
		}

		public DayRange season(String seasonId) {
			if (FIELD_SEASON_SPRING.equals(seasonId)) {
				return spring;
			}
			if (FIELD_SEASON_SUMMER.equals(seasonId)) {
				return summer;
			}
			if (FIELD_SEASON_FALL.equals(seasonId)) {
				return fall;
			}
			if (FIELD_SEASON_WINTER.equals(seasonId)) {
				return winter;
			}
			return null;
		}
	}

	public record ErosionRule(
		boolean enabled,
		List<String> sourceBlocks,
		String targetBlock,
		List<String> requiredBiomeIds,
		List<String> requiredBiomeTags,
		DayRange growthDays
	) {
		public ErosionRule {
			sourceBlocks = normalizeList(sourceBlocks);
			targetBlock = normalize(targetBlock);
			requiredBiomeIds = normalizeList(requiredBiomeIds);
			requiredBiomeTags = normalizeList(requiredBiomeTags);
			growthDays = growthDays == null ? new DayRange(7, 11) : growthDays;
		}
	}

	public record NamedErosionRule(String ruleId, ErosionRule rule) {
	}

	public record SystemSettings(boolean naturalGrowthEnabled, boolean naturalErosionEnabled) {
	}

	public record NaturalGrowthSettings(
		DayRange dirtSpringGrowthDays,
		DayRange dirtSummerGrowthDays,
		DayRange dirtFallGrowthDays,
		DayRange dirtWinterGrowthDays,
		DayRange grassSpringGrowthDays,
		DayRange grassSummerGrowthDays,
		DayRange grassFallGrowthDays,
		DayRange grassWinterGrowthDays,
		TreeGrowthProfile treeOakGrowth,
		TreeGrowthProfile treeSpruceGrowth,
		TreeGrowthProfile treeBirchGrowth,
		TreeGrowthProfile treeJungleGrowth,
		TreeGrowthProfile treeMangroveGrowth,
		TreeGrowthProfile treeAcaciaGrowth,
		TreeGrowthProfile treeDarkOakGrowth,
		TreeGrowthProfile treePaleOakGrowth,
		TreeGrowthProfile treeCherryGrowth
	) {
		public NaturalGrowthSettings {
			dirtSpringGrowthDays = safeRange(dirtSpringGrowthDays, 1, 3);
			dirtSummerGrowthDays = safeRange(dirtSummerGrowthDays, 3, 5);
			dirtFallGrowthDays = safeRange(dirtFallGrowthDays, 3, 5);
			dirtWinterGrowthDays = safeRange(dirtWinterGrowthDays, 7, 9);
			grassSpringGrowthDays = safeRange(grassSpringGrowthDays, 1, 3);
			grassSummerGrowthDays = safeRange(grassSummerGrowthDays, 3, 5);
			grassFallGrowthDays = safeRange(grassFallGrowthDays, 3, 5);
			grassWinterGrowthDays = safeRange(grassWinterGrowthDays, 7, 9);
			treeOakGrowth = treeOakGrowth == null ? defaults().naturalGrowth().treeOakGrowth() : treeOakGrowth;
			treeSpruceGrowth = treeSpruceGrowth == null ? defaults().naturalGrowth().treeSpruceGrowth() : treeSpruceGrowth;
			treeBirchGrowth = treeBirchGrowth == null ? defaults().naturalGrowth().treeBirchGrowth() : treeBirchGrowth;
			treeJungleGrowth = treeJungleGrowth == null ? defaults().naturalGrowth().treeJungleGrowth() : treeJungleGrowth;
			treeMangroveGrowth = treeMangroveGrowth == null ? defaults().naturalGrowth().treeMangroveGrowth() : treeMangroveGrowth;
			treeAcaciaGrowth = treeAcaciaGrowth == null ? defaults().naturalGrowth().treeAcaciaGrowth() : treeAcaciaGrowth;
			treeDarkOakGrowth = treeDarkOakGrowth == null ? defaults().naturalGrowth().treeDarkOakGrowth() : treeDarkOakGrowth;
			treePaleOakGrowth = treePaleOakGrowth == null ? defaults().naturalGrowth().treePaleOakGrowth() : treePaleOakGrowth;
			treeCherryGrowth = treeCherryGrowth == null ? defaults().naturalGrowth().treeCherryGrowth() : treeCherryGrowth;
		}

		public DayRange dirtGrowthForSeason(String seasonId) {
			if (FIELD_SEASON_SPRING.equals(seasonId)) {
				return dirtSpringGrowthDays;
			}
			if (FIELD_SEASON_SUMMER.equals(seasonId)) {
				return dirtSummerGrowthDays;
			}
			if (FIELD_SEASON_FALL.equals(seasonId)) {
				return dirtFallGrowthDays;
			}
			if (FIELD_SEASON_WINTER.equals(seasonId)) {
				return dirtWinterGrowthDays;
			}
			return dirtSummerGrowthDays;
		}

		public DayRange treeGrowthForSeason(String treeType, String seasonId) {
			TreeGrowthProfile profile = switch (treeType) {
				case FIELD_TREE_OAK -> treeOakGrowth;
				case FIELD_TREE_SPRUCE -> treeSpruceGrowth;
				case FIELD_TREE_BIRCH -> treeBirchGrowth;
				case FIELD_TREE_JUNGLE -> treeJungleGrowth;
				case FIELD_TREE_MANGROVE -> treeMangroveGrowth;
				case FIELD_TREE_ACACIA -> treeAcaciaGrowth;
				case FIELD_TREE_DARK_OAK -> treeDarkOakGrowth;
				case FIELD_TREE_PALE_OAK -> treePaleOakGrowth;
				case FIELD_TREE_CHERRY -> treeCherryGrowth;
				default -> null;
			};
			return profile == null ? null : profile.season(seasonId);
		}

		public DayRange grassGrowthForSeason(String seasonId) {
			if (FIELD_SEASON_SPRING.equals(seasonId)) {
				return grassSpringGrowthDays;
			}
			if (FIELD_SEASON_SUMMER.equals(seasonId)) {
				return grassSummerGrowthDays;
			}
			if (FIELD_SEASON_FALL.equals(seasonId)) {
				return grassFallGrowthDays;
			}
			if (FIELD_SEASON_WINTER.equals(seasonId)) {
				return grassWinterGrowthDays;
			}
			return grassSummerGrowthDays;
		}
	}

	public record NaturalErosionSettings(
		int waterErosionRadius,
		ErosionRule blockErosionMud,
		ErosionRule blockErosionRedSand,
		ErosionRule blockErosionSand
	) {
		public NaturalErosionSettings {
			waterErosionRadius = Math.max(0, waterErosionRadius);
			blockErosionMud = blockErosionMud == null ? defaults().naturalErosion().blockErosionMud() : blockErosionMud;
			blockErosionRedSand = blockErosionRedSand == null ? defaults().naturalErosion().blockErosionRedSand() : blockErosionRedSand;
			blockErosionSand = blockErosionSand == null ? defaults().naturalErosion().blockErosionSand() : blockErosionSand;
		}
	}

	public record Settings(SystemSettings system, NaturalGrowthSettings naturalGrowth, NaturalErosionSettings naturalErosion) {
		public Settings {
			system = system == null ? new SystemSettings(true, true) : system;
			naturalGrowth = naturalGrowth == null ? defaults().naturalGrowth() : naturalGrowth;
			naturalErosion = naturalErosion == null ? defaults().naturalErosion() : naturalErosion;
		}
	}

	private static DayRange safeRange(DayRange range, int defaultMin, int defaultMax) {
		if (range == null) {
			return new DayRange(defaultMin, defaultMax);
		}
		return range;
	}
}

package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class MadokuLootTableConfig {
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_TABLE_ID = "table-id";
	public static final String FIELD_ROLLS = "rolls";
	public static final String FIELD_MIN = "min";
	public static final String FIELD_MAX = "max";
	public static final String FIELD_GROUPS = "groups";
	public static final String FIELD_RARITY = "rarity";
	public static final String FIELD_WEIGHT = "weight";
	public static final String FIELD_ENTRIES = "entries";
	public static final String FIELD_ITEM = "item";
	public static final String FIELD_BLOCK = "block";
	public static final String FIELD_ITEM_RARITY = "item-rarity";
	public static final String FIELD_MIN_COUNT = "min-count";
	public static final String FIELD_MAX_COUNT = "max-count";
	public static final String FIELD_USE_MADOKU_LUCK = "use-madoku-luck";
	public static final String FIELD_ROLL_LUCK_MULTIPLIER = "roll-luck-multiplier";
	public static final String FIELD_RARITY_LUCK_MULTIPLIERS = "rarity-luck-multipliers";
	public static final String FIELD_LUCK_POINTS = "luck-points";
	public static final String FIELD_MULTIPLIERS = "multipliers";

	private MadokuLootTableConfig() {
	}

	public static JsonObject buildSettingsDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		root.addProperty(FIELD_USE_MADOKU_LUCK, true);
		root.add(FIELD_ROLL_LUCK_MULTIPLIER, buildMultiplierCurve(new double[] { 1.0d, 1.25d, 1.5d, 1.75d, 2.0d }));
		root.add(FIELD_RARITY_LUCK_MULTIPLIERS, buildRarityLuckMultipliersDefaults());
		return root;
	}

	public static JsonObject buildSampleTableDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		root.addProperty(FIELD_TABLE_ID, "minecraft:structure_chests/starter_chest");

		JsonObject rolls = new JsonObject();
		rolls.addProperty(FIELD_MIN, 7);
		rolls.addProperty(FIELD_MAX, 11);
		root.add(FIELD_ROLLS, rolls);

		JsonArray groups = new JsonArray();
		groups.add(buildGroup("common", 100, new String[][] {
			{ "minecraft:potato", "1", "2", "6" },
			{ "minecraft:carrot", "1", "2", "6" }
		}));
		groups.add(buildGroup("common", 40, new String[][] {
			{ "minecraft:stone_axe", "1", "1", "1", "mythic" },
			{ "minecraft:stone_pickaxe", "1", "1", "1", "mythic" },
			{ "minecraft:stone_sword", "1", "1", "1", "mythic" },
			{ "minecraft:stone_spear", "1", "1", "1", "mythic" },
			{ "minecraft:stone_shovel", "1", "1", "1", "mythic" },
			{ "minecraft:leather_helmet", "1", "1", "1", "mythic" },
			{ "minecraft:leather_chestplate", "1", "1", "1", "mythic" },
			{ "minecraft:leather_leggings", "1", "1", "1", "mythic" },
			{ "minecraft:leather_boots", "1", "1", "1", "mythic" }
		}));
		groups.add(buildGroup("rare", 75, new String[][] {
			{ "minecraft:baked_potato", "1", "2", "6" },
			{ "minecraft:bread", "1", "2", "6" }
		}));
		groups.add(buildGroup("rare", 30, new String[][] {
			{ "minecraft:copper_axe", "1", "1", "1", "epic" },
			{ "minecraft:copper_pickaxe", "1", "1", "1", "epic" },
			{ "minecraft:copper_sword", "1", "1", "1", "epic" },
			{ "minecraft:copper_spear", "1", "1", "1", "epic" },
			{ "minecraft:copper_shovel", "1", "1", "1", "epic" },
			{ "minecraft:copper_helmet", "1", "1", "1", "epic" },
			{ "minecraft:copper_chestplate", "1", "1", "1", "epic" },
			{ "minecraft:copper_leggings", "1", "1", "1", "epic" },
			{ "minecraft:copper_boots", "1", "1", "1", "epic" }
		}));
		root.add(FIELD_GROUPS, groups);
		return root;
	}

	public static JsonObject buildStructureTableTemplate(String tableId) {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, false);
		root.addProperty(FIELD_TABLE_ID, tableId == null ? "" : tableId);

		JsonObject rolls = new JsonObject();
		rolls.addProperty(FIELD_MIN, 1);
		rolls.addProperty(FIELD_MAX, 3);
		root.add(FIELD_ROLLS, rolls);
		root.add(FIELD_GROUPS, new JsonArray());
		return root;
	}

	public static JsonObject buildVillageTableDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		root.addProperty(FIELD_TABLE_ID, "minecraft:structure_chests/village");

		JsonObject rolls = new JsonObject();
		rolls.addProperty(FIELD_MIN, 3);
		rolls.addProperty(FIELD_MAX, 7);
		root.add(FIELD_ROLLS, rolls);

		JsonArray groups = new JsonArray();
		groups.add(buildGroup("common", 100, new String[][] {
			{ "minecraft:bread", "1", "2", "6" },
			{ "minecraft:baked_potato", "1", "2", "6" }
		}));
		groups.add(buildGroup("rare", 75, new String[][] {
			{ "minecraft:emerald", "1", "3", "5" }
		}));
		groups.add(buildGroup("epic", 50, new String[][] {
			{ "minecraft:coal", "42", "5", "7" },
			{ "minecraft:copper_ingot", "27", "4", "6" },
			{ "minecraft:iron_ingot", "17", "3", "5" },
			{ "minecraft:gold_ingot", "10", "2", "4" },
			{ "minecraft:diamond", "3", "1", "3" },
			{ "minecraft:netherite_scrap", "1", "0", "2" }
		}));
		root.add(FIELD_GROUPS, groups);
		return root;
	}

	public static JsonObject buildAbandonedMineshaftTableDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		root.addProperty(FIELD_TABLE_ID, "minecraft:structure_chests/abandoned_mineshaft");

		JsonObject rolls = new JsonObject();
		rolls.addProperty(FIELD_MIN, 3);
		rolls.addProperty(FIELD_MAX, 7);
		root.add(FIELD_ROLLS, rolls);

		JsonArray groups = new JsonArray();
		groups.add(buildGroup("common", 100, new String[][] {
			{ "minecraft:bread", "1", "2", "6" },
			{ "minecraft:baked_potato", "1", "2", "6" }
		}));
		groups.add(buildGroup("rare", 75, new String[][] {
			{ "minecraft:music_disc_13", "1", "1", "1" },
			{ "minecraft:music_disc_cat", "1", "1", "1" },
			{ "minecraft:music_disc_blocks", "1", "1", "1" },
			{ "minecraft:music_disc_chirp", "1", "1", "1" },
			{ "minecraft:music_disc_far", "1", "1", "1" },
			{ "minecraft:music_disc_mall", "1", "1", "1" },
			{ "minecraft:music_disc_mellohi", "1", "1", "1" },
			{ "minecraft:music_disc_stal", "1", "1", "1" },
			{ "minecraft:music_disc_strad", "1", "1", "1" },
			{ "minecraft:music_disc_ward", "1", "1", "1" },
			{ "minecraft:music_disc_11", "1", "1", "1" },
			{ "minecraft:music_disc_wait", "1", "1", "1" },
			{ "minecraft:music_disc_otherside", "1", "1", "1" },
			{ "minecraft:music_disc_5", "1", "1", "1" },
			{ "minecraft:music_disc_pigstep", "1", "1", "1" },
			{ "minecraft:music_disc_relic", "1", "1", "1" },
			{ "minecraft:music_disc_creator", "1", "1", "1" },
			{ "minecraft:music_disc_creator_music_box", "1", "1", "1" },
			{ "minecraft:music_disc_precipice", "1", "1", "1" }
		}));
		groups.add(buildGroup("epic", 50, new String[][] {
			{ "minecraft:coal", "42", "5", "7" },
			{ "minecraft:copper_ingot", "27", "4", "6" },
			{ "minecraft:iron_ingot", "17", "3", "5" },
			{ "minecraft:gold_ingot", "10", "2", "4" },
			{ "minecraft:diamond", "3", "1", "3" },
			{ "minecraft:netherite_scrap", "1", "0", "2" }
		}));
		groups.add(buildGroup("mythic", 25, new String[][] {
			{ "minecraft:chicken_spawn_egg", "100", "1", "1" },
			{ "minecraft:zombie_spawn_egg", "100", "1", "1" },
			{ "minecraft:pig_spawn_egg", "100", "1", "1" },
			{ "minecraft:sheep_spawn_egg", "100", "1", "1" },
			{ "minecraft:cow_spawn_egg", "75", "1", "1" },
			{ "minecraft:skeleton_spawn_egg", "75", "1", "1" },
			{ "minecraft:spider_spawn_egg", "75", "1", "1" },
			{ "minecraft:creeper_spawn_egg", "75", "1", "1" },
			{ "minecraft:bat_spawn_egg", "50", "1", "1" },
			{ "minecraft:bee_spawn_egg", "50", "1", "1" }
		}));
		root.add(FIELD_GROUPS, groups);
		return root;
	}

	private static JsonObject buildRarityLuckMultipliersDefaults() {
		JsonObject root = new JsonObject();
		root.add(MadokuLootRarity.COMMON.id(), buildMultiplierCurve(new double[] { 1.0d, 0.875d, 0.75d, 0.625d, 0.5d }));
		root.add(MadokuLootRarity.RARE.id(), buildMultiplierCurve(new double[] { 1.0d, 0.9375d, 0.875d, 0.8125d, 0.75d }));
		root.add(MadokuLootRarity.EPIC.id(), buildMultiplierCurve(new double[] { 1.0d, 1.25d, 1.5d, 1.75d, 2.0d }));
		root.add(MadokuLootRarity.MYTHIC.id(), buildMultiplierCurve(new double[] { 1.0d, 1.5d, 2.0d, 3.0d, 4.0d }));
		return root;
	}

	private static JsonObject buildMultiplierCurve(double[] multipliers) {
		JsonObject root = new JsonObject();
		JsonArray points = new JsonArray();
		points.add(0.0d);
		points.add(25.0d);
		points.add(50.0d);
		points.add(75.0d);
		points.add(100.0d);

		JsonArray values = new JsonArray();
		for (double multiplier : multipliers) {
			values.add(multiplier);
		}

		root.add(FIELD_LUCK_POINTS, points);
		root.add(FIELD_MULTIPLIERS, values);
		return root;
	}

	private static JsonObject buildGroup(String rarity, int weight, String[][] entries) {
		JsonObject group = new JsonObject();
		group.addProperty(FIELD_RARITY, rarity);
		group.addProperty(FIELD_WEIGHT, weight);

		JsonArray groupEntries = new JsonArray();
		for (String[] entry : entries) {
			JsonObject entryRoot = new JsonObject();
			entryRoot.addProperty(FIELD_ITEM, entry[0]);
			entryRoot.addProperty(FIELD_WEIGHT, Integer.parseInt(entry[1]));
			entryRoot.addProperty(FIELD_MIN_COUNT, Integer.parseInt(entry[2]));
			entryRoot.addProperty(FIELD_MAX_COUNT, Integer.parseInt(entry[3]));
			if (entry.length >= 5 && entry[4] != null && !entry[4].isBlank()) {
				entryRoot.addProperty(FIELD_ITEM_RARITY, entry[4]);
			}
			groupEntries.add(entryRoot);
		}
		group.add(FIELD_ENTRIES, groupEntries);
		return group;
	}
}

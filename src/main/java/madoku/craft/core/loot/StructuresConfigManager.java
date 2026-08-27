package madoku.craft.core.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.json.MadokuJSONManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StructuresConfigManager {
	private StructuresConfigManager() {
	}

	public static void initialize() { }
	public static void reset() { }

	public static JsonObject buildStructureTableTemplate(String tableId) {
		return JSONFormatManager.object()
			.put(LootTableConfigManager.FIELD_ENABLED, true)
			.put(LootTableConfigManager.FIELD_TABLE_ID, MadokuJSONManager.normalizeRegistryIdentifierForJson(tableId))
			.object(LootTableConfigManager.FIELD_ROLLS, rolls -> rolls
				.put(LootTableConfigManager.FIELD_MIN, 1)
				.put(LootTableConfigManager.FIELD_MAX, 3))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> {
			})
			.build();
	}

	public static JsonObject buildStructureTable(String tableId, int minRolls, int maxRolls) {
		return JSONFormatManager.object()
			.put(LootTableConfigManager.FIELD_ENABLED, true)
			.put(LootTableConfigManager.FIELD_TABLE_ID, MadokuJSONManager.normalizeRegistryIdentifierForJson(tableId))
			.object(LootTableConfigManager.FIELD_ROLLS, rolls -> rolls
				.put(LootTableConfigManager.FIELD_MIN, Math.max(0, minRolls))
				.put(LootTableConfigManager.FIELD_MAX, Math.max(minRolls, maxRolls)))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> {
			})
			.build();
	}

	public static JsonObject group(String rarity, int weight, JsonArray entries) {
		return JSONFormatManager.object()
			.put(LootTableConfigManager.FIELD_RARITY, rarity == null ? "common" : rarity)
			.put(LootTableConfigManager.FIELD_WEIGHT, Math.max(0, weight))
			.put(LootTableConfigManager.FIELD_ENTRIES, entries == null ? JSONFormatManager.array().build() : entries)
			.build();
	}

	public static JsonObject group(String rarity, int weight, List<String> tags, JsonArray entries) {
		JSONFormatManager.ObjectBuilder group = JSONFormatManager.object()
			.put(LootTableConfigManager.FIELD_RARITY, rarity == null ? "common" : rarity)
			.put(LootTableConfigManager.FIELD_WEIGHT, Math.max(0, weight))
			.put(LootTableConfigManager.FIELD_ENTRIES, entries == null ? JSONFormatManager.array().build() : entries);
		JSONFormatManager.ArrayBuilder tagArray = JSONFormatManager.array();
		if (tags != null) {
			for (String tag : tags) {
				if (tag == null || tag.isBlank()) {
					continue;
				}
				tagArray.add(tag);
			}
		}
		JsonArray builtTags = tagArray.build();
		if (!builtTags.isEmpty()) {
			group.put(LootTableConfigManager.FIELD_TAGS, builtTags);
		}
		return group.build();
	}

	public static JsonArray entries(JsonObject... entries) {
		JSONFormatManager.ArrayBuilder array = JSONFormatManager.array();
		if (entries != null) {
			for (JsonObject entry : entries) {
				if (entry == null || entry.isEmpty()) {
					continue;
				}
				array.add(entry);
			}
		}
		return array.build();
	}

	public static JsonObject item(String itemId, int weight, int minCount, int maxCount) {
		return JSONFormatManager.object()
			.put(LootTableConfigManager.FIELD_ITEM, MadokuJSONManager.normalizeRegistryIdentifierForJson(itemId))
			.put(LootTableConfigManager.FIELD_WEIGHT, Math.max(1, weight))
			.put(LootTableConfigManager.FIELD_MIN_COUNT, minCount)
			.put(LootTableConfigManager.FIELD_MAX_COUNT, Math.max(minCount, maxCount))
			.build();
	}

	public static JsonObject item(String itemId, int weight, int minCount, int maxCount, String itemRarity) {
		JSONFormatManager.ObjectBuilder entry = JSONFormatManager.object()
			.put(LootTableConfigManager.FIELD_ITEM, MadokuJSONManager.normalizeRegistryIdentifierForJson(itemId))
			.put(LootTableConfigManager.FIELD_WEIGHT, Math.max(1, weight))
			.put(LootTableConfigManager.FIELD_MIN_COUNT, minCount)
			.put(LootTableConfigManager.FIELD_MAX_COUNT, Math.max(minCount, maxCount));
		if (itemRarity != null && !itemRarity.isBlank()) {
			entry.put(LootTableConfigManager.FIELD_ITEM_RARITY, itemRarity);
		}
		return entry.build();
	}

	private static JsonArray defaultMadokuPetEntries() {
		return StructuresConfigManager.entries(
			StructuresConfigManager.item("madoku-craft:zombie-pet", 100, 1, 1),
			StructuresConfigManager.item("madoku-craft:pig-pet", 100, 1, 1),
			StructuresConfigManager.item("madoku-craft:sheep-pet", 100, 1, 1),
			StructuresConfigManager.item("madoku-craft:cow-pet", 40, 1, 1),
			StructuresConfigManager.item("madoku-craft:skeleton-pet", 80, 1, 1),
			StructuresConfigManager.item("madoku-craft:spider-pet", 80, 1, 1),
			StructuresConfigManager.item("madoku-craft:creeper-pet", 80, 1, 1),
			StructuresConfigManager.item("madoku-craft:bat-pet", 60, 1, 1),
			StructuresConfigManager.item("madoku-craft:bee-pet", 60, 1, 1),
			StructuresConfigManager.item("madoku-craft:chicken-pet", 40, 1, 1)
		);
	}

	public static Map<String, JsonObject> buildDefaultStructureTableFiles() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		put(defaults, "minecraft:structure_chests/abandoned_mineshaft", buildDefaultAbandonedMineshaft());
		put(defaults, "minecraft:structure_chests/ancient_city", buildDefaultAncientCity());
		put(defaults, "minecraft:structure_chests/bastion_remnant", buildDefaultBastionRemnant());
		put(defaults, "minecraft:structure_chests/buried_treasure", buildDefaultBuriedTreasure());
		put(defaults, "minecraft:structure_chests/desert_pyramid", buildDefaultDesertPyramid());
		put(defaults, "minecraft:structure_chests/dungeon", buildDefaultDungeon());
		put(defaults, "minecraft:structure_chests/end_city", buildDefaultEndCity());
		put(defaults, "minecraft:structure_chests/igloo", buildDefaultIgloo());
		put(defaults, "minecraft:structure_chests/jungle_temple", buildDefaultJungleTemple());
		put(defaults, "minecraft:structure_chests/nether_fortress", buildDefaultNetherFortress());
		put(defaults, "minecraft:structure_chests/pillager_outpost", buildDefaultPillagerOutpost());
		put(defaults, "minecraft:structure_chests/ruined_portal", buildDefaultRuinedPortal());
		put(defaults, "minecraft:structure_chests/shipwreck", buildDefaultShipwreck());
		put(defaults, "minecraft:structure_chests/starter_chest", buildDefaultStarterChest());
		put(defaults, "minecraft:structure_chests/stronghold", buildDefaultStronghold());
		put(defaults, "minecraft:structure_chests/trial_chambers", buildDefaultTrialChambers());
		put(defaults, "minecraft:structure_chests/underwater_ruin", buildDefaultUnderwaterRuin());
		put(defaults, "minecraft:structure_chests/village", buildDefaultVillage());
		put(defaults, "minecraft:structure_chests/woodland_mansion", buildDefaultWoodlandMansion());
		return defaults;
	}

	private static JsonObject buildDefaultAbandonedMineshaft() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/abandoned_mineshaft", 5, 9))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("legendary", 40, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-lava-chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-bounce", 1, 0, 1)
				)))
				.add(StructuresConfigManager.group("mythic", 20, List.of("madoku-pets"), defaultMadokuPetEntries())))
			.build();

	}

	private static JsonObject buildDefaultAncientCity() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/ancient_city", 5, 9))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 2, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 2, 2, 6)
				)))
				.add(StructuresConfigManager.group("rare", 80, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:echo_shard", 1, 1, 3)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("legendary", 40, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-lava-chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-bounce", 1, 0, 1)
				)))
				.add(StructuresConfigManager.group("mythic", 20, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:diamond_sword", 100, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:diamond_spear", 100, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:diamond_helmet", 100, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:diamond_chestplate", 100, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:diamond_leggings", 100, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:diamond_boots", 100, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:diamond_pickaxe", 100, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:diamond_axe", 100, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:diamond_shovel", 100, 1, 1, "mythic")
				))))
			.build();

	}

	private static JsonObject buildDefaultBastionRemnant() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/bastion_remnant", 5, 9))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 2, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 2, 2, 6)
				)))
				.add(StructuresConfigManager.group("rare", 80, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-lava-chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-bounce", 1, 0, 1)
				)))
				.add(StructuresConfigManager.group("legendary", 40, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:netherite_sword", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:netherite_spear", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:netherite_helmet", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:netherite_chestplate", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:netherite_leggings", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:netherite_boots", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:netherite_pickaxe", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:netherite_axe", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:netherite_shovel", 100, 1, 1, "epic")
				)))
				.add(StructuresConfigManager.group("mythic", 20, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:netherite_upgrade_smithing_template", 100, 1, 1)
				))))
			.build();

	}

	private static JsonObject buildDefaultBuriedTreasure() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/buried_treasure", 12, 16))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("legendary", 40, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-lava-chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-bounce", 1, 0, 1)
				)))
				.add(StructuresConfigManager.group("mythic", 20, List.of("madoku-pets"), defaultMadokuPetEntries())))
			.build();

	}

	private static JsonObject buildDefaultDesertPyramid() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/desert_pyramid", 5, 9))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("legendary", 40, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-lava-chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-bounce", 1, 0, 1)
				)))
				.add(StructuresConfigManager.group("mythic", 20, List.of("madoku-pets"), defaultMadokuPetEntries())))
			.build();

	}

	private static JsonObject buildDefaultDungeon() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/dungeon", 12, 16))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("legendary", 40, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-lava-chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-bounce", 1, 0, 1)
				)))
				.add(StructuresConfigManager.group("mythic", 20, List.of("madoku-pets"), defaultMadokuPetEntries())))
			.build();

	}

	private static JsonObject buildDefaultEndCity() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/end_city", 5, 9))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("rare", 80, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-lava-chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-bounce", 1, 0, 1)
				)))
				.add(StructuresConfigManager.group("legendary", 40, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:netherite_sword", 100, 1, 1, "rare"),
					StructuresConfigManager.item("minecraft:netherite_spear", 100, 1, 1, "rare"),
					StructuresConfigManager.item("minecraft:netherite_helmet", 100, 1, 1, "rare"),
					StructuresConfigManager.item("minecraft:netherite_chestplate", 100, 1, 1, "rare"),
					StructuresConfigManager.item("minecraft:netherite_leggings", 100, 1, 1, "rare"),
					StructuresConfigManager.item("minecraft:netherite_boots", 100, 1, 1, "rare"),
					StructuresConfigManager.item("minecraft:netherite_pickaxe", 100, 1, 1, "rare"),
					StructuresConfigManager.item("minecraft:netherite_axe", 100, 1, 1, "rare"),
					StructuresConfigManager.item("minecraft:netherite_shovel", 100, 1, 1, "rare")
				)))
				.add(StructuresConfigManager.group("mythic", 20, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:diamond_sword", 100, 1, 1, "legendary"),
					StructuresConfigManager.item("minecraft:diamond_spear", 100, 1, 1, "legendary"),
					StructuresConfigManager.item("minecraft:diamond_helmet", 100, 1, 1, "legendary"),
					StructuresConfigManager.item("minecraft:diamond_chestplate", 100, 1, 1, "legendary"),
					StructuresConfigManager.item("minecraft:diamond_leggings", 100, 1, 1, "legendary"),
					StructuresConfigManager.item("minecraft:diamond_boots", 100, 1, 1, "legendary"),
					StructuresConfigManager.item("minecraft:diamond_pickaxe", 100, 1, 1, "legendary"),
					StructuresConfigManager.item("minecraft:diamond_axe", 100, 1, 1, "legendary"),
					StructuresConfigManager.item("minecraft:diamond_shovel", 100, 1, 1, "legendary")
				))))
			.build();

	}

	private static JsonObject buildDefaultIgloo() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/igloo", 12, 16))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("mythic", 20, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-lava-chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-bounce", 1, 0, 1)
				))))
			.build();

	}

	private static JsonObject buildDefaultJungleTemple() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/jungle_temple", 12, 16))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("legendary", 40, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-lava-chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-bounce", 1, 0, 1)
				)))
				.add(StructuresConfigManager.group("mythic", 20, List.of("madoku-pets"), defaultMadokuPetEntries())))
			.build();

	}

	private static JsonObject buildDefaultPillagerOutpost() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/pillager_outpost", 12, 16))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("rare", 80, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_lava_chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_bounce", 1, 0, 1)
				)))
				.add(StructuresConfigManager.group("legendary", 40, List.of("madoku-pets"), defaultMadokuPetEntries()))
				.add(StructuresConfigManager.group("mythic", 20, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:diamond_sword", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:diamond_spear", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:diamond_helmet", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:diamond_chestplate", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:diamond_leggings", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:diamond_boots", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:diamond_pickaxe", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:diamond_axe", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:diamond_shovel", 100, 1, 1, "common")
				))))
			.build();

	}

	private static JsonObject buildDefaultNetherFortress() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/nether_fortress", 5, 9))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("rare", 80, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-lava-chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-bounce", 1, 0, 1)
				)))
				.add(StructuresConfigManager.group("legendary", 40, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:netherite_sword", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:netherite_spear", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:netherite_helmet", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:netherite_chestplate", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:netherite_leggings", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:netherite_boots", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:netherite_pickaxe", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:netherite_axe", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:netherite_shovel", 100, 1, 1, "common")
				)))
				.add(StructuresConfigManager.group("mythic", 20, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:diamond_sword", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:diamond_spear", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:diamond_helmet", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:diamond_chestplate", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:diamond_leggings", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:diamond_boots", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:diamond_pickaxe", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:diamond_axe", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:diamond_shovel", 100, 1, 1, "epic")
				))))
			.build();

	}

	private static JsonObject buildDefaultRuinedPortal() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/ruined_portal", 12, 16))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("legendary", 40, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-lava-chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-bounce", 1, 0, 1)
				)))
				.add(StructuresConfigManager.group("mythic", 20, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:golden_sword", 100, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:golden_spear", 100, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:golden_helmet", 100, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:golden_chestplate", 100, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:golden_leggings", 100, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:golden_boots", 100, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:golden_pickaxe", 100, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:golden_axe", 100, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:golden_shovel", 100, 1, 1, "mythic")
				))))
			.build();

	}

	private static JsonObject buildDefaultShipwreck() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/shipwreck", 12, 16))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("rare", 80, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:heart_of_the_sea", 1, 1, 3)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("legendary", 40, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-lava-chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-bounce", 1, 0, 1)
				)))
				.add(StructuresConfigManager.group("mythic", 20, List.of("madoku-pets"), defaultMadokuPetEntries())))
			.build();

	}

	private static JsonObject buildDefaultStarterChest() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/starter_chest", 12, 16))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:potato", 1, 2, 6),
					StructuresConfigManager.item("minecraft:carrot", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:stone_axe", 1, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:stone_pickaxe", 1, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:stone_sword", 1, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:stone_spear", 1, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:stone_shovel", 1, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:chainmail_helmet", 1, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:chainmail_chestplate", 1, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:chainmail_leggings", 1, 1, 1, "mythic"),
					StructuresConfigManager.item("minecraft:chainmail_boots", 1, 1, 1, "mythic")
				)))
				.add(StructuresConfigManager.group("rare", 80, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6),
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("legendary", 40, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:copper_axe", 1, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:copper_pickaxe", 1, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:copper_sword", 1, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:copper_spear", 1, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:copper_shovel", 1, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:copper_helmet", 1, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:copper_chestplate", 1, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:copper_leggings", 1, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:copper_boots", 1, 1, 1, "epic")
				))))
			.build();

	}

	private static JsonObject buildDefaultStronghold() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/stronghold", 5, 9))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("rare", 80, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-lava-chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-bounce", 1, 0, 1)
				)))
				.add(StructuresConfigManager.group("legendary", 40, List.of("madoku-pets"), defaultMadokuPetEntries()))
				.add(StructuresConfigManager.group("mythic", 20, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:diamond_sword", 100, 1, 1, "rare"),
					StructuresConfigManager.item("minecraft:diamond_spear", 100, 1, 1, "rare"),
					StructuresConfigManager.item("minecraft:diamond_helmet", 100, 1, 1, "rare"),
					StructuresConfigManager.item("minecraft:diamond_chestplate", 100, 1, 1, "rare"),
					StructuresConfigManager.item("minecraft:diamond_leggings", 100, 1, 1, "rare"),
					StructuresConfigManager.item("minecraft:diamond_boots", 100, 1, 1, "rare"),
					StructuresConfigManager.item("minecraft:diamond_pickaxe", 100, 1, 1, "rare"),
					StructuresConfigManager.item("minecraft:diamond_axe", 100, 1, 1, "rare"),
					StructuresConfigManager.item("minecraft:diamond_shovel", 100, 1, 1, "rare")
				))))
			.build();

	}

	private static JsonObject buildDefaultTrialChambers() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/trial_chambers", 5, 9))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("rare", 80, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-lava-chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-bounce", 1, 0, 1)
				)))
				.add(StructuresConfigManager.group("legendary", 40, List.of("madoku-pets"), defaultMadokuPetEntries()))
				.add(StructuresConfigManager.group("mythic", 20, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:diamond_sword", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:diamond_spear", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:diamond_helmet", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:diamond_chestplate", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:diamond_leggings", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:diamond_boots", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:diamond_pickaxe", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:diamond_axe", 100, 1, 1, "common"),
					StructuresConfigManager.item("minecraft:diamond_shovel", 100, 1, 1, "common")
				))))
			.build();

	}

	private static JsonObject buildDefaultUnderwaterRuin() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/underwater_ruin", 7, 11))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("rare", 80, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:heart_of_the_sea", 1, 1, 3)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("legendary", 40, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-lava-chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-bounce", 1, 0, 1)
				)))
				.add(StructuresConfigManager.group("mythic", 20, List.of("madoku-pets"), defaultMadokuPetEntries())))
			.build();

	}

	private static JsonObject buildDefaultVillage() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/village", 5, 9))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:emerald", 1, 3, 5)
				)))
				.add(StructuresConfigManager.group("mythic", 20, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				))))
			.build();

	}

	private static JsonObject buildDefaultWoodlandMansion() {
		return JSONFormatManager.object()
			.putAll(StructuresConfigManager.buildStructureTable("minecraft:structure_chests/woodland_mansion", 5, 9))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(StructuresConfigManager.group("common", 100, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:bread", 1, 2, 6),
					StructuresConfigManager.item("minecraft:baked_potato", 1, 2, 6)
				)))
				.add(StructuresConfigManager.group("rare", 80, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:coal", 42, 5, 7),
					StructuresConfigManager.item("minecraft:copper_ingot", 27, 4, 6),
					StructuresConfigManager.item("minecraft:iron_ingot", 17, 3, 5),
					StructuresConfigManager.item("minecraft:gold_ingot", 10, 2, 4),
					StructuresConfigManager.item("minecraft:diamond", 3, 1, 3),
					StructuresConfigManager.item("minecraft:netherite_scrap", 1, 0, 2)
				)))
				.add(StructuresConfigManager.group("epic", 60, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:music_disc_13", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_cat", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_blocks", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_chirp", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_far", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mall", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_mellohi", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_stal", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_strad", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_ward", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_11", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_wait", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_otherside", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_5", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_pigstep", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_relic", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_creator_music_box", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music_disc_precipice", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-tears", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-lava-chicken", 1, 0, 1),
					StructuresConfigManager.item("minecraft:music-disc-bounce", 1, 0, 1)
				)))
				.add(StructuresConfigManager.group("legendary", 40, List.of("madoku-pets"), defaultMadokuPetEntries()))
				.add(StructuresConfigManager.group("mythic", 20, StructuresConfigManager.entries(
					StructuresConfigManager.item("minecraft:diamond_sword", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:diamond_spear", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:diamond_helmet", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:diamond_chestplate", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:diamond_leggings", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:diamond_boots", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:diamond_pickaxe", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:diamond_axe", 100, 1, 1, "epic"),
					StructuresConfigManager.item("minecraft:diamond_shovel", 100, 1, 1, "epic")
				))))
			.build();

	}
	private static void put(Map<String, JsonObject> defaults, String tableId, JsonObject root) {
		if (defaults == null || root == null || tableId == null || tableId.isBlank()) {
			return;
		}
		defaults.put(LootTableConfigManager.fileKeyFromTableId(tableId, "structure-table"), root);
	}
}

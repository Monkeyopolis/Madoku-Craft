package madoku.craft.api.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;

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
			.put(LootTableConfigManager.FIELD_TABLE_ID, tableId == null ? "" : tableId)
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
			.put(LootTableConfigManager.FIELD_TABLE_ID, tableId == null ? "" : tableId)
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
			.put(LootTableConfigManager.FIELD_ITEM, itemId == null ? "" : itemId)
			.put(LootTableConfigManager.FIELD_WEIGHT, Math.max(1, weight))
			.put(LootTableConfigManager.FIELD_MIN_COUNT, minCount)
			.put(LootTableConfigManager.FIELD_MAX_COUNT, Math.max(minCount, maxCount))
			.build();
	}

	public static JsonObject item(String itemId, int weight, int minCount, int maxCount, String itemRarity) {
		JSONFormatManager.ObjectBuilder entry = JSONFormatManager.object()
			.put(LootTableConfigManager.FIELD_ITEM, itemId == null ? "" : itemId)
			.put(LootTableConfigManager.FIELD_WEIGHT, Math.max(1, weight))
			.put(LootTableConfigManager.FIELD_MIN_COUNT, minCount)
			.put(LootTableConfigManager.FIELD_MAX_COUNT, Math.max(minCount, maxCount));
		if (itemRarity != null && !itemRarity.isBlank()) {
			entry.put(LootTableConfigManager.FIELD_ITEM_RARITY, itemRarity);
		}
		return entry.build();
	}

	public static Map<String, JsonObject> buildDefaultStructureTableFiles() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		String[] ids = {"abandoned_mineshaft", "ancient_city", "bastion_remnant", "buried_treasure", "desert_pyramid", "dungeon", "end_city", "igloo", "jungle_temple", "nether_fortress", "ruined_portal", "shipwreck", "starter_chest", "stronghold", "trial_chambers", "underwater_ruin", "village", "woodland_mansion"};
		for (String id : ids) {
			String tableId = "minecraft:structure_chests/" + id;
			put(defaults, tableId, buildDefaultStructure(tableId));
		}
		return defaults;
	}

	private static JsonObject buildDefaultStructure(String tableId) {
		return JSONFormatManager.object()
			.putAll(buildStructureTable(tableId, 3, 7))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(group("common", 100, entries(item("minecraft:bread", 1, 2, 6), item("minecraft:baked_potato", 1, 2, 6))))
				.add(group("rare", 75, entries(item("minecraft:emerald", 1, 1, 3))))
				.add(group("epic", 50, entries(item("minecraft:coal", 42, 5, 7), item("minecraft:iron_ingot", 17, 3, 5), item("minecraft:gold_ingot", 10, 2, 4), item("minecraft:diamond", 3, 1, 3)))))
			.build();
	}

	private static void put(Map<String, JsonObject> defaults, String tableId, JsonObject root) {
		if (defaults == null || root == null || tableId == null || tableId.isBlank()) {
			return;
		}
		defaults.put(LootTableConfigManager.fileKeyFromTableId(tableId, "structure-table"), root);
	}
}


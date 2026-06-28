package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import madoku.craft.config.JsonFormatBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LootTableConfigStructures {
	private LootTableConfigStructures() {
	}

	public static JsonObject buildStructureTableTemplate(String tableId) {
		return JsonFormatBuilder.object()
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
		return JsonFormatBuilder.object()
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
		return JsonFormatBuilder.object()
			.put(LootTableConfigManager.FIELD_RARITY, rarity == null ? "common" : rarity)
			.put(LootTableConfigManager.FIELD_WEIGHT, Math.max(0, weight))
			.put(LootTableConfigManager.FIELD_ENTRIES, entries == null ? JsonFormatBuilder.array().build() : entries)
			.build();
	}

	public static JsonObject group(String rarity, int weight, List<String> tags, JsonArray entries) {
		JsonFormatBuilder.ObjectBuilder group = JsonFormatBuilder.object()
			.put(LootTableConfigManager.FIELD_RARITY, rarity == null ? "common" : rarity)
			.put(LootTableConfigManager.FIELD_WEIGHT, Math.max(0, weight))
			.put(LootTableConfigManager.FIELD_ENTRIES, entries == null ? JsonFormatBuilder.array().build() : entries);
		JsonFormatBuilder.ArrayBuilder tagArray = JsonFormatBuilder.array();
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
		JsonFormatBuilder.ArrayBuilder array = JsonFormatBuilder.array();
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
		return JsonFormatBuilder.object()
			.put(LootTableConfigManager.FIELD_ITEM, itemId == null ? "" : itemId)
			.put(LootTableConfigManager.FIELD_WEIGHT, Math.max(1, weight))
			.put(LootTableConfigManager.FIELD_MIN_COUNT, minCount)
			.put(LootTableConfigManager.FIELD_MAX_COUNT, Math.max(minCount, maxCount))
			.build();
	}

	public static JsonObject item(String itemId, int weight, int minCount, int maxCount, String itemRarity) {
		JsonFormatBuilder.ObjectBuilder entry = JsonFormatBuilder.object()
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
		put(defaults, "minecraft:structure_chests/abandoned_mineshaft", ConfigStructuresAbandonedMineshaft.buildDefaults());
		put(defaults, "minecraft:structure_chests/ancient_city", ConfigStructuresAncientCity.buildDefaults());
		put(defaults, "minecraft:structure_chests/bastion_remnant", ConfigStructuresBastionRemnant.buildDefaults());
		put(defaults, "minecraft:structure_chests/buried_treasure", ConfigStructuresBuriedTreasure.buildDefaults());
		put(defaults, "minecraft:structure_chests/desert_pyramid", ConfigStructuresDesertPyramid.buildDefaults());
		put(defaults, "minecraft:structure_chests/dungeon", ConfigStructuresDungeon.buildDefaults());
		put(defaults, "minecraft:structure_chests/end_city", ConfigStructuresEndCity.buildDefaults());
		put(defaults, "minecraft:structure_chests/igloo", ConfigStructuresIgloo.buildDefaults());
		put(defaults, "minecraft:structure_chests/jungle_temple", ConfigStructuresJungleTemple.buildDefaults());
		put(defaults, "minecraft:structure_chests/nether_fortress", ConfigStructuresNetherFortress.buildDefaults());
		put(defaults, "minecraft:structure_chests/ruined_portal", ConfigStructuresRuinedPortal.buildDefaults());
		put(defaults, "minecraft:structure_chests/shipwreck", ConfigStructuresShipwreck.buildDefaults());
		put(defaults, "minecraft:structure_chests/starter_chest", ConfigStructuresStarterChest.buildDefaults());
		put(defaults, "minecraft:structure_chests/stronghold", ConfigStructuresStronghold.buildDefaults());
		put(defaults, "minecraft:structure_chests/trial_chambers", ConfigStructuresTrialChambers.buildDefaults());
		put(defaults, "minecraft:structure_chests/underwater_ruin", ConfigStructuresUnderwaterRuin.buildDefaults());
		put(defaults, "minecraft:structure_chests/village", ConfigStructuresVillage.buildDefaults());
		put(defaults, "minecraft:structure_chests/woodland_mansion", ConfigStructuresWoodlandMansion.buildDefaults());
		return defaults;
	}

	private static void put(Map<String, JsonObject> defaults, String tableId, JsonObject root) {
		if (defaults == null || root == null || tableId == null || tableId.isBlank()) {
			return;
		}
		defaults.put(fileKeyFromTableId(tableId), root);
	}

	private static String fileKeyFromTableId(String tableId) {
		String normalized = tableId == null ? "" : tableId.trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			return "structure-table";
		}
		StringBuilder key = new StringBuilder(normalized.length() + 8);
		boolean previousDash = false;
		for (int index = 0; index < normalized.length(); index++) {
			char value = normalized.charAt(index);
			if (Character.isLetterOrDigit(value)) {
				key.append(value);
				previousDash = false;
				continue;
			}
			if (!previousDash) {
				key.append('-');
				previousDash = true;
			}
		}
		int start = 0;
		while (start < key.length() && key.charAt(start) == '-') {
			start++;
		}
		int end = key.length();
		while (end > start && key.charAt(end - 1) == '-') {
			end--;
		}
		String collapsed = key.substring(start, end);
		return collapsed.isBlank() ? "structure-table" : collapsed;
	}
}

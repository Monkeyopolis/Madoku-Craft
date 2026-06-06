package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LootTableConfigStructures {
	private LootTableConfigStructures() {
	}

	public static JsonObject buildStructureTableTemplate(String tableId) {
		JsonObject root = new JsonObject();
		root.addProperty(LootTableConfigManager.FIELD_ENABLED, true);
		root.addProperty(LootTableConfigManager.FIELD_TABLE_ID, tableId == null ? "" : tableId);

		JsonObject rolls = new JsonObject();
		rolls.addProperty(LootTableConfigManager.FIELD_MIN, 1);
		rolls.addProperty(LootTableConfigManager.FIELD_MAX, 3);
		root.add(LootTableConfigManager.FIELD_ROLLS, rolls);
		root.add(LootTableConfigManager.FIELD_GROUPS, new JsonArray());
		return root;
	}

	public static JsonObject buildStructureTable(String tableId, int minRolls, int maxRolls) {
		JsonObject root = buildStructureTableTemplate(tableId);
		JsonObject rolls = new JsonObject();
		rolls.addProperty(LootTableConfigManager.FIELD_MIN, Math.max(0, minRolls));
		rolls.addProperty(LootTableConfigManager.FIELD_MAX, Math.max(minRolls, maxRolls));
		root.add(LootTableConfigManager.FIELD_ROLLS, rolls);
		return root;
	}

	public static JsonObject group(String rarity, int weight, JsonArray entries) {
		JsonObject group = new JsonObject();
		group.addProperty(LootTableConfigManager.FIELD_RARITY, rarity == null ? "common" : rarity);
		group.addProperty(LootTableConfigManager.FIELD_WEIGHT, Math.max(0, weight));
		group.add(
			LootTableConfigManager.FIELD_ENTRIES,
			entries == null ? new JsonArray() : entries
		);
		return group;
	}

	public static JsonObject group(String rarity, int weight, List<String> tags, JsonArray entries) {
		JsonObject group = group(rarity, weight, entries);
		JsonArray tagArray = new JsonArray();
		if (tags != null) {
			for (String tag : tags) {
				if (tag == null || tag.isBlank()) {
					continue;
				}
				tagArray.add(tag);
			}
		}
		if (!tagArray.isEmpty()) {
			group.add(LootTableConfigManager.FIELD_TAGS, tagArray);
		}
		return group;
	}

	public static JsonArray entries(JsonObject... entries) {
		JsonArray array = new JsonArray();
		if (entries == null) {
			return array;
		}
		for (JsonObject entry : entries) {
			if (entry == null || entry.isEmpty()) {
				continue;
			}
			array.add(entry);
		}
		return array;
	}

	public static JsonObject item(String itemId, int weight, int minCount, int maxCount) {
		JsonObject entry = new JsonObject();
		entry.addProperty(LootTableConfigManager.FIELD_ITEM, itemId == null ? "" : itemId);
		entry.addProperty(LootTableConfigManager.FIELD_WEIGHT, Math.max(1, weight));
		entry.addProperty(LootTableConfigManager.FIELD_MIN_COUNT, minCount);
		entry.addProperty(LootTableConfigManager.FIELD_MAX_COUNT, Math.max(minCount, maxCount));
		return entry;
	}

	public static JsonObject item(String itemId, int weight, int minCount, int maxCount, String itemRarity) {
		JsonObject entry = item(itemId, weight, minCount, maxCount);
		if (itemRarity != null && !itemRarity.isBlank()) {
			entry.addProperty(LootTableConfigManager.FIELD_ITEM_RARITY, itemRarity);
		}
		return entry;
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

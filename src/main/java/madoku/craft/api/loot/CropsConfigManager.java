package madoku.craft.api.loot;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Defaults and builders for crop loot tables managed by the loot-table API. */
public final class CropsConfigManager {
	private CropsConfigManager() {
	}

	public static Map<String, JsonObject> buildDefaultCropTableFiles() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		put(defaults, "minecraft:blocks/potatoes", table("minecraft:blocks/potatoes",
			entryTable(entry("minecraft:potato", 5, 7))));
		put(defaults, "minecraft:blocks/carrots", table("minecraft:blocks/carrots",
			entryTable(entry("minecraft:carrot", 3, 5))));
		put(defaults, "minecraft:blocks/beetroots", table("minecraft:blocks/beetroots",
			entryTable(entry("minecraft:beetroot", 5, 7)),
			entryTable(entry("minecraft:beetroot-seeds", 1, 3))));
		put(defaults, "minecraft:blocks/melon", table("minecraft:blocks/melon",
			entryTable(entry("minecraft:melon-slice", 11, 13)),
			entryTable(entry("minecraft:melon-seeds", 1, 3))));
		put(defaults, "minecraft:blocks/pumpkin", table("minecraft:blocks/pumpkin",
			entryTable(entry("minecraft:pumpkin", 1, 3)),
			entryTable(entry("minecraft:pumpkin-seeds", 1, 3))));
		put(defaults, "minecraft:blocks/wheat", table("minecraft:blocks/wheat",
			entryTable(entry("minecraft:wheat", 7, 9)),
			entryTable(entry("minecraft:wheat-seeds", 1, 3))));
		return defaults;
	}

	private static JsonObject table(String tableId, JsonObject... tableEntries) {
		return MadokuLootTableManager.buildSharedTable(tableId, tableEntries);
	}

	private static JsonObject entryTable(JsonObject... entries) {
		return MadokuLootTableManager.buildSharedTableEntry(1, 1, entries);
	}

	private static JsonObject entry(String itemId, int minCount, int maxCount) {
		return MadokuLootTableManager.buildSharedEntry(itemId, minCount, maxCount);
	}

	private static void put(Map<String, JsonObject> defaults, String tableId, JsonObject table) {
		defaults.put(LootTableConfigManager.fileKeyFromTableId(tableId, "crop"), table);
	}
}

package madoku.craft.core.loot;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EntitiesConfigManager {
	private EntitiesConfigManager() {
	}

	public static void initialize() { }
	public static void reset() { }

	public static JsonObject buildEntityTableTemplate(String tableId) {
		return MadokuLootTableManager.buildSharedTable(
			tableId,
			false,
			MadokuLootTableManager.buildSharedTableEntry(1, 2)
		);
	}

	public static JsonObject buildEntityTable(String tableId, boolean enabled, int minRolls, int maxRolls) {
		return MadokuLootTableManager.buildSharedTable(
			tableId,
			enabled,
			MadokuLootTableManager.buildSharedTableEntry(minRolls, maxRolls)
		);
	}

	public static Map<String, JsonObject> buildDefaultEntityTableFiles() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		put(defaults, "minecraft:entities/bee", buildMobTable("minecraft:entities/bee", 1, 2,
			drop("empty", 149, 0, 0), drop("madoku-craft:bee-pet", 1, 0, 1)));
		put(defaults, "minecraft:entities/bogged", buildMobTable("minecraft:entities/bogged", 1, 2,
			drop("minecraft:bone", 60, 1, 3), drop("minecraft:arrow", 40, 1, 2)));
		put(defaults, "minecraft:entities/parched", buildMobTable("minecraft:entities/parched", 1, 2,
			drop("minecraft:bone", 60, 1, 3), drop("minecraft:arrow", 40, 1, 2)));
		put(defaults, "minecraft:entities/cave_spider", buildMobTable("minecraft:entities/cave_spider", 1, 2,
			drop("minecraft:string", 60, 1, 3), drop("minecraft:spider_eye", 40, 1, 2)));
		put(defaults, "minecraft:entities/creeper", buildMobTable("minecraft:entities/creeper", 1, 2,
			drop("minecraft:gunpowder", 149, 1, 3), drop("madoku-craft:creeper-pet", 1, 0, 1)));
		put(defaults, "minecraft:entities/chicken", buildMobTable("minecraft:entities/chicken", 1, 2,
			drop("minecraft:feather", 60, 1, 3), drop("minecraft:chicken", 89, 1, 2), drop("madoku-craft:chicken-pet", 1, 0, 1)));
		put(defaults, "minecraft:entities/cow", buildMobTable("minecraft:entities/cow", 1, 2,
			drop("minecraft:leather", 60, 1, 3), drop("minecraft:beef", 89, 1, 2), drop("madoku-craft:cow-pet", 1, 0, 1)));
		put(defaults, "minecraft:entities/drowned", buildMobTable("minecraft:entities/drowned", 1, 2,
			drop("minecraft:rotten_flesh", 90, 1, 3), drop("minecraft:copper_ingot", 10, 1, 3)));
		put(defaults, "minecraft:entities/husk", buildMobTable("minecraft:entities/husk", 1, 2,
			drop("minecraft:rotten_flesh", 85, 1, 3), drop("minecraft:charcoal", 15, 2, 4)));
		put(defaults, "minecraft:entities/pig", buildMobTable("minecraft:entities/pig", 1, 2,
			drop("minecraft:porkchop", 149, 2, 4), drop("madoku-craft:pig-pet", 1, 0, 1)));
		put(defaults, "minecraft:entities/skeleton", buildMobTable("minecraft:entities/skeleton", 1, 2,
			drop("minecraft:bone", 89, 1, 3), drop("minecraft:arrow", 60, 1, 2), drop("madoku-craft:skeleton-pet", 1, 0, 1)));
		put(defaults, "minecraft:entities/sheep", buildMobTable("minecraft:entities/sheep", 1, 2,
			drop("minecraft:white_wool", 60, 1, 3), drop("minecraft:mutton", 89, 1, 2), drop("madoku-craft:sheep-pet", 1, 0, 1)));
		put(defaults, "minecraft:entities/spider", buildMobTable("minecraft:entities/spider", 1, 2,
			drop("minecraft:string", 89, 1, 3), drop("minecraft:spider_eye", 60, 1, 2), drop("madoku-craft:spider-pet", 1, 0, 1)));
		put(defaults, "minecraft:entities/stray", buildMobTable("minecraft:entities/stray", 1, 2,
			drop("minecraft:bone", 60, 1, 3), drop("minecraft:arrow", 40, 1, 2)));
		put(defaults, "minecraft:entities/wither_skeleton", buildMobTable("minecraft:entities/wither_skeleton", 1, 2,
			drop("minecraft:bone", 69, 1, 3), drop("minecraft:coal", 30, 2, 4), drop("minecraft:wither_skeleton_skull", 1, 1, 1)));
		put(defaults, "minecraft:entities/zombie", buildMobTable("minecraft:entities/zombie", 1, 2,
			drop("minecraft:rotten_flesh", 140, 1, 3), drop("minecraft:iron_ingot", 9, 1, 2), drop("madoku-craft:zombie-pet", 1, 0, 1)));
		put(defaults, "minecraft:entities/zombie_villager", buildMobTable("minecraft:entities/zombie_villager", 1, 2,
			drop("minecraft:rotten_flesh", 99, 1, 3), drop("minecraft:gold_ingot", 1, 1, 2)));
		return defaults;
	}

	private static JsonObject buildMobTable(
		String tableId,
		int minRolls,
		int maxRolls,
		DropDefinition... drops
	) {
		JsonObject[] entries = new JsonObject[drops == null ? 0 : drops.length];
		for (int index = 0; index < entries.length; index++) {
			DropDefinition drop = drops[index];
			entries[index] = MadokuLootTableManager.buildSharedEntry(
				drop.itemId(), drop.weight(), drop.minCount(), drop.maxCount()
			);
		}
		return MadokuLootTableManager.buildSharedTable(
			tableId,
			MadokuLootTableManager.buildSharedTableEntry(minRolls, maxRolls, entries)
		);
	}

	private static DropDefinition drop(String itemId, int weight, int minCount, int maxCount) {
		return new DropDefinition(itemId, weight, minCount, maxCount);
	}

	private static void put(Map<String, JsonObject> defaults, String tableId, JsonObject root) {
		if (defaults == null || root == null || tableId == null || tableId.isBlank()) {
			return;
		}
		defaults.put(LootTableConfigManager.fileKeyFromTableId(tableId, "entity-table"), root);
	}

	private record DropDefinition(String itemId, int weight, int minCount, int maxCount) { }
}

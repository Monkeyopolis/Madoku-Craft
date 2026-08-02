package madoku.craft.api.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.MadokuJSONManager;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EntitiesConfigManager {
	private EntitiesConfigManager() {
	}

	public static void initialize() { }
	public static void reset() { }

	public static JsonObject buildEntityTableTemplate(String tableId) {
		return JSONFormatManager.object()
			.put(LootTableConfigManager.FIELD_ENABLED, false)
			.put(LootTableConfigManager.FIELD_TABLE_ID, MadokuJSONManager.normalizeRegistryIdentifierForJson(tableId))
			.object(LootTableConfigManager.FIELD_ROLLS, rolls -> rolls
				.put(LootTableConfigManager.FIELD_MIN, 1)
				.put(LootTableConfigManager.FIELD_MAX, 2))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> {
			})
			.build();
	}

	public static JsonObject buildEntityTable(String tableId, boolean enabled, int minRolls, int maxRolls) {
		return JSONFormatManager.object()
			.put(LootTableConfigManager.FIELD_ENABLED, enabled)
			.put(LootTableConfigManager.FIELD_TABLE_ID, MadokuJSONManager.normalizeRegistryIdentifierForJson(tableId))
			.object(LootTableConfigManager.FIELD_ROLLS, rolls -> rolls
				.put(LootTableConfigManager.FIELD_MIN, Math.max(0, minRolls))
				.put(LootTableConfigManager.FIELD_MAX, Math.max(minRolls, maxRolls)))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> {
			})
			.build();
	}

	public static Map<String, JsonObject> buildDefaultEntityTableFiles() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		put(defaults, "minecraft:entities/bee", buildEntityTable("minecraft:entities/bee", false, 1, 2));
		put(defaults, "minecraft:entities/bogged", buildMobTable("minecraft:entities/bogged", 0, 2, "minecraft:bone", 60, "minecraft:arrow", 40, null, null));
		put(defaults, "minecraft:entities/parched", buildMobTable("minecraft:entities/parched", 0, 2, "minecraft:bone", 60, "minecraft:arrow", 40, null, null));
		put(defaults, "minecraft:entities/cave_spider", buildMobTable("minecraft:entities/cave_spider", 1, 2, "minecraft:string", 60, "minecraft:spider_eye", 40, null, null));
		put(defaults, "minecraft:entities/creeper", buildMobTable("minecraft:entities/creeper", 0, 2, "minecraft:gunpowder", 99, "madoku-craft:creeper-pet", 1, "madoku-craft:creeper-pet", "legendary"));
		put(defaults, "minecraft:entities/drowned", buildMobTable("minecraft:entities/drowned", 1, 2, "minecraft:rotten_flesh", 95, "minecraft:copper_ingot", 5, null, null));
		put(defaults, "minecraft:entities/husk", buildMobTable("minecraft:entities/husk", 1, 2, "minecraft:rotten_flesh", 91, "minecraft:charcoal", 9, null, null));
		put(defaults, "minecraft:entities/skeleton", buildMobTable("minecraft:entities/skeleton", 0, 2, "minecraft:bone", 59, "minecraft:arrow", 40, "madoku-craft:skeleton-pet", "legendary"));
		put(defaults, "minecraft:entities/spider", buildMobTable("minecraft:entities/spider", 1, 2, "minecraft:string", 59, "minecraft:spider_eye", 40, "madoku-craft:spider-pet", "legendary"));
		put(defaults, "minecraft:entities/stray", buildMobTable("minecraft:entities/stray", 0, 2, "minecraft:bone", 60, "minecraft:arrow", 40, null, null));
		put(defaults, "minecraft:entities/wither_skeleton", buildMobTable("minecraft:entities/wither_skeleton", 0, 2, "minecraft:bone", 69, "minecraft:coal", 30, "minecraft:wither_skeleton_skull", "mythic"));
		put(defaults, "minecraft:entities/zombie", buildMobTable("minecraft:entities/zombie", 0, 2, "minecraft:rotten_flesh", 94, "minecraft:iron_ingot", 5, "madoku-craft:zombie-pet", "legendary"));
		put(defaults, "minecraft:entities/zombie_villager", buildMobTable("minecraft:entities/zombie_villager", 0, 2, "minecraft:rotten_flesh", 97, "minecraft:gold_ingot", 3, null, null));
		return defaults;
	}

	private static JsonObject buildMobTable(String tableId, int minRolls, int maxRolls, String commonItem, int commonWeight, String rareItem, int rareWeight, String specialItem, String specialRarity) {
		return JSONFormatManager.object()
			.putAll(buildEntityTable(tableId, true, minRolls, maxRolls))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> {
				groups.add(StructuresConfigManager.group("common", commonWeight, StructuresConfigManager.entries(StructuresConfigManager.item(commonItem, 1, 1, 3))));
				groups.add(StructuresConfigManager.group("epic", rareWeight, StructuresConfigManager.entries(StructuresConfigManager.item(rareItem, 1, 0, 2))));
				if (specialItem != null && specialRarity != null) {
					JsonArray specialEntries = StructuresConfigManager.entries(StructuresConfigManager.item(specialItem, 1, 0, 1));
					JsonObject specialGroup = specialItem.endsWith("-pet")
						? StructuresConfigManager.group(specialRarity, 1, java.util.List.of("madoku-pets"), specialEntries)
						: StructuresConfigManager.group(specialRarity, 1, specialEntries);
					groups.add(specialGroup);
				}
			})
			.build();
	}

	private static void put(Map<String, JsonObject> defaults, String tableId, JsonObject root) {
		if (defaults == null || root == null || tableId == null || tableId.isBlank()) {
			return;
		}
		defaults.put(LootTableConfigManager.fileKeyFromTableId(tableId, "entity-table"), root);
	}
}


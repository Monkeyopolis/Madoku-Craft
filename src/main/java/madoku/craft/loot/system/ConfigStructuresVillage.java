package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class ConfigStructuresVillage {
	private static final String TABLE_ID = "minecraft:structure_chests/village";

	private ConfigStructuresVillage() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = LootTableConfigStructures.buildStructureTable(TABLE_ID, 3, 7);
		JsonArray groups = new JsonArray();

		groups.add(LootTableConfigStructures.group("common", 100, LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:bread", 1, 2, 6),
				LootTableConfigStructures.item("minecraft:baked_potato", 1, 2, 6)
			)));

		groups.add(LootTableConfigStructures.group("rare", 75, LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:emerald", 1, 3, 5)
			)));

		groups.add(LootTableConfigStructures.group("epic", 50, LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:coal", 42, 5, 7),
				LootTableConfigStructures.item("minecraft:copper_ingot", 27, 4, 6),
				LootTableConfigStructures.item("minecraft:iron_ingot", 17, 3, 5),
				LootTableConfigStructures.item("minecraft:gold_ingot", 10, 2, 4),
				LootTableConfigStructures.item("minecraft:diamond", 3, 1, 3),
				LootTableConfigStructures.item("minecraft:netherite_scrap", 1, 0, 2)
			)));

		root.add(LootTableConfigManager.FIELD_GROUPS, groups);
		return root;
	}
}


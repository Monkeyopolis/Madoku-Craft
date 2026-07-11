package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;

public final class ConfigStructuresStarterChest {
	private static final String TABLE_ID = "minecraft:structure_chests/starter_chest";

	private ConfigStructuresStarterChest() {
	}

	public static JsonObject buildDefaults() {
		JsonArray groups = JSONFormatManager.array()
			.add(LootTableConfigStructures.group("common", 100, LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:potato", 1, 2, 6),
				LootTableConfigStructures.item("minecraft:carrot", 1, 2, 6)
			)))
			.add(LootTableConfigStructures.group("common", 40, LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:stone_axe", 1, 1, 1, "mythic"),
				LootTableConfigStructures.item("minecraft:stone_pickaxe", 1, 1, 1, "mythic"),
				LootTableConfigStructures.item("minecraft:stone_sword", 1, 1, 1, "mythic"),
				LootTableConfigStructures.item("minecraft:stone_spear", 1, 1, 1, "mythic"),
				LootTableConfigStructures.item("minecraft:stone_shovel", 1, 1, 1, "mythic"),
				LootTableConfigStructures.item("minecraft:leather_helmet", 1, 1, 1, "mythic"),
				LootTableConfigStructures.item("minecraft:leather_chestplate", 1, 1, 1, "mythic"),
				LootTableConfigStructures.item("minecraft:leather_leggings", 1, 1, 1, "mythic"),
				LootTableConfigStructures.item("minecraft:leather_boots", 1, 1, 1, "mythic")
			)))
			.add(LootTableConfigStructures.group("rare", 75, LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:baked_potato", 1, 2, 6),
				LootTableConfigStructures.item("minecraft:bread", 1, 2, 6)
			)))
			.add(LootTableConfigStructures.group("rare", 30, LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:copper_axe", 1, 1, 1, "epic"),
				LootTableConfigStructures.item("minecraft:copper_pickaxe", 1, 1, 1, "epic"),
				LootTableConfigStructures.item("minecraft:copper_sword", 1, 1, 1, "epic"),
				LootTableConfigStructures.item("minecraft:copper_spear", 1, 1, 1, "epic"),
				LootTableConfigStructures.item("minecraft:copper_shovel", 1, 1, 1, "epic"),
				LootTableConfigStructures.item("minecraft:copper_helmet", 1, 1, 1, "epic"),
				LootTableConfigStructures.item("minecraft:copper_chestplate", 1, 1, 1, "epic"),
				LootTableConfigStructures.item("minecraft:copper_leggings", 1, 1, 1, "epic"),
				LootTableConfigStructures.item("minecraft:copper_boots", 1, 1, 1, "epic")
			)))
			.build();

		return JSONFormatManager.object()
			.putAll(LootTableConfigStructures.buildStructureTable(TABLE_ID, 7, 11))
			.put(LootTableConfigManager.FIELD_GROUPS, groups)
			.build();
	}
}



package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;
import java.util.List;

public final class ConfigStructuresShipwreck {
	private static final String TABLE_ID = "minecraft:structure_chests/shipwreck";

	private ConfigStructuresShipwreck() {
	}

	public static JsonObject buildDefaults() {
		JsonArray groups = JSONFormatManager.array()
			.add(LootTableConfigStructures.group("common", 100, LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:bread", 1, 2, 6),
				LootTableConfigStructures.item("minecraft:baked_potato", 1, 2, 6)
			)))
			.add(LootTableConfigStructures.group("rare", 75, LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:music_disc_13", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_cat", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_blocks", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_chirp", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_far", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_mall", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_mellohi", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_stal", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_strad", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_ward", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_11", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_wait", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_otherside", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_5", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_pigstep", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_relic", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_creator", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_creator_music_box", 1, 1, 1),
				LootTableConfigStructures.item("minecraft:music_disc_precipice", 1, 1, 1)
			)))
			.add(LootTableConfigStructures.group("epic", 50, LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:coal", 42, 5, 7),
				LootTableConfigStructures.item("minecraft:copper_ingot", 27, 4, 6),
				LootTableConfigStructures.item("minecraft:iron_ingot", 17, 3, 5),
				LootTableConfigStructures.item("minecraft:gold_ingot", 10, 2, 4),
				LootTableConfigStructures.item("minecraft:diamond", 3, 1, 3),
				LootTableConfigStructures.item("minecraft:netherite_scrap", 1, 0, 2)
			)))
			.add(LootTableConfigStructures.group("epic", 20, LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:heart_of_the_sea", 1, 1, 3)
			)))
			.add(LootTableConfigStructures.group("mythic", 25, List.of("madoku-pets"), LootTableConfigStructures.entries(
				LootTableConfigStructures.item("madoku:chicken-pet", 100, 1, 1),
				LootTableConfigStructures.item("madoku:zombie-pet", 100, 1, 1),
				LootTableConfigStructures.item("madoku:pig-pet", 100, 1, 1),
				LootTableConfigStructures.item("madoku:sheep-pet", 100, 1, 1),
				LootTableConfigStructures.item("madoku:cow-pet", 75, 1, 1),
				LootTableConfigStructures.item("madoku:skeleton-pet", 75, 1, 1),
				LootTableConfigStructures.item("madoku:spider-pet", 75, 1, 1),
				LootTableConfigStructures.item("madoku:creeper-pet", 75, 1, 1),
				LootTableConfigStructures.item("madoku:bat-pet", 50, 1, 1),
				LootTableConfigStructures.item("madoku:bee-pet", 50, 1, 1)
			)))
			.build();

		return JSONFormatManager.object()
			.putAll(LootTableConfigStructures.buildStructureTable(TABLE_ID, 7, 11))
			.put(LootTableConfigManager.FIELD_GROUPS, groups)
			.build();
	}
}



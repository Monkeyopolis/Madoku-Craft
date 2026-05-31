package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

public final class ConfigStructuresDesertPyramid {
	private static final String TABLE_ID = "minecraft:structure_chests/desert_pyramid";

	private ConfigStructuresDesertPyramid() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = LootTableConfigStructures.buildStructureTable(TABLE_ID, 3, 7);
		JsonArray groups = new JsonArray();

		groups.add(LootTableConfigStructures.group("common", 100, LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:bread", 1, 2, 6),
				LootTableConfigStructures.item("minecraft:baked_potato", 1, 2, 6)
			)));

		groups.add(LootTableConfigStructures.group("rare", 75, LootTableConfigStructures.entries(
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
			)));

		groups.add(LootTableConfigStructures.group("epic", 50, LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:coal", 42, 5, 7),
				LootTableConfigStructures.item("minecraft:copper_ingot", 27, 4, 6),
				LootTableConfigStructures.item("minecraft:iron_ingot", 17, 3, 5),
				LootTableConfigStructures.item("minecraft:gold_ingot", 10, 2, 4),
				LootTableConfigStructures.item("minecraft:diamond", 3, 1, 3),
				LootTableConfigStructures.item("minecraft:netherite_scrap", 1, 0, 2)
			)));

		groups.add(LootTableConfigStructures.group("mythic", 25, List.of("madoku-pets"), LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:chicken_spawn_egg", 100, 1, 1),
				LootTableConfigStructures.item("minecraft:zombie_spawn_egg", 100, 1, 1),
				LootTableConfigStructures.item("minecraft:pig_spawn_egg", 100, 1, 1),
				LootTableConfigStructures.item("minecraft:sheep_spawn_egg", 100, 1, 1),
				LootTableConfigStructures.item("minecraft:cow_spawn_egg", 75, 1, 1),
				LootTableConfigStructures.item("minecraft:skeleton_spawn_egg", 75, 1, 1),
				LootTableConfigStructures.item("minecraft:spider_spawn_egg", 75, 1, 1),
				LootTableConfigStructures.item("minecraft:creeper_spawn_egg", 75, 1, 1),
				LootTableConfigStructures.item("minecraft:bat_spawn_egg", 50, 1, 1),
				LootTableConfigStructures.item("minecraft:bee_spawn_egg", 50, 1, 1)
			)));

		root.add(LootTableConfigManager.FIELD_GROUPS, groups);
		return root;
	}
}


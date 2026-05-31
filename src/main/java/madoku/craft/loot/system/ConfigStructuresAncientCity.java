package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class ConfigStructuresAncientCity {
	private static final String TABLE_ID = "minecraft:structure_chests/ancient_city";

	private ConfigStructuresAncientCity() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = LootTableConfigStructures.buildStructureTable(TABLE_ID, 3, 7);
		JsonArray groups = new JsonArray();

		groups.add(LootTableConfigStructures.group("common", 100, LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:bread", 2, 2, 6),
				LootTableConfigStructures.item("minecraft:baked_potato", 2, 2, 6),
				LootTableConfigStructures.item("minecraft:echo_shard", 1, 1, 3)
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

		groups.add(LootTableConfigStructures.group("mythic", 25, LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:diamond_sword", 100, 1, 1, "epic"),
				LootTableConfigStructures.item("minecraft:diamond_spear", 100, 1, 1, "epic"),
				LootTableConfigStructures.item("minecraft:diamond_helmet", 100, 1, 1, "epic"),
				LootTableConfigStructures.item("minecraft:diamond_chestplate", 100, 1, 1, "epic"),
				LootTableConfigStructures.item("minecraft:diamond_leggings", 100, 1, 1, "epic"),
				LootTableConfigStructures.item("minecraft:diamond_boots", 100, 1, 1, "epic"),
				LootTableConfigStructures.item("minecraft:diamond_pickaxe", 100, 1, 1, "epic"),
				LootTableConfigStructures.item("minecraft:diamond_axe", 100, 1, 1, "epic"),
				LootTableConfigStructures.item("minecraft:diamond_shovel", 100, 1, 1, "epic")
			)));

		groups.add(LootTableConfigStructures.group("mythic", 10, LootTableConfigStructures.entries(
				LootTableConfigStructures.item("minecraft:diamond_sword", 100, 1, 1, "mythic"),
				LootTableConfigStructures.item("minecraft:diamond_spear", 100, 1, 1, "mythic"),
				LootTableConfigStructures.item("minecraft:diamond_helmet", 100, 1, 1, "mythic"),
				LootTableConfigStructures.item("minecraft:diamond_chestplate", 100, 1, 1, "mythic"),
				LootTableConfigStructures.item("minecraft:diamond_leggings", 100, 1, 1, "mythic"),
				LootTableConfigStructures.item("minecraft:diamond_boots", 100, 1, 1, "mythic"),
				LootTableConfigStructures.item("minecraft:diamond_pickaxe", 100, 1, 1, "mythic"),
				LootTableConfigStructures.item("minecraft:diamond_axe", 100, 1, 1, "mythic"),
				LootTableConfigStructures.item("minecraft:diamond_shovel", 100, 1, 1, "mythic")
			)));

		root.add(LootTableConfigManager.FIELD_GROUPS, groups);
		return root;
	}
}


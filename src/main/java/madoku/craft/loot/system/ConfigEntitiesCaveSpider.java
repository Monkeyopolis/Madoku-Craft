package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class ConfigEntitiesCaveSpider {
	private static final String TABLE_ID = "minecraft:entities/cave_spider";

	private ConfigEntitiesCaveSpider() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = LootTableConfigEntities.buildEntityTable(TABLE_ID, true, 1, 2);
		JsonArray groups = new JsonArray();

		groups.add(
			LootTableConfigStructures.group(
				"common",
				60,
				LootTableConfigStructures.entries(
					LootTableConfigStructures.item("minecraft:string", 1, 1, 3)
				)
			)
		);

		groups.add(
			LootTableConfigStructures.group(
				"epic",
				40,
				LootTableConfigStructures.entries(
					LootTableConfigStructures.item("minecraft:spider_eye", 1, 1, 3)
				)
			)
		);

		root.add(LootTableConfigManager.FIELD_GROUPS, groups);
		return root;
	}
}


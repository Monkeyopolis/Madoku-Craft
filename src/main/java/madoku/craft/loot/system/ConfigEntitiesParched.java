package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class ConfigEntitiesParched {
	private static final String TABLE_ID = "minecraft:entities/parched";

	private ConfigEntitiesParched() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = LootTableConfigEntities.buildEntityTable(TABLE_ID, true, 0, 2);
		JsonArray groups = new JsonArray();

		groups.add(
			LootTableConfigStructures.group(
				"common",
				60,
				LootTableConfigStructures.entries(
					LootTableConfigStructures.item("minecraft:bone", 1, 1, 3)
				)
			)
		);

		groups.add(
			LootTableConfigStructures.group(
				"epic",
				40,
				LootTableConfigStructures.entries(
					LootTableConfigStructures.item("minecraft:arrow", 1, 0, 2)
				)
			)
		);

		root.add(LootTableConfigManager.FIELD_GROUPS, groups);
		return root;
	}
}

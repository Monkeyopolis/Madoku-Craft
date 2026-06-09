package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class ConfigEntitiesStray {
	private static final String TABLE_ID = "minecraft:entities/stray";

	private ConfigEntitiesStray() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		JsonObject general = new JsonObject();
		general.addProperty("version", "1.1.7");
		general.addProperty("type", "dynamic");
		general.addProperty(LootTableConfigManager.FIELD_ENABLED, true);
		root.add("general", general);

		JsonObject main = new JsonObject();
		main.addProperty(LootTableConfigManager.FIELD_TABLE_ID, TABLE_ID);
		JsonObject rolls = new JsonObject();
		rolls.addProperty(LootTableConfigManager.FIELD_MIN, 0);
		rolls.addProperty(LootTableConfigManager.FIELD_MAX, 2);
		main.add(LootTableConfigManager.FIELD_ROLLS, rolls);

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
		main.add(LootTableConfigManager.FIELD_GROUPS, groups);
		root.add("main", main);
		return root;
	}
}


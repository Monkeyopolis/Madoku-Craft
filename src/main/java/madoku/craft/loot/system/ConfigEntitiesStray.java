package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class ConfigEntitiesStray {
	private static final String TABLE_ID = "minecraft:entities/stray";

	private ConfigEntitiesStray() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = LootTableConfigEntities.buildEntityTable(TABLE_ID, false, 1, 2);
		root.add(LootTableConfigManager.FIELD_GROUPS, new JsonArray());
		return root;
	}
}


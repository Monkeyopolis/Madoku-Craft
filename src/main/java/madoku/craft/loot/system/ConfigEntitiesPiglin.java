package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class ConfigEntitiesPiglin {
	private static final String TABLE_ID = "minecraft:entities/piglin";

	private ConfigEntitiesPiglin() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = LootTableConfigEntities.buildEntityTable(TABLE_ID, false, 1, 2);
		root.add(LootTableConfigManager.FIELD_GROUPS, new JsonArray());
		return root;
	}
}


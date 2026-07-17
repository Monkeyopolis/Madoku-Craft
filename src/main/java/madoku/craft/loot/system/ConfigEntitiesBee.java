package madoku.craft.loot.system;

import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;

public final class ConfigEntitiesBee {
	private static final String TABLE_ID = "minecraft:entities/bee";

	private ConfigEntitiesBee() {
	}

	public static JsonObject buildDefaults() {
		return JSONFormatManager.object()
			.putAll(LootTableConfigEntities.buildEntityTable(TABLE_ID, false, 1, 2))
			.build();
	}
}



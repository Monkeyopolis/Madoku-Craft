package madoku.craft.loot.system;

import com.google.gson.JsonObject;
import madoku.craft.config.JsonFormatBuilder;

public final class ConfigEntitiesBee {
	private static final String TABLE_ID = "minecraft:entities/bee";

	private ConfigEntitiesBee() {
	}

	public static JsonObject buildDefaults() {
		return JsonFormatBuilder.object()
			.putAll(LootTableConfigEntities.buildEntityTable(TABLE_ID, false, 1, 2))
			.build();
	}
}



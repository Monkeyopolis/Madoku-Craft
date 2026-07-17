package madoku.craft.loot.system;

import com.google.gson.JsonObject;

public final class ConfigEntitiesBogged {
	private static final String TABLE_ID = "minecraft:entities/bogged";

	private ConfigEntitiesBogged() {
	}

	public static JsonObject buildDefaults() {
		return madoku.craft.api.json.JSONFormatManager.object()
			.put(LootTableConfigManager.FIELD_ENABLED, true)
			.put(LootTableConfigManager.FIELD_TABLE_ID, TABLE_ID)
			.object(LootTableConfigManager.FIELD_ROLLS, rolls -> rolls
				.put(LootTableConfigManager.FIELD_MIN, 0)
				.put(LootTableConfigManager.FIELD_MAX, 2))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.object(commonGroup -> commonGroup
					.put(LootTableConfigManager.FIELD_RARITY, "common")
					.put(LootTableConfigManager.FIELD_WEIGHT, 60)
					.put(LootTableConfigManager.FIELD_ENTRIES, LootTableConfigStructures.entries(
						LootTableConfigStructures.item("minecraft:bone", 1, 1, 3)
					)))
				.object(epicGroup -> epicGroup
					.put(LootTableConfigManager.FIELD_RARITY, "epic")
					.put(LootTableConfigManager.FIELD_WEIGHT, 40)
					.put(LootTableConfigManager.FIELD_ENTRIES, LootTableConfigStructures.entries(
						LootTableConfigStructures.item("minecraft:arrow", 1, 0, 2)
					))))
			.build();
	}
}



package madoku.craft.loot.system;

import com.google.gson.JsonObject;

public final class ConfigEntitiesHusk {
	private static final String TABLE_ID = "minecraft:entities/husk";

	private ConfigEntitiesHusk() {
	}

	public static JsonObject buildDefaults() {
		return madoku.craft.config.JsonFormatBuilder.object()
			.put(LootTableConfigManager.FIELD_ENABLED, true)
			.put(LootTableConfigManager.FIELD_TABLE_ID, TABLE_ID)
			.object(LootTableConfigManager.FIELD_ROLLS, rolls -> rolls
				.put(LootTableConfigManager.FIELD_MIN, 1)
				.put(LootTableConfigManager.FIELD_MAX, 2))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.object(commonGroup -> commonGroup
					.put(LootTableConfigManager.FIELD_RARITY, "common")
					.put(LootTableConfigManager.FIELD_WEIGHT, 91)
					.put(LootTableConfigManager.FIELD_ENTRIES, LootTableConfigStructures.entries(
						LootTableConfigStructures.item("minecraft:rotten_flesh", 1, 1, 3)
					)))
				.object(epicGroup -> epicGroup
					.put(LootTableConfigManager.FIELD_RARITY, "epic")
					.put(LootTableConfigManager.FIELD_WEIGHT, 9)
					.put(LootTableConfigManager.FIELD_ENTRIES, LootTableConfigStructures.entries(
						LootTableConfigStructures.item("minecraft:charcoal", 1, 1, 3)
					))))
			.build();
	}
}


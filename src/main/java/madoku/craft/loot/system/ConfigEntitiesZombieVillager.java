package madoku.craft.loot.system;

import com.google.gson.JsonObject;

public final class ConfigEntitiesZombieVillager {
	private static final String TABLE_ID = "minecraft:entities/zombie_villager";

	private ConfigEntitiesZombieVillager() {
	}

	public static JsonObject buildDefaults() {
		return madoku.craft.config.JsonFormatBuilder.object()
			.put(LootTableConfigManager.FIELD_ENABLED, true)
			.put(LootTableConfigManager.FIELD_TABLE_ID, TABLE_ID)
			.object(LootTableConfigManager.FIELD_ROLLS, rolls -> rolls
				.put(LootTableConfigManager.FIELD_MIN, 0)
				.put(LootTableConfigManager.FIELD_MAX, 2))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.object(commonGroup -> commonGroup
					.put(LootTableConfigManager.FIELD_RARITY, "common")
					.put(LootTableConfigManager.FIELD_WEIGHT, 97)
					.put(LootTableConfigManager.FIELD_ENTRIES, LootTableConfigStructures.entries(
						LootTableConfigStructures.item("minecraft:rotten_flesh", 1, 1, 3)
					)))
				.object(epicGroup -> epicGroup
					.put(LootTableConfigManager.FIELD_RARITY, "epic")
					.put(LootTableConfigManager.FIELD_WEIGHT, 3)
					.put(LootTableConfigManager.FIELD_ENTRIES, LootTableConfigStructures.entries(
						LootTableConfigStructures.item("minecraft:gold_ingot", 1, 0, 2)
					))))
			.build();
	}
}


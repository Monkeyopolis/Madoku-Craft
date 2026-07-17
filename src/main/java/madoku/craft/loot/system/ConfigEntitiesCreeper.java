package madoku.craft.loot.system;

import com.google.gson.JsonObject;

public final class ConfigEntitiesCreeper {
	private ConfigEntitiesCreeper() {
	}

	public static JsonObject buildDefaults() {
		return madoku.craft.api.json.JSONFormatManager.object()
			.object("general", general -> general
				.put("version", "1.1.7")
				.put("type", "dynamic")
				.put(LootTableConfigManager.FIELD_ENABLED, true))
			.object("main", main -> main
				.put(LootTableConfigManager.FIELD_TABLE_ID, "minecraft:entities/creeper")
				.object(LootTableConfigManager.FIELD_ROLLS, rolls -> rolls
					.put(LootTableConfigManager.FIELD_MIN, 0)
					.put(LootTableConfigManager.FIELD_MAX, 2))
				.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
					.object(commonGroup -> commonGroup
						.put(LootTableConfigManager.FIELD_RARITY, "common")
						.put(LootTableConfigManager.FIELD_WEIGHT, 99)
						.put(LootTableConfigManager.FIELD_ENTRIES, LootTableConfigStructures.entries(
							LootTableConfigStructures.item("minecraft:gunpowder", 1, 1, 3)
						)))
					.object(mythicGroup -> mythicGroup
						.put(LootTableConfigManager.FIELD_RARITY, "mythic")
						.put(LootTableConfigManager.FIELD_WEIGHT, 1)
						.put(LootTableConfigManager.FIELD_ENTRIES, LootTableConfigStructures.entries(
							LootTableConfigStructures.item("minecraft:creeper_spawn_egg", 1, 0, 1)
						))
						.put(LootTableConfigManager.FIELD_TAGS, madoku.craft.api.json.JSONFormatManager.array().add("madoku-pets").build()))))
			.build();
	}
}


package madoku.craft.loot.system;

import com.google.gson.JsonObject;
import madoku.craft.config.JsonFormatBuilder;

public final class ConfigEntitiesParched {
	private static final String TABLE_ID = "minecraft:entities/parched";

	private ConfigEntitiesParched() {
	}

	public static JsonObject buildDefaults() {
		return JsonFormatBuilder.object()
			.putAll(LootTableConfigEntities.buildEntityTable(TABLE_ID, true, 0, 2))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(LootTableConfigStructures.group(
					"common",
					60,
					LootTableConfigStructures.entries(
						LootTableConfigStructures.item("minecraft:bone", 1, 1, 3)
					)
				))
				.add(LootTableConfigStructures.group(
					"epic",
					40,
					LootTableConfigStructures.entries(
						LootTableConfigStructures.item("minecraft:arrow", 1, 0, 2)
					)
				)))
			.build();
	}
}

package madoku.craft.loot.system;

import com.google.gson.JsonObject;
import madoku.craft.config.JsonFormatBuilder;

public final class ConfigEntitiesCaveSpider {
	private static final String TABLE_ID = "minecraft:entities/cave_spider";

	private ConfigEntitiesCaveSpider() {
	}

	public static JsonObject buildDefaults() {
		return JsonFormatBuilder.object()
			.putAll(LootTableConfigEntities.buildEntityTable(TABLE_ID, true, 1, 2))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(LootTableConfigStructures.group(
					"common",
					60,
					LootTableConfigStructures.entries(
						LootTableConfigStructures.item("minecraft:string", 1, 1, 3)
					)
				))
				.add(LootTableConfigStructures.group(
					"epic",
					40,
					LootTableConfigStructures.entries(
						LootTableConfigStructures.item("minecraft:spider_eye", 1, 1, 3)
					)
				)))
			.build();
	}
}


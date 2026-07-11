package madoku.craft.loot.system;

import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;

public final class ConfigEntitiesSpider {
	private static final String TABLE_ID = "minecraft:entities/spider";

	private ConfigEntitiesSpider() {
	}

	public static JsonObject buildDefaults() {
		return JSONFormatManager.object()
			.putAll(LootTableConfigEntities.buildEntityTable(TABLE_ID, true, 1, 2))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> groups
				.add(LootTableConfigStructures.group(
					"common",
					59,
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
				))
				.add(LootTableConfigStructures.group(
					"mythic",
					1,
					java.util.List.of("madoku-pets"),
					LootTableConfigStructures.entries(
						LootTableConfigStructures.item("minecraft:spider_spawn_egg", 1, 0, 1)
					)
				)))
			.build();
	}
}


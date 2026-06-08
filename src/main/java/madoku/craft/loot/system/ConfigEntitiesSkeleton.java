package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

public final class ConfigEntitiesSkeleton {
	private static final String TABLE_ID = "minecraft:entities/skeleton";

	private ConfigEntitiesSkeleton() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = LootTableConfigEntities.buildEntityTable(TABLE_ID, true, 0, 2);
		JsonArray groups = new JsonArray();

		groups.add(
			LootTableConfigStructures.group(
				"common",
				59,
				LootTableConfigStructures.entries(
					LootTableConfigStructures.item("minecraft:bone", 1, 1, 3)
				)
			)
		);

		groups.add(
			LootTableConfigStructures.group(
				"epic",
				40,
				LootTableConfigStructures.entries(
					LootTableConfigStructures.item("minecraft:arrow", 1, 0, 2)
				)
			)
		);

		groups.add(
			LootTableConfigStructures.group(
				"mythic",
				1,
				List.of("madoku-pets"),
				LootTableConfigStructures.entries(
					LootTableConfigStructures.item("minecraft:skeleton_spawn_egg", 1, 0, 1)
				)
			)
		);

		root.add(LootTableConfigManager.FIELD_GROUPS, groups);
		return root;
	}
}


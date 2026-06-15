package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class ConfigEntitiesWitherSkeleton {
	private static final String TABLE_ID = "minecraft:entities/wither_skeleton";

	private ConfigEntitiesWitherSkeleton() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = LootTableConfigEntities.buildEntityTable(TABLE_ID, true, 0, 2);
		JsonArray groups = new JsonArray();

		groups.add(
			LootTableConfigStructures.group(
				"common",
				69,
				LootTableConfigStructures.entries(
					LootTableConfigStructures.item("minecraft:bone", 1, 1, 3)
				)
			)
		);

		groups.add(
			LootTableConfigStructures.group(
				"epic",
				30,
				LootTableConfigStructures.entries(
					LootTableConfigStructures.item("minecraft:coal", 1, 1, 3)
				)
			)
		);

		groups.add(
			LootTableConfigStructures.group(
				"mythic",
				1,
				LootTableConfigStructures.entries(
					LootTableConfigStructures.item("minecraft:wither_skeleton_skull", 1, 0, 2)
				)
			)
		);

		root.add(LootTableConfigManager.FIELD_GROUPS, groups);
		return root;
	}
}


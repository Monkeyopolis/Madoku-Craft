package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class ConfigEntitiesZombie {
	private static final String TABLE_ID = "minecraft:entities/zombie";

	private ConfigEntitiesZombie() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = LootTableConfigEntities.buildEntityTable(TABLE_ID, true, 0, 2);
		JsonArray groups = new JsonArray();

		JsonObject commonGroup = new JsonObject();
		commonGroup.addProperty(LootTableConfigManager.FIELD_RARITY, "common");
		commonGroup.addProperty(LootTableConfigManager.FIELD_WEIGHT, 94);
		JsonArray commonEntries = new JsonArray();
		commonEntries.add(buildEntry("minecraft:rotten_flesh", 1, 1, 3));
		commonGroup.add(LootTableConfigManager.FIELD_ENTRIES, commonEntries);
		groups.add(commonGroup);

		JsonObject epicGroup = new JsonObject();
		epicGroup.addProperty(LootTableConfigManager.FIELD_RARITY, "epic");
		epicGroup.addProperty(LootTableConfigManager.FIELD_WEIGHT, 5);
		JsonArray epicEntries = new JsonArray();
		epicEntries.add(buildEntry("minecraft:iron_ingot", 1, 0, 2));
		epicGroup.add(LootTableConfigManager.FIELD_ENTRIES, epicEntries);
		groups.add(epicGroup);

		JsonObject mythicGroup = new JsonObject();
		mythicGroup.addProperty(LootTableConfigManager.FIELD_RARITY, "mythic");
		mythicGroup.addProperty(LootTableConfigManager.FIELD_WEIGHT, 1);
		JsonArray mythicEntries = new JsonArray();
		mythicEntries.add(buildEntry("minecraft:zombie_spawn_egg", 1, 0, 1));
		mythicGroup.add(LootTableConfigManager.FIELD_ENTRIES, mythicEntries);
		JsonArray mythicTags = new JsonArray();
		mythicTags.add("madoku-pets");
		mythicGroup.add(LootTableConfigManager.FIELD_TAGS, mythicTags);
		groups.add(mythicGroup);

		root.add(LootTableConfigManager.FIELD_GROUPS, groups);
		return root;
	}

	private static JsonObject buildEntry(String itemId, int weight, int minCount, int maxCount) {
		JsonObject entry = new JsonObject();
		entry.addProperty(LootTableConfigManager.FIELD_ITEM, itemId);
		entry.addProperty(LootTableConfigManager.FIELD_WEIGHT, weight);
		entry.addProperty(LootTableConfigManager.FIELD_MIN_COUNT, minCount);
		entry.addProperty(LootTableConfigManager.FIELD_MAX_COUNT, maxCount);
		return entry;
	}
}


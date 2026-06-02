package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class ConfigEntitiesZombieVillager {
	private static final String TABLE_ID = "minecraft:entities/zombie_villager";

	private ConfigEntitiesZombieVillager() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = LootTableConfigEntities.buildEntityTable(TABLE_ID, true, 0, 2);
		JsonArray groups = new JsonArray();

		JsonObject commonGroup = new JsonObject();
		commonGroup.addProperty(LootTableConfigManager.FIELD_RARITY, "common");
		commonGroup.addProperty(LootTableConfigManager.FIELD_WEIGHT, 97);
		JsonArray commonEntries = new JsonArray();
		commonEntries.add(buildEntry("minecraft:rotten_flesh", 1, 1, 3));
		commonGroup.add(LootTableConfigManager.FIELD_ENTRIES, commonEntries);
		groups.add(commonGroup);

		JsonObject epicGroup = new JsonObject();
		epicGroup.addProperty(LootTableConfigManager.FIELD_RARITY, "epic");
		epicGroup.addProperty(LootTableConfigManager.FIELD_WEIGHT, 3);
		JsonArray epicEntries = new JsonArray();
		epicEntries.add(buildEntry("minecraft:gold_ingot", 1, 0, 2));
		epicGroup.add(LootTableConfigManager.FIELD_ENTRIES, epicEntries);
		groups.add(epicGroup);

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

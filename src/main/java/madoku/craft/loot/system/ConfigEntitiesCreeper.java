package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class ConfigEntitiesCreeper {
	private ConfigEntitiesCreeper() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();

		JsonObject general = new JsonObject();
		general.addProperty("version", "1.1.7");
		general.addProperty("type", "dynamic");
		general.addProperty(LootTableConfigManager.FIELD_ENABLED, true);
		root.add("general", general);

		JsonObject main = new JsonObject();
		main.addProperty(LootTableConfigManager.FIELD_TABLE_ID, "minecraft:entities/creeper");

		JsonObject rolls = new JsonObject();
		rolls.addProperty(LootTableConfigManager.FIELD_MIN, 0);
		rolls.addProperty(LootTableConfigManager.FIELD_MAX, 2);
		main.add(LootTableConfigManager.FIELD_ROLLS, rolls);

		JsonArray groups = new JsonArray();

		JsonObject commonGroup = new JsonObject();
		commonGroup.addProperty(LootTableConfigManager.FIELD_RARITY, "common");
		commonGroup.addProperty(LootTableConfigManager.FIELD_WEIGHT, 99);
		JsonArray commonEntries = new JsonArray();
		JsonObject gunpowderEntry = new JsonObject();
		gunpowderEntry.addProperty(LootTableConfigManager.FIELD_ITEM, "minecraft:gunpowder");
		gunpowderEntry.addProperty(LootTableConfigManager.FIELD_WEIGHT, 1);
		gunpowderEntry.addProperty(LootTableConfigManager.FIELD_MIN_COUNT, 1);
		gunpowderEntry.addProperty(LootTableConfigManager.FIELD_MAX_COUNT, 3);
		commonEntries.add(gunpowderEntry);
		commonGroup.add(LootTableConfigManager.FIELD_ENTRIES, commonEntries);
		groups.add(commonGroup);

		JsonObject mythicGroup = new JsonObject();
		mythicGroup.addProperty(LootTableConfigManager.FIELD_RARITY, "mythic");
		mythicGroup.addProperty(LootTableConfigManager.FIELD_WEIGHT, 1);
		JsonArray mythicEntries = new JsonArray();
		JsonObject spawnEggEntry = new JsonObject();
		spawnEggEntry.addProperty(LootTableConfigManager.FIELD_ITEM, "minecraft:creeper_spawn_egg");
		spawnEggEntry.addProperty(LootTableConfigManager.FIELD_WEIGHT, 1);
		spawnEggEntry.addProperty(LootTableConfigManager.FIELD_MIN_COUNT, 0);
		spawnEggEntry.addProperty(LootTableConfigManager.FIELD_MAX_COUNT, 1);
		mythicEntries.add(spawnEggEntry);
		mythicGroup.add(LootTableConfigManager.FIELD_ENTRIES, mythicEntries);
		JsonArray tags = new JsonArray();
		tags.add("madoku-pets");
		mythicGroup.add(LootTableConfigManager.FIELD_TAGS, tags);
		groups.add(mythicGroup);

		main.add(LootTableConfigManager.FIELD_GROUPS, groups);
		root.add("main", main);
		return root;
	}
}

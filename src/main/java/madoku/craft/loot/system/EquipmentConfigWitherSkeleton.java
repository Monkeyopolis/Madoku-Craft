package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class EquipmentConfigWitherSkeleton {
	private EquipmentConfigWitherSkeleton() {
	}

	public static JsonObject buildDefaults() {
		JsonObject root = new JsonObject();
		JsonObject general = new JsonObject();
		general.addProperty("version", "1.1.7");
		general.addProperty("type", "dynamic");
		general.addProperty(LootTableEquipmentsConfig.FIELD_ENABLED, true);
		root.add("general", general);

		JsonObject main = new JsonObject();
		main.addProperty(LootTableEquipmentsConfig.FIELD_MOB_ID, "minecraft:wither_skeleton");

		JsonObject armorSet = new JsonObject();
		armorSet.addProperty(LootTableEquipmentsConfig.FIELD_PARTIAL_SET, 60.0D);
		armorSet.addProperty(LootTableEquipmentsConfig.FIELD_HALF_SET, 30.0D);
		armorSet.addProperty(LootTableEquipmentsConfig.FIELD_FULL_SET, 10.0D);
		main.add(LootTableEquipmentsConfig.FIELD_ARMOR_SET, armorSet);

		main.add(LootTableEquipmentsConfig.FIELD_HELMET, buildDefaultSlotEntries("helmet"));
		main.add(LootTableEquipmentsConfig.FIELD_CHESTPLATE, buildDefaultSlotEntries("chestplate"));
		main.add(LootTableEquipmentsConfig.FIELD_LEGGINGS, buildDefaultSlotEntries("leggings"));
		main.add(LootTableEquipmentsConfig.FIELD_BOOTS, buildDefaultSlotEntries("boots"));

		root.add("main", main);
		return root;
	}

	private static JsonArray buildDefaultSlotEntries(String piece) {
		JsonArray entries = new JsonArray();
		entries.add(itemEntry("minecraft:netherite_" + piece, 1.0D));
		entries.add(itemEntry("minecraft:diamond_" + piece, 5.0D));
		entries.add(itemEntry("minecraft:golden_" + piece, 10.0D));
		entries.add(itemEntry("minecraft:iron_" + piece, 17.0D));
		entries.add(itemEntry("minecraft:copper_" + piece, 28.0D));
		entries.add(itemEntry("minecraft:leather_" + piece, 39.0D));
		return entries;
	}

	private static JsonObject itemEntry(String itemId, double weight) {
		JsonObject entry = new JsonObject();
		entry.addProperty(LootTableEquipmentsConfig.FIELD_ITEM, itemId);
		entry.addProperty(LootTableEquipmentsConfig.FIELD_WEIGHT, weight);
		return entry;
	}
}

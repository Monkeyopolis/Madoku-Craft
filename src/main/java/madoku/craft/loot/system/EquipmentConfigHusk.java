package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class EquipmentConfigHusk {
	private EquipmentConfigHusk() {
	}

	public static JsonObject buildDefaults() {
		return buildDefaults("minecraft:husk");
	}

	private static JsonObject buildDefaults(String mobId) {
		JsonObject root = new JsonObject();
		root.addProperty(LootTableEquipmentsConfig.FIELD_ENABLED, true);
		root.addProperty(LootTableEquipmentsConfig.FIELD_MOB_ID, mobId == null ? "" : mobId);

		JsonObject armorSet = new JsonObject();
		armorSet.addProperty(LootTableEquipmentsConfig.FIELD_PARTIAL_SET, 60.0D);
		armorSet.addProperty(LootTableEquipmentsConfig.FIELD_HALF_SET, 30.0D);
		armorSet.addProperty(LootTableEquipmentsConfig.FIELD_FULL_SET, 10.0D);
		root.add(LootTableEquipmentsConfig.FIELD_ARMOR_SET, armorSet);

		root.add(LootTableEquipmentsConfig.FIELD_HELMET, buildDefaultSlotEntries("helmet"));
		root.add(LootTableEquipmentsConfig.FIELD_CHESTPLATE, buildDefaultSlotEntries("chestplate"));
		root.add(LootTableEquipmentsConfig.FIELD_LEGGINGS, buildDefaultSlotEntries("leggings"));
		root.add(LootTableEquipmentsConfig.FIELD_BOOTS, buildDefaultSlotEntries("boots"));
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

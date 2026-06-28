package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import madoku.craft.config.JsonFormatBuilder;

public final class EquipmentConfigZombie {
	private EquipmentConfigZombie() {
	}

	public static JsonObject buildZombieDefaults() {
		return buildDefaults("minecraft:zombie");
	}

	public static JsonObject buildDefaults(String mobId) {
		return madoku.craft.config.JsonFormatBuilder.object()
			.put(LootTableEquipmentsConfig.FIELD_ENABLED, true)
			.put(LootTableEquipmentsConfig.FIELD_MOB_ID, mobId == null ? "" : mobId)
			.object(LootTableEquipmentsConfig.FIELD_ARMOR_SET, armorSet -> armorSet
				.put(LootTableEquipmentsConfig.FIELD_PARTIAL_SET, 60.0D)
				.put(LootTableEquipmentsConfig.FIELD_HALF_SET, 30.0D)
				.put(LootTableEquipmentsConfig.FIELD_FULL_SET, 10.0D))
			.put(LootTableEquipmentsConfig.FIELD_HELMET, buildDefaultSlotEntries("helmet"))
			.put(LootTableEquipmentsConfig.FIELD_CHESTPLATE, buildDefaultSlotEntries("chestplate"))
			.put(LootTableEquipmentsConfig.FIELD_LEGGINGS, buildDefaultSlotEntries("leggings"))
			.put(LootTableEquipmentsConfig.FIELD_BOOTS, buildDefaultSlotEntries("boots"))
			.build();
	}

	private static JsonArray buildDefaultSlotEntries(String piece) {
		return JsonFormatBuilder.array()
			.add(itemEntry("minecraft:netherite_" + piece, 1.0D))
			.add(itemEntry("minecraft:diamond_" + piece, 5.0D))
			.add(itemEntry("minecraft:golden_" + piece, 10.0D))
			.add(itemEntry("minecraft:iron_" + piece, 17.0D))
			.add(itemEntry("minecraft:copper_" + piece, 28.0D))
			.add(itemEntry("minecraft:leather_" + piece, 39.0D))
			.build();
	}

	private static JsonObject itemEntry(String itemId, double weight) {
		return madoku.craft.config.JsonFormatBuilder.object()
			.put(LootTableEquipmentsConfig.FIELD_ITEM, itemId)
			.put(LootTableEquipmentsConfig.FIELD_WEIGHT, weight)
			.build();
	}
}

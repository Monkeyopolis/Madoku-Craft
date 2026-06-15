package madoku.craft.loot.system;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LootTableEquipmentsConfig {
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_MOB_ID = "mob_id";
	public static final String FIELD_ARMOR_SET = "armor-set";
	public static final String FIELD_PARTIAL_SET = "partial-set";
	public static final String FIELD_HALF_SET = "half-set";
	public static final String FIELD_FULL_SET = "full-set";
	public static final String FIELD_HELMET = "helmet";
	public static final String FIELD_CHESTPLATE = "chestplate";
	public static final String FIELD_LEGGINGS = "leggings";
	public static final String FIELD_BOOTS = "boots";
	public static final String FIELD_ITEM = "item";
	public static final String FIELD_WEIGHT = "weight";

	private LootTableEquipmentsConfig() {
	}

	public static JsonObject buildSettingsDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		return root;
	}

	public static Map<String, JsonObject> buildDefaultEquipmentTableFiles() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		defaults.put("minecraft-equipment-skeleton", EquipmentConfigSkeleton.buildDefaults());
		defaults.put("minecraft-equipment-stray", EquipmentConfigStray.buildDefaults());
		defaults.put("minecraft-equipment-bogged", EquipmentConfigBogged.buildDefaults());
		defaults.put("minecraft-equipment-parched", EquipmentConfigParched.buildDefaults());
		defaults.put("minecraft-equipment-wither-skeleton", EquipmentConfigWitherSkeleton.buildDefaults());
		defaults.put("minecraft-equipment-zombie", EquipmentConfigZombie.buildZombieDefaults());
		defaults.put("minecraft-equipment-husk", EquipmentConfigHusk.buildDefaults());
		defaults.put("minecraft-equipment-drowned", EquipmentConfigDrowned.buildDefaults());
		defaults.put("minecraft-equipment-zombie-villager", EquipmentConfigZombieVillager.buildZombieVillagerDefaults());
		return defaults;
	}
}

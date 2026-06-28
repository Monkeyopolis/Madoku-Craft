package madoku.craft.loot.system;

import com.google.gson.JsonObject;
import madoku.craft.config.JsonFormatBuilder;

public final class LootTableConfigManager {
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_TABLE_ID = "table-id";
	public static final String FIELD_ROLLS = "rolls";
	public static final String FIELD_MIN = "min";
	public static final String FIELD_MAX = "max";
	public static final String FIELD_GROUPS = "groups";
	public static final String FIELD_TAGS = "tags";
	public static final String FIELD_RARITY = "rarity";
	public static final String FIELD_WEIGHT = "weight";
	public static final String FIELD_ENTRIES = "entries";
	public static final String FIELD_ITEM = "item";
	public static final String FIELD_BLOCK = "block";
	public static final String FIELD_ITEM_RARITY = "item-rarity";
	public static final String FIELD_MIN_COUNT = "min-count";
	public static final String FIELD_MAX_COUNT = "max-count";
	public static final String FIELD_USE_MADOKU_LUCK = "use-madoku-luck";
	public static final String FIELD_OVERRIDE_STRUCTURE_LOOT_TABLES = "override-structure-loot-tables";
	public static final String FIELD_OVERRIDE_ENTITY_LOOT_TABLES = "override-entity-loot-tables";
	public static final String FIELD_CUSTOM_ENTITY_EQUIPMENT = "custom-entity-equipment";
	public static final String FIELD_CUSTOM_ENTITY_EQUIPMENT_CHANCE = "custom-entity-equipment-chance";

	private LootTableConfigManager() {
	}

	public static JsonObject buildSettingsDefaults() {
		return JsonFormatBuilder.object()
			.put(FIELD_ENABLED, true)
			.put(FIELD_USE_MADOKU_LUCK, true)
			.put(FIELD_OVERRIDE_STRUCTURE_LOOT_TABLES, true)
			.put(FIELD_OVERRIDE_ENTITY_LOOT_TABLES, true)
			.put(FIELD_CUSTOM_ENTITY_EQUIPMENT, true)
			.put(FIELD_CUSTOM_ENTITY_EQUIPMENT_CHANCE, 10.0d)
			.build();
	}
}


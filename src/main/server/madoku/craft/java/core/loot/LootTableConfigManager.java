package madoku.craft.java.core.loot;

import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;

import java.util.Locale;

public final class LootTableConfigManager {
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_TABLE_ID = "table-id";
	public static final String FIELD_ROLLS = "rolls";
	public static final String FIELD_MIN = "min";
	public static final String FIELD_MAX = "max";
	public static final String FIELD_GROUPS = "groups";
	public static final String FIELD_TABLE_ENTRIES = "table-entries";
	public static final String FIELD_ENTRY = "entry";
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
	public static final String FIELD_OVERRIDE_CROP_LOOT_TABLES = "override-crop-loot-tables";
	public static final String FIELD_OVERRIDE_ENTITY_EQUIPMENT = "override-entity-equipment";

	private LootTableConfigManager() {
	}

	public static void initialize() {
		EntitiesConfigManager.initialize();
		StructuresConfigManager.initialize();
		EquipmentsConfigAPIManager.initialize();
	}

	public static void reset() {
		EntitiesConfigManager.reset();
		StructuresConfigManager.reset();
		EquipmentsConfigAPIManager.reset();
	}

	public static JsonObject buildSettingsDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENABLED, true)
			.put(FIELD_USE_MADOKU_LUCK, true)
			.put(FIELD_OVERRIDE_STRUCTURE_LOOT_TABLES, true)
			.put(FIELD_OVERRIDE_ENTITY_LOOT_TABLES, true)
			.put(FIELD_OVERRIDE_CROP_LOOT_TABLES, true)
			.put(FIELD_OVERRIDE_ENTITY_EQUIPMENT, true)
			.build();
	}

	static String fileKeyFromTableId(String tableId, String fallback) {
		String normalized = tableId == null ? "" : tableId.trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			return fallback;
		}
		StringBuilder key = new StringBuilder(normalized.length() + 8);
		boolean previousDash = false;
		for (int index = 0; index < normalized.length(); index++) {
			char value = normalized.charAt(index);
			if (Character.isLetterOrDigit(value)) {
				key.append(value);
				previousDash = false;
			} else if (!previousDash) {
				key.append('-');
				previousDash = true;
			}
		}
		int start = 0;
		while (start < key.length() && key.charAt(start) == '-') start++;
		int end = key.length();
		while (end > start && key.charAt(end - 1) == '-') end--;
		String collapsed = key.substring(start, end);
		return collapsed.isBlank() ? fallback : collapsed;
	}
}



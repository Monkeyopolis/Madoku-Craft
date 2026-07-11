package madoku.craft.loot.system;

import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class LootTableConfigEntities {
	private LootTableConfigEntities() {
	}

	public static JsonObject buildEntityTableTemplate(String tableId) {
		return JSONFormatManager.object()
			.put(LootTableConfigManager.FIELD_ENABLED, false)
			.put(LootTableConfigManager.FIELD_TABLE_ID, tableId == null ? "" : tableId)
			.object(LootTableConfigManager.FIELD_ROLLS, rolls -> rolls
				.put(LootTableConfigManager.FIELD_MIN, 1)
				.put(LootTableConfigManager.FIELD_MAX, 2))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> {
			})
			.build();
	}

	public static JsonObject buildEntityTable(String tableId, boolean enabled, int minRolls, int maxRolls) {
		return JSONFormatManager.object()
			.put(LootTableConfigManager.FIELD_ENABLED, enabled)
			.put(LootTableConfigManager.FIELD_TABLE_ID, tableId == null ? "" : tableId)
			.object(LootTableConfigManager.FIELD_ROLLS, rolls -> rolls
				.put(LootTableConfigManager.FIELD_MIN, Math.max(0, minRolls))
				.put(LootTableConfigManager.FIELD_MAX, Math.max(minRolls, maxRolls)))
			.array(LootTableConfigManager.FIELD_GROUPS, groups -> {
			})
			.build();
	}

	public static Map<String, JsonObject> buildDefaultEntityTableFiles() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		put(defaults, "minecraft:entities/bee", ConfigEntitiesBee.buildDefaults());
		put(defaults, "minecraft:entities/bogged", ConfigEntitiesBogged.buildDefaults());
		put(defaults, "minecraft:entities/parched", ConfigEntitiesParched.buildDefaults());
		put(defaults, "minecraft:entities/cave_spider", ConfigEntitiesCaveSpider.buildDefaults());
		put(defaults, "minecraft:entities/creeper", ConfigEntitiesCreeper.buildDefaults());
		put(defaults, "minecraft:entities/drowned", ConfigEntitiesDrowned.buildDefaults());
		put(defaults, "minecraft:entities/husk", ConfigEntitiesHusk.buildDefaults());
		put(defaults, "minecraft:entities/skeleton", ConfigEntitiesSkeleton.buildDefaults());
		put(defaults, "minecraft:entities/spider", ConfigEntitiesSpider.buildDefaults());
		put(defaults, "minecraft:entities/stray", ConfigEntitiesStray.buildDefaults());
		put(defaults, "minecraft:entities/wither_skeleton", ConfigEntitiesWitherSkeleton.buildDefaults());
		put(defaults, "minecraft:entities/zombie", ConfigEntitiesZombie.buildDefaults());
		put(defaults, "minecraft:entities/zombie_villager", ConfigEntitiesZombieVillager.buildDefaults());
		return defaults;
	}

	private static void put(Map<String, JsonObject> defaults, String tableId, JsonObject root) {
		if (defaults == null || root == null || tableId == null || tableId.isBlank()) {
			return;
		}
		defaults.put(fileKeyFromTableId(tableId), root);
	}

	private static String fileKeyFromTableId(String tableId) {
		String normalized = tableId == null ? "" : tableId.trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			return "entity-table";
		}
		StringBuilder key = new StringBuilder(normalized.length() + 8);
		boolean previousDash = false;
		for (int index = 0; index < normalized.length(); index++) {
			char value = normalized.charAt(index);
			if (Character.isLetterOrDigit(value)) {
				key.append(value);
				previousDash = false;
				continue;
			}
			if (!previousDash) {
				key.append('-');
				previousDash = true;
			}
		}
		int start = 0;
		while (start < key.length() && key.charAt(start) == '-') {
			start++;
		}
		int end = key.length();
		while (end > start && key.charAt(end - 1) == '-') {
			end--;
		}
		String collapsed = key.substring(start, end);
		return collapsed.isBlank() ? "entity-table" : collapsed;
	}
}


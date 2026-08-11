package madoku.craft.api.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.MadokuJSONManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.LootContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads and rolls the crop loot-table group used by farming and vanilla crop drops. */
public final class LootTableCropsManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(LootTableCropsManager.class);
	private static final String TABLES_FOLDER = "madoku-crops";

	private static volatile Settings settings = Settings.defaults();
	private static volatile Map<String, ManagedLootTable> tablesById = Map.of();
	private static volatile long nextReloadAtMillis;

	private LootTableCropsManager() {
	}

	public static void initialize() {
		reloadNow(null);
	}

	public static void reset() {
		settings = Settings.defaults();
		tablesById = Map.of();
		nextReloadAtMillis = 0L;
	}

	/** Returns null when this table is not a managed crop table, allowing vanilla loot to continue. */
	public static List<ItemStack> generateManagedLootForContext(LootContext lootContext) {
		if (lootContext == null) {
			return null;
		}
		// Farming owns crop harvesting while enabled, including its tracked crop
		// state and fertilized-yield adjustments. The loot API still handles the
		// vanilla crop tables when Farming is disabled.
		if (madoku.craft.farming.MadokuFarmingManager.isEnabled()) {
			return null;
		}
		ServerLevel level = lootContext.getLevel();
		reloadIfNeeded(level == null ? null : level.getServer());
		if (!settings.enabled || !settings.overrideCropLootTables) {
			return null;
		}
		String tableId = resolveQueriedLootTableId(lootContext);
		if (tableId.isBlank()) {
			tableId = resolveBlockLootTableId(lootContext);
		}
		ManagedLootTable table = tablesById.get(resolveTableId(tableId));
		if (table == null) {
			return null;
		}
		RandomSource random = lootContext.getRandom();
		return rollTable(table, random == null ? RandomSource.create() : random);
	}

	/** Rolls a crop table referenced by a crop file's yield-id field. */
	public static List<ItemStack> generateManagedLootForTable(String tableId, RandomSource random) {
		reloadIfNeeded(null);
		if (!settings.enabled || !settings.overrideCropLootTables) {
			return List.of();
		}
		ManagedLootTable table = tablesById.get(resolveTableId(tableId));
		return table == null ? List.of() : rollTable(table, random == null ? RandomSource.create() : random);
	}

	public static boolean hasManagedLootTable(String tableId) {
		reloadIfNeeded(null);
		return settings.enabled && settings.overrideCropLootTables && tablesById.containsKey(resolveTableId(tableId));
	}

	private static List<ItemStack> rollTable(ManagedLootTable table, RandomSource random) {
		if (table == null || table.tableEntries().isEmpty() || random == null) {
			return List.of();
		}
		List<ItemStack> generated = new ArrayList<>();
		for (ManagedTableEntry tableEntry : table.tableEntries()) {
			int minRolls = Math.max(0, tableEntry.minRolls());
			int maxRolls = Math.max(minRolls, tableEntry.maxRolls());
			int rolls = minRolls == maxRolls ? minRolls : minRolls + random.nextInt(maxRolls - minRolls + 1);
			for (int roll = 0; roll < rolls; roll++) {
				ManagedLootEntry entry = pickEntry(tableEntry.entries(), random);
				if (entry == null) continue;
				int min = Math.max(0, entry.minCount());
				int max = Math.max(min, entry.maxCount());
				int count = min == max ? min : min + random.nextInt(max - min + 1);
				appendStack(generated, entry.item(), count);
			}
		}
		return List.copyOf(generated);
	}

	private static ManagedLootEntry pickEntry(List<ManagedLootEntry> entries, RandomSource random) {
		int totalWeight = 0;
		for (ManagedLootEntry entry : entries) {
			if (entry != null && entry.weight() > 0) {
				totalWeight += entry.weight();
			}
		}
		if (totalWeight <= 0) {
			return null;
		}
		int pick = random.nextInt(totalWeight);
		int cursor = 0;
		for (ManagedLootEntry entry : entries) {
			if (entry == null || entry.weight() <= 0) {
				continue;
			}
			cursor += entry.weight();
			if (pick < cursor) {
				return entry;
			}
		}
		return entries.getLast();
	}

	private static void appendStack(List<ItemStack> generated, Item item, int count) {
		if (generated == null || item == null || count <= 0) {
			return;
		}
		int maxStackSize = Math.max(1, new ItemStack(item, 1).getMaxStackSize());
		int remaining = count;
		while (remaining > 0) {
			int stackCount = Math.min(maxStackSize, remaining);
			generated.add(new ItemStack(item, stackCount));
			remaining -= stackCount;
		}
	}

	private static void reloadIfNeeded(net.minecraft.server.MinecraftServer server) {
		if (System.currentTimeMillis() >= nextReloadAtMillis) {
			reloadNow(server);
		}
	}

	private static synchronized void reloadNow(net.minecraft.server.MinecraftServer server) {
		long now = System.currentTimeMillis();
		try {
			Path root = MadokuJSONManager.getOrCreateGlobalSystemDirectory(MadokuLootTableManager.CONFIG_ROOT_FOLDER_NAME);
			Path settingsFile = root.resolve("madoku-loot-tables.json");
			JsonObject defaults = LootTableConfigManager.buildSettingsDefaults();
			JsonObject normalizedSettings = JSONFormatManager.ensureManagedFile(settingsFile, defaults);
			Settings loadedSettings = Settings.fromJson(normalizedSettings);

			Map<String, JsonObject> files = JSONFormatManager.ensureManagedFolder(
				root.resolve(TABLES_FOLDER),
				CropsConfigManager.buildDefaultCropTableFiles(),
				ignored -> new JsonObject(),
				LootTableCropsManager::isSupportedTable,
				(key, value) -> value == null || value.isJsonNull() ? null : value.deepCopy()
			);
			Map<String, ManagedLootTable> resolved = new HashMap<>();
			for (JsonObject file : files.values()) {
				ManagedLootTable table = parseTable(file);
				if (table != null) {
					resolved.put(table.tableId(), table);
				}
			}
			settings = loadedSettings;
			tablesById = Map.copyOf(resolved);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to reload crop loot tables; preserving last valid cache.", exception);
		} finally {
			nextReloadAtMillis = now + MadokuLootTableManager.resolveReloadIntervalMillis(server);
		}
	}

	private static boolean isSupportedTable(String fileKey, JsonObject source) {
		return source != null && (!readBoolean(source, LootTableConfigManager.FIELD_ENABLED, true)
			|| !resolveTableId(readString(source, LootTableConfigManager.FIELD_TABLE_ID, "")).isBlank());
	}

	private static ManagedLootTable parseTable(JsonObject root) {
		if (root == null || !readBoolean(root, LootTableConfigManager.FIELD_ENABLED, true)) {
			return null;
		}
		String tableId = resolveTableId(readString(root, LootTableConfigManager.FIELD_TABLE_ID, ""));
		if (tableId.isBlank()) {
			return null;
		}
		List<ManagedTableEntry> tableEntries = parseTableEntries(root.get(LootTableConfigManager.FIELD_TABLE_ENTRIES));
		return tableEntries.isEmpty() ? null : new ManagedLootTable(tableId, List.copyOf(tableEntries));
	}

	private static List<ManagedTableEntry> parseTableEntries(JsonElement element) {
		if (!(element instanceof JsonArray array) || array.isEmpty()) {
			return List.of();
		}
		List<ManagedTableEntry> tableEntries = new ArrayList<>();
		for (JsonElement value : array) {
			if (!(value instanceof JsonObject tableEntry)) {
				continue;
			}
			JsonObject rolls = object(tableEntry, LootTableConfigManager.FIELD_ROLLS);
			int minRolls = Math.max(0, readInt(rolls, LootTableConfigManager.FIELD_MIN, 1));
			int maxRolls = Math.max(minRolls, readInt(rolls, LootTableConfigManager.FIELD_MAX, minRolls));
			List<ManagedLootEntry> entries = parseEntries(tableEntry.get(LootTableConfigManager.FIELD_ENTRY));
			if (!entries.isEmpty()) {
				tableEntries.add(new ManagedTableEntry(minRolls, maxRolls, List.copyOf(entries)));
			}
		}
		return tableEntries;
	}

	private static List<ManagedLootEntry> parseEntries(JsonElement element) {
		if (!(element instanceof JsonArray array) || array.isEmpty()) {
			return List.of();
		}
		List<ManagedLootEntry> entries = new ArrayList<>();
		for (JsonElement value : array) {
			if (!(value instanceof JsonObject entry)) {
				continue;
			}
			String itemId = readString(entry, LootTableConfigManager.FIELD_ITEM, readString(entry, LootTableConfigManager.FIELD_BLOCK, ""));
			Identifier identifier = Identifier.tryParse(MadokuJSONManager.normalizeRegistryIdentifierForLookup(itemId));
			Item item = identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier) ? null : BuiltInRegistries.ITEM.getValue(identifier);
			if (item == null) {
				continue;
			}
			int weight = Math.max(1, readInt(entry, LootTableConfigManager.FIELD_WEIGHT, 1));
			int minCount = Math.max(0, readInt(entry, LootTableConfigManager.FIELD_MIN_COUNT, 1));
			int maxCount = Math.max(minCount, readInt(entry, LootTableConfigManager.FIELD_MAX_COUNT, minCount));
			entries.add(new ManagedLootEntry(item, weight, minCount, maxCount));
		}
		return entries;
	}

	private static String resolveTableId(String value) {
		return value == null || value.isBlank() ? "" : MadokuJSONManager.normalizeRegistryIdentifierForLookup(value);
	}

	private static String resolveQueriedLootTableId(LootContext context) {
		for (String methodName : new String[] { "getQueriedLootTableId", "queriedLootTableId" }) {
			try {
				Method method = context.getClass().getMethod(methodName);
				Object value = method.invoke(context);
				String id = normalizeIdentifierObject(value);
				if (!id.isBlank()) return id;
			} catch (ReflectiveOperationException | RuntimeException ignored) {
			}
		}
		return "";
	}

	private static String resolveBlockLootTableId(LootContext context) {
		if (context == null) {
			return "";
		}

		try {
			BlockState state = context.getOptionalParameter(LootContextParams.BLOCK_STATE);
			if (state == null) {
				return "";
			}

			Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
			if (blockId == null) {
				return "";
			}

			return switch (blockId.toString()) {
				case "minecraft:potatoes" -> "minecraft:blocks/potatoes";
				case "minecraft:carrots" -> "minecraft:blocks/carrots";
				case "minecraft:beetroots" -> "minecraft:blocks/beetroots";
				case "minecraft:melon" -> "minecraft:blocks/melon";
				case "minecraft:pumpkin" -> "minecraft:blocks/pumpkin";
				case "minecraft:wheat" -> "minecraft:blocks/wheat";
				default -> "";
			};
		} catch (RuntimeException ignored) {
			return "";
		}
	}

	private static String normalizeIdentifierObject(Object value) {
		if (value == null) return "";
		if (value instanceof java.util.Optional<?> optional) {
			return optional.isPresent() ? normalizeIdentifierObject(optional.get()) : "";
		}
		if (value instanceof ResourceKey<?> key) {
			return resolveTableId(key.identifier().toString());
		}
		String id = value.toString();
		if (id.startsWith("Optional[") && id.endsWith("]")) {
			id = id.substring(9, id.length() - 1);
		}
		Identifier identifier = Identifier.tryParse(id.trim().toLowerCase(Locale.ROOT));
		return identifier == null ? "" : resolveTableId(identifier.toString());
	}

	private static JsonObject object(JsonObject root, String key) {
		JsonElement value = root == null ? null : root.get(key);
		return value instanceof JsonObject object ? object : new JsonObject();
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		JsonElement value = root == null ? null : root.get(key);
		return value instanceof JsonPrimitive primitive && primitive.isBoolean() ? primitive.getAsBoolean() : fallback;
	}

	private static int readInt(JsonObject root, String key, int fallback) {
		JsonElement value = root == null ? null : root.get(key);
		return value instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsInt() : fallback;
	}

	private static String readString(JsonObject root, String key, String fallback) {
		JsonElement value = root == null ? null : root.get(key);
		return value instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : fallback;
	}

	private record ManagedLootTable(String tableId, List<ManagedTableEntry> tableEntries) { }
	private record ManagedTableEntry(int minRolls, int maxRolls, List<ManagedLootEntry> entries) { }
	private record ManagedLootEntry(Item item, int weight, int minCount, int maxCount) { }

	private static final class Settings {
		private final boolean enabled;
		private final boolean overrideCropLootTables;

		private Settings(boolean enabled, boolean overrideCropLootTables) {
			this.enabled = enabled;
			this.overrideCropLootTables = overrideCropLootTables;
		}

		private static Settings defaults() {
			JsonObject defaults = LootTableConfigManager.buildSettingsDefaults();
			return new Settings(
				readBoolean(defaults, LootTableConfigManager.FIELD_ENABLED, true),
				readBoolean(defaults, LootTableConfigManager.FIELD_OVERRIDE_CROP_LOOT_TABLES, true)
			);
		}

		private static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			return new Settings(
				readBoolean(source, LootTableConfigManager.FIELD_ENABLED, defaults.enabled),
				readBoolean(source, LootTableConfigManager.FIELD_OVERRIDE_CROP_LOOT_TABLES, defaults.overrideCropLootTables)
			);
		}
	}
}

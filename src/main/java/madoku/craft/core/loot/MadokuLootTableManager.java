package madoku.craft.core.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.attributes.MadokuLuckManager;
import madoku.craft.core.MadokuCoreManager;
import madoku.craft.core.json.MadokuJSONManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.List;

public final class MadokuLootTableManager {
	static final String CONFIG_ROOT_FOLDER_NAME = MadokuCoreManager.API_FOLDER_NAME + "/madoku-loot-tables";

	private MadokuLootTableManager() {
	}

	public static void initialize() {
		LootTableConfigManager.initialize();
		LootTableStructuresManager.initialize();
		LootTableEntitiesManager.initialize();
		LootTableEquipmentsManager.initialize();
		LootTableCropsManager.initialize();
	}

	public static boolean applyManagedLootTable(
		Container container,
		ResourceKey<LootTable> lootTableKey,
		long lootTableSeed,
		ServerLevel level,
		ServerPlayer player
	) {
		return LootTableStructuresManager.applyManagedLootTable(container, lootTableKey, lootTableSeed, level, player);
	}

	public static List<ItemStack> generateManagedLootForContext(LootContext lootContext) {
		List<ItemStack> generated = LootTableStructuresManager.generateManagedLootForContext(lootContext);
		if (generated != null) {
			return generated;
		}
		generated = LootTableEntitiesManager.generateManagedLootForContext(lootContext);
		if (generated != null) {
			return generated;
		}
		return LootTableCropsManager.generateManagedLootForContext(lootContext);
	}

	public static void reset() {
		LootTableEquipmentsManager.reset();
		LootTableStructuresManager.reset();
		LootTableEntitiesManager.reset();
		LootTableConfigManager.reset();
		LootTableCropsManager.reset();
	}

	static long resolveReloadIntervalMillis(net.minecraft.server.MinecraftServer server) {
		long ticks = madoku.craft.core.scheduler.SchedulerAdaptiveIntervalManager.resolve("loot-table-config", server, 30L, 600L);
		return ticks * 50L;
	}

	static JsonObject buildSharedTable(String tableId, JsonObject... tableEntries) {
		return buildSharedTable(tableId, true, tableEntries);
	}

	static JsonObject buildSharedTable(String tableId, boolean enabled, JsonObject... tableEntries) {
		JsonObject root = new JsonObject();
		root.addProperty(LootTableConfigManager.FIELD_ENABLED, enabled);
		root.addProperty(
			LootTableConfigManager.FIELD_TABLE_ID,
			MadokuJSONManager.normalizeRegistryIdentifierForJson(tableId)
		);
		JsonArray entries = new JsonArray();
		if (tableEntries != null) {
			for (JsonObject tableEntry : tableEntries) {
				if (tableEntry != null && !tableEntry.isEmpty()) {
					entries.add(tableEntry);
				}
			}
		}
		root.add(LootTableConfigManager.FIELD_TABLE_ENTRIES, entries);
		return root;
	}

	static JsonObject buildSharedTableEntry(int minRolls, int maxRolls, JsonObject... entries) {
		JsonObject root = new JsonObject();
		JsonObject rolls = new JsonObject();
		rolls.addProperty(LootTableConfigManager.FIELD_MIN, Math.max(0, minRolls));
		rolls.addProperty(LootTableConfigManager.FIELD_MAX, Math.max(minRolls, maxRolls));
		root.add(LootTableConfigManager.FIELD_ROLLS, rolls);
		JsonArray entryArray = new JsonArray();
		if (entries != null) {
			for (JsonObject entry : entries) {
				if (entry != null && !entry.isEmpty()) {
					entryArray.add(entry);
				}
			}
		}
		root.add(LootTableConfigManager.FIELD_ENTRY, entryArray);
		return root;
	}

	static JsonObject buildSharedEntry(String itemId, int minCount, int maxCount) {
		return buildSharedEntry(itemId, 1, minCount, maxCount);
	}

	static JsonObject buildSharedEntry(String itemId, int weight, int minCount, int maxCount) {
		JsonObject root = new JsonObject();
		root.addProperty(
			LootTableConfigManager.FIELD_ITEM,
			normalizeSharedItemIdForJson(itemId)
		);
		root.addProperty(LootTableConfigManager.FIELD_WEIGHT, Math.max(1, weight));
		root.addProperty(LootTableConfigManager.FIELD_MIN_COUNT, Math.max(0, minCount));
		root.addProperty(LootTableConfigManager.FIELD_MAX_COUNT, Math.max(minCount, maxCount));
		return root;
	}

	static SharedLootTable parseSharedTable(JsonObject root) {
		if (root == null || !readBoolean(root, LootTableConfigManager.FIELD_ENABLED, true)) {
			return null;
		}

		String tableId = normalizeSharedTableId(readString(root, LootTableConfigManager.FIELD_TABLE_ID, ""));
		if (tableId.isBlank()) {
			return null;
		}

		JsonElement tableEntriesElement = root.get(LootTableConfigManager.FIELD_TABLE_ENTRIES);
		if (!(tableEntriesElement instanceof JsonArray tableEntriesArray) || tableEntriesArray.isEmpty()) {
			return null;
		}

		List<SharedTableEntry> tableEntries = new ArrayList<>();
		for (JsonElement value : tableEntriesArray) {
			if (!(value instanceof JsonObject tableEntry)) {
				continue;
			}
			JsonObject rolls = readObject( tableEntry, LootTableConfigManager.FIELD_ROLLS);
			int minRolls = Math.max(0, readInt(rolls, LootTableConfigManager.FIELD_MIN, 1));
			int maxRolls = Math.max(minRolls, readInt(rolls, LootTableConfigManager.FIELD_MAX, minRolls));
			List<SharedLootEntry> entries = parseSharedEntries(tableEntry.get(LootTableConfigManager.FIELD_ENTRY));
			if (!entries.isEmpty()) {
				tableEntries.add(new SharedTableEntry(minRolls, maxRolls, List.copyOf(entries)));
			}
		}
		return tableEntries.isEmpty() ? null : new SharedLootTable(tableId, List.copyOf(tableEntries));
	}

	static List<ItemStack> rollSharedTable(SharedLootTable table, RandomSource random) {
		return rollSharedTable(table, random, 0.0d, false);
	}

	static List<ItemStack> rollSharedTable(
		SharedLootTable table,
		RandomSource random,
		ServerPlayer player,
		boolean useMadokuLuck
	) {
		boolean luckActive = useMadokuLuck && player != null && MadokuLuckManager.isEnabled();
		double luckStat = luckActive ? MadokuLuckManager.resolveLootLuckStat(player) : 0.0d;
		return rollSharedTable(table, random, luckStat, luckActive);
	}

	private static List<ItemStack> rollSharedTable(
		SharedLootTable table,
		RandomSource random,
		double luckStat,
		boolean luckActive
	) {
		if (table == null || random == null || table.tableEntries().isEmpty()) {
			return List.of();
		}

		List<ItemStack> generated = new ArrayList<>();
		for (SharedTableEntry tableEntry : table.tableEntries()) {
			int minRolls = Math.max(0, tableEntry.minRolls());
			int maxRolls = Math.max(minRolls, tableEntry.maxRolls());
			int rolls = minRolls == maxRolls
				? minRolls
				: minRolls + random.nextInt(maxRolls - minRolls + 1);
			rolls = applySharedRollLuckMultiplier(rolls, luckStat, luckActive, random);
			for (int roll = 0; roll < rolls; roll++) {
				SharedLootEntry entry = pickSharedEntry(tableEntry.entries(), random);
				if (entry == null) {
					continue;
				}
				int min = Math.max(0, entry.minCount());
				int max = Math.max(min, entry.maxCount());
				int count = min == max ? min : min + random.nextInt(max - min + 1);
				appendSharedStacks(generated, entry.item(), count);
			}
		}
		return List.copyOf(generated);
	}

	private static int applySharedRollLuckMultiplier(
		int baseRolls,
		double luckStat,
		boolean luckActive,
		RandomSource random
	) {
		if (baseRolls <= 0) {
			return 0;
		}
		if (!luckActive || !Double.isFinite(luckStat)) {
			return baseRolls;
		}

		double multiplier = Math.max(0.0d, 1.0d + (Math.max(0.0d, luckStat) * 0.01d));
		double rawRolls = Math.max(0.0d, baseRolls * multiplier);
		int wholeRolls = (int) Math.floor(rawRolls);
		double fractionalRoll = rawRolls - wholeRolls;
		if (fractionalRoll > 0.0d && random != null && random.nextDouble() < fractionalRoll) {
			wholeRolls++;
		}
		return Math.max(0, wholeRolls);
	}

	static String normalizeSharedTableId(String value) {
		return value == null || value.isBlank() ? "" : MadokuJSONManager.normalizeRegistryIdentifierForLookup(value);
	}

	private static String normalizeSharedItemIdForJson(String value) {
		if (value != null && "empty".equalsIgnoreCase(value.trim())) {
			return "empty";
		}
		return MadokuJSONManager.normalizeRegistryIdentifierForJson(value);
	}

	private static List<SharedLootEntry> parseSharedEntries(JsonElement element) {
		if (!(element instanceof JsonArray entriesArray) || entriesArray.isEmpty()) {
			return List.of();
		}

		List<SharedLootEntry> entries = new ArrayList<>();
		for (JsonElement rawEntry : entriesArray) {
			if (!(rawEntry instanceof JsonObject entryRoot)) {
				continue;
			}
			String itemId = readString(entryRoot, LootTableConfigManager.FIELD_ITEM, "");
			if (itemId.isBlank()) {
				itemId = readString(entryRoot, LootTableConfigManager.FIELD_BLOCK, "");
			}
			Item item = resolveSharedItem(itemId);
			if (item == null) {
				continue;
			}
			int weight = Math.max(1, readInt(entryRoot, LootTableConfigManager.FIELD_WEIGHT, 1));
			int minCount = Math.max(0, readInt(entryRoot, LootTableConfigManager.FIELD_MIN_COUNT, 1));
			int maxCount = Math.max(minCount, readInt(entryRoot, LootTableConfigManager.FIELD_MAX_COUNT, minCount));
			entries.add(new SharedLootEntry(item, weight, minCount, maxCount));
		}
		return entries;
	}

	private static SharedLootEntry pickSharedEntry(List<SharedLootEntry> entries, RandomSource random) {
		int totalWeight = 0;
		for (SharedLootEntry entry : entries) {
			if (entry != null && entry.weight() > 0) {
				totalWeight += entry.weight();
			}
		}
		if (totalWeight <= 0) {
			return null;
		}

		int pick = random.nextInt(totalWeight);
		int cursor = 0;
		for (SharedLootEntry entry : entries) {
			if (entry == null || entry.weight() <= 0) {
				continue;
			}
			cursor += entry.weight();
			if (pick < cursor) {
				return entry;
			}
		}
		return null;
	}

	private static void appendSharedStacks(List<ItemStack> generated, Item item, int count) {
		if (generated == null || item == null || item == Items.AIR || count <= 0) {
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

	private static Item resolveSharedItem(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return null;
		}
		if ("empty".equalsIgnoreCase(itemId.trim())) {
			return Items.AIR;
		}
		Identifier identifier = Identifier.tryParse(MadokuJSONManager.normalizeRegistryIdentifierForLookup(itemId));
		if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
			return null;
		}
		return BuiltInRegistries.ITEM.getValue(identifier);
	}

	private static JsonObject readObject(JsonObject root, String key) {
		JsonElement value = root == null ? null : root.get(key);
		return value instanceof JsonObject object ? object : new JsonObject();
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		JsonElement value = root == null ? null : root.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
			? value.getAsBoolean() : fallback;
	}

	private static int readInt(JsonObject root, String key, int fallback) {
		JsonElement value = root == null ? null : root.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
			? value.getAsInt() : fallback;
	}

	private static String readString(JsonObject root, String key, String fallback) {
		JsonElement value = root == null ? null : root.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
			? value.getAsString() : fallback;
	}

	static record SharedLootTable(String tableId, List<SharedTableEntry> tableEntries) { }

	static record SharedTableEntry(int minRolls, int maxRolls, List<SharedLootEntry> entries) { }

	static record SharedLootEntry(Item item, int weight, int minCount, int maxCount) { }
}


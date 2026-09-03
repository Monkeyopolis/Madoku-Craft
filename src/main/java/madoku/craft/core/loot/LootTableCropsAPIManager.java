package madoku.craft.core.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import madoku.craft.attributes.LuckAPIManager;
import madoku.craft.core.data.ChunkDataAPIManager;
import madoku.craft.core.json.JSONFormatAPIManager;
import madoku.craft.core.json.JSONAPIManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.LootContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads and rolls the crop loot-table group used by farming and vanilla crop drops. */
public final class LootTableCropsAPIManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(LootTableCropsAPIManager.class);
	private static final String TABLES_FOLDER = "madoku-crops";

	private static volatile Settings settings = Settings.defaults();
	private static volatile Map<String, LootTableAPIManager.SharedLootTable> tablesById = Map.of();
	private static volatile long nextReloadAtMillis;

	private LootTableCropsAPIManager() {
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
		if (LuckAPIManager.isActiveDropPlayerPlacedBlock()
			|| ChunkDataAPIManager.isPlayerPlacedBlock(level, resolveBlockPos(lootContext))) {
			return null;
		}
		reloadIfNeeded(level == null ? null : level.getServer());
		if (!settings.enabled || !settings.overrideCropLootTables) {
			return null;
		}
		String tableId = resolveQueriedLootTableId(lootContext);
		if (tableId.isBlank()) {
			tableId = resolveBlockLootTableId(lootContext);
		}
		LootTableAPIManager.SharedLootTable table = tablesById.get(resolveTableId(tableId));
		if (table == null) {
			return null;
		}
		RandomSource random = lootContext.getRandom();
		ServerPlayer player = LuckAPIManager.resolveLootPlayer(lootContext);
		if (player == null) {
			player = LuckAPIManager.resolveActiveDropPlayer();
		}
		return LootTableAPIManager.rollSharedTable(
			table,
			random == null ? RandomSource.create() : random,
			player,
			true
		);
	}

	/** Rolls a crop table referenced by a crop file's yield-id field. */
	public static List<ItemStack> generateManagedLootForTable(String tableId, RandomSource random) {
		reloadIfNeeded(null);
		if (!settings.enabled || !settings.overrideCropLootTables) {
			return List.of();
		}
		LootTableAPIManager.SharedLootTable table = tablesById.get(resolveTableId(tableId));
		ServerPlayer player = LuckAPIManager.resolveActiveDropPlayer();
		return table == null
			? List.of()
			: LootTableAPIManager.rollSharedTable(
				table,
				random == null ? RandomSource.create() : random,
				player,
				true
			);
	}

	public static boolean hasManagedLootTable(String tableId) {
		reloadIfNeeded(null);
		return settings.enabled && settings.overrideCropLootTables && tablesById.containsKey(resolveTableId(tableId));
	}

	private static void reloadIfNeeded(net.minecraft.server.MinecraftServer server) {
		if (System.currentTimeMillis() >= nextReloadAtMillis) {
			reloadNow(server);
		}
	}

	private static synchronized void reloadNow(net.minecraft.server.MinecraftServer server) {
		long now = System.currentTimeMillis();
		try {
			Path root = JSONAPIManager.getOrCreateGlobalSystemDirectory(LootTableAPIManager.CONFIG_ROOT_FOLDER_NAME);
			Path settingsFile = root.resolve("madoku-loot-tables.json");
			JsonObject defaults = LootTableConfigManager.buildSettingsDefaults();
			JsonObject normalizedSettings = JSONFormatAPIManager.ensureManagedFile(settingsFile, defaults);
			Settings loadedSettings = Settings.fromJson(normalizedSettings);

			Map<String, JsonObject> files = JSONFormatAPIManager.ensureManagedFolder(
				root.resolve(TABLES_FOLDER),
				CropsConfigManager.buildDefaultCropTableFiles(),
				ignored -> new JsonObject(),
				LootTableCropsAPIManager::isSupportedTable,
				(key, value) -> value == null || value.isJsonNull() ? null : value.deepCopy()
			);
			Map<String, LootTableAPIManager.SharedLootTable> resolved = new HashMap<>();
			for (JsonObject file : files.values()) {
				LootTableAPIManager.SharedLootTable table = LootTableAPIManager.parseSharedTable(file);
				if (table != null) {
					resolved.put(table.tableId(), table);
				}
			}
			settings = loadedSettings;
			tablesById = Map.copyOf(resolved);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to reload crop loot tables; preserving last valid cache.", exception);
		} finally {
			nextReloadAtMillis = now + LootTableAPIManager.resolveReloadIntervalMillis(server);
		}
	}

	private static boolean isSupportedTable(String fileKey, JsonObject source) {
		return source != null && (!readBoolean(source, LootTableConfigManager.FIELD_ENABLED, true)
			|| !resolveTableId(readString(source, LootTableConfigManager.FIELD_TABLE_ID, "")).isBlank());
	}

	private static String resolveTableId(String value) {
		return LootTableAPIManager.normalizeSharedTableId(value);
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

	private static BlockPos resolveBlockPos(LootContext context) {
		if (context == null) {
			return null;
		}

		try {
			Vec3 origin = context.getOptionalParameter(LootContextParams.ORIGIN);
			return origin == null ? null : BlockPos.containing(origin);
		} catch (RuntimeException ignored) {
			return null;
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

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		JsonElement value = root == null ? null : root.get(key);
		return value instanceof JsonPrimitive primitive && primitive.isBoolean() ? primitive.getAsBoolean() : fallback;
	}

	private static String readString(JsonObject root, String key, String fallback) {
		JsonElement value = root == null ? null : root.get(key);
		return value instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : fallback;
	}

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

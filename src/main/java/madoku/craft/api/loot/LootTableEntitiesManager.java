package madoku.craft.api.loot;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.MadokuJSONManager;
import madoku.craft.attributes.MadokuLuckManager;
import madoku.craft.mob.MobEntityManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class LootTableEntitiesManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(LootTableEntitiesManager.class);

	private static final String LOOT_CONFIG_ROOT_FOLDER_NAME = MadokuLootTableManager.CONFIG_ROOT_FOLDER_NAME;
	private static final String LOOT_CONFIG_SETTINGS_FILE_NAME = "madoku-loot-tables";
	private static final String LOOT_CONFIG_TABLES_FOLDER_NAME = "madoku-entities";
	private static final String ENTITY_LOOT_NAMESPACE = "minecraft";
	private static final String ENTITY_LOOT_PREFIX = "minecraft:entities/";
	private static volatile Settings settings = Settings.defaults();
	private static volatile Map<String, MadokuLootTableManager.SharedLootTable> tablesById = Map.of();
	private static volatile Map<String, MadokuLootTableManager.SharedLootTable> tablesByFileKey = Map.of();
	private static volatile long nextReloadAtMillis;

	private LootTableEntitiesManager() {
	}

	public static void initialize() {
		reloadNow(null);
	}

	public static void reset() {
		settings = Settings.defaults();
		tablesById = Map.of();
		tablesByFileKey = Map.of();
		nextReloadAtMillis = 0L;
	}

	public static boolean applyManagedLootTable(
		Container container,
		ResourceKey<LootTable> lootTableKey,
		long lootTableSeed,
		ServerLevel level,
		ServerPlayer player
	) {
		if (container == null || lootTableKey == null || level == null) {
			return false;
		}

		reloadIfNeeded(level.getServer());
		Settings activeSettings = settings;
		if (!activeSettings.enabled || !activeSettings.overrideEntityLootTables) {
			return false;
		}

		String tableId = normalizeTableId(lootTableKey.identifier().toString());
		MadokuLootTableManager.SharedLootTable managed = resolveManagedTableByLootId(tableId);
		if (managed == null) {
			return false;
		}

		RandomSource random = createRandom(level, lootTableSeed);
		List<ItemStack> generated = MadokuLootTableManager.rollSharedTable(
			managed, random, player, activeSettings.useMadokuLuck
		);
		fillContainer(container, generated, random);
		return true;
	}

	public static List<ItemStack> generateManagedLootForContext(LootContext lootContext) {
		if (lootContext == null) {
			return null;
		}

		ServerLevel contextLevel = lootContext.getLevel();
		reloadIfNeeded(contextLevel == null ? null : contextLevel.getServer());
		Settings activeSettings = settings;
		if (!activeSettings.enabled || !activeSettings.overrideEntityLootTables) {
			return null;
		}

		LivingEntity thisEntity = resolveLootContextParameter(lootContext, "THIS_ENTITY", LivingEntity.class);
		String tableId = resolveQueriedLootTableId(lootContext);
		if (tableId.isBlank()) {
			tableId = resolveEntityLootTableId(thisEntity);
		}

		MadokuLootTableManager.SharedLootTable managed = null;
		boolean zombieEntity = thisEntity != null && isZombieMobType(thisEntity.getType());
		String zombieConfiguredReference = "";
		boolean zombieCustomDropsEnabled = false;
		if (thisEntity != null && thisEntity.getType() == madoku.craft.entity.MadokuEntityTypes.BEE && MobEntityManager.isEnabled()) {
			if (!MobEntityManager.isBeeCustomMobDropsEnabled(thisEntity)) {
				return null;
			}
			String configuredReference = MobEntityManager.resolveBeeMobDropsConfigReference(thisEntity);
			managed = resolveManagedTableByConfigReference(configuredReference);
		}
		if (managed == null && thisEntity != null && MobEntityManager.isEnabled() && zombieEntity) {
			zombieCustomDropsEnabled = MobEntityManager.isZombieCustomMobDropsEnabled(thisEntity);
			if (zombieCustomDropsEnabled) {
				zombieConfiguredReference = MobEntityManager.resolveZombieMobDropsConfigReference(thisEntity);
				managed = resolveManagedTableByConfigReference(zombieConfiguredReference);
			}
		}
		if (managed == null) {
			managed = resolveManagedTableByLootId(tableId);
		}
		if (managed == null) {
			return null;
		}

		RandomSource random = lootContext.getRandom();
		if (random == null) {
			ServerLevel level = lootContext.getLevel();
			random = level == null ? RandomSource.create() : level.getRandom();
		}
		ServerPlayer player = MadokuLuckManager.resolveLootPlayer(lootContext);
		return applySheepDropBehavior(
			MadokuLootTableManager.rollSharedTable(managed, random, player, activeSettings.useMadokuLuck),
			thisEntity
		);
	}

	public static List<ItemStack> generateManagedLootForReference(String configuredReference, ServerPlayer player, RandomSource random) {
		reloadIfNeeded(null);
		Settings activeSettings = settings;
		if (!activeSettings.enabled || !activeSettings.overrideEntityLootTables) {
			return null;
		}
		MadokuLootTableManager.SharedLootTable managed = resolveManagedTableByConfigReference(configuredReference);
		if (managed == null) {
			return null;
		}
		RandomSource resolvedRandom = random == null ? RandomSource.create() : random;
		ObjectArrayList<ItemStack> generated = new ObjectArrayList<>(
			MadokuLootTableManager.rollSharedTable(managed, resolvedRandom, player, activeSettings.useMadokuLuck)
		);
		MadokuLuckManager.applyManagedMobDrops(player, resolvedRandom, generated);
		return List.copyOf(generated);
	}

	private static List<ItemStack> applySheepDropBehavior(List<ItemStack> generated, LivingEntity entity) {
		if (!(entity instanceof Sheep sheep) || generated == null || generated.isEmpty()) {
			return generated;
		}

		ItemStack wool = new ItemStack(Items.WOOL.pick(sheep.getColor()));
		List<ItemStack> adjusted = new ArrayList<>(generated.size());
		for (ItemStack stack : generated) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			if (Items.WOOL.asList().contains(stack.getItem())) {
				if (!sheep.isSheared()) {
					adjusted.add(stack.transmuteCopy(wool.getItem(), stack.getCount()));
				}
				continue;
			}
			adjusted.add(stack);
		}
		return List.copyOf(adjusted);
	}



	private static boolean isZombieMobType(EntityType<?> type) {
		return type == madoku.craft.entity.MadokuEntityTypes.ZOMBIE
			|| type == madoku.craft.entity.MadokuEntityTypes.HUSK
			|| type == madoku.craft.entity.MadokuEntityTypes.DROWNED
			|| type == madoku.craft.entity.MadokuEntityTypes.ZOMBIE_VILLAGER;
	}

	private static void fillContainer(Container container, List<ItemStack> generated, RandomSource random) {
		if (container == null) {
			return;
		}
		if (generated == null || generated.isEmpty()) {
			container.setChanged();
			return;
		}

		int containerSize = container.getContainerSize();
		if (containerSize <= 0) {
			container.setChanged();
			return;
		}
		List<Integer> targetSlots = new ArrayList<>(containerSize);
		for (int slot = 0; slot < containerSize; slot++) {
			targetSlots.add(slot);
		}

		if (random != null) {
			for (int index = targetSlots.size() - 1; index > 0; index--) {
				int swap = random.nextInt(index + 1);
				Collections.swap(targetSlots, index, swap);
			}
		}

		int maxPlacements = Math.min(targetSlots.size(), generated.size());
		for (int index = 0; index < maxPlacements; index++) {
			ItemStack stack = generated.get(index);
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			container.setItem(targetSlots.get(index), stack.copy());
		}
		container.setChanged();
	}

	private static RandomSource createRandom(ServerLevel level, long lootTableSeed) {
		if (lootTableSeed != 0L) {
			return RandomSource.create(lootTableSeed);
		}
		return level == null ? RandomSource.create() : level.getRandom();
	}

	private static String resolveQueriedLootTableId(LootContext lootContext) {
		Object value = invokeNoArgMethodIfPresent(lootContext, "getQueriedLootTableId");
		String fromMethod = normalizeIdentifierObject(value);
		if (!fromMethod.isBlank()) {
			return fromMethod;
		}

		value = invokeNoArgMethodIfPresent(lootContext, "queriedLootTableId");
		fromMethod = normalizeIdentifierObject(value);
		if (!fromMethod.isBlank()) {
			return fromMethod;
		}

		value = readFieldIfPresent(lootContext, "queriedLootTableId");
		return normalizeIdentifierObject(value);
	}

	private static String normalizeIdentifierObject(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof Optional<?> optional) {
			if (optional.isEmpty()) {
				return "";
			}
			return normalizeIdentifierObject(optional.get());
		}
		if (value instanceof ResourceKey<?> key) {
			return normalizeTableId(key.identifier().toString());
		}

		String candidate = value.toString();
		if (candidate == null || candidate.isBlank()) {
			return "";
		}
		String trimmed = candidate.trim();
		if (trimmed.startsWith("Optional[") && trimmed.endsWith("]")) {
			trimmed = trimmed.substring("Optional[".length(), trimmed.length() - 1);
		}
		var parsed = net.minecraft.resources.Identifier.tryParse(trimmed.toLowerCase(Locale.ROOT));
		return parsed == null ? "" : parsed.toString();
	}

	private static Object invokeNoArgMethodIfPresent(Object target, String methodName) {
		if (target == null || methodName == null || methodName.isBlank()) {
			return null;
		}
		try {
			Method method = target.getClass().getMethod(methodName);
			method.setAccessible(true);
			return method.invoke(target);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}

	private static Object readFieldIfPresent(Object target, String fieldName) {
		if (target == null || fieldName == null || fieldName.isBlank()) {
			return null;
		}
		try {
			Field field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}

	private static <T> T resolveLootContextParameter(LootContext lootContext, String fieldName, Class<T> targetType) {
		if (lootContext == null || fieldName == null || fieldName.isBlank() || targetType == null) {
			return null;
		}

		Object parameter = resolveLootContextParameterKey(fieldName);
		if (parameter == null) {
			return null;
		}

		try {
			Method hasParameter = findLootContextMethod(lootContext.getClass(), "hasParameter", parameter.getClass());
			if (hasParameter != null) {
				Object present = hasParameter.invoke(lootContext, parameter);
				if (!(present instanceof Boolean) || !((Boolean) present)) {
					return null;
				}
			}

			for (String methodName : new String[] { "getParameter", "getParam", "getOptionalParameter", "getParamOrNull", "get" }) {
				Method method = findLootContextMethod(lootContext.getClass(), methodName, parameter.getClass());
				if (method == null) {
					continue;
				}

				Object value = method.invoke(lootContext, parameter);
				if (targetType.isInstance(value)) {
					return targetType.cast(value);
				}
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}

		return null;
	}

	private static Object resolveLootContextParameterKey(String fieldName) {
		try {
			Field field = LootContextParams.class.getField(fieldName);
			return field.get(null);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}

	private static Method findLootContextMethod(Class<?> type, String name, Class<?> parameterType) {
		if (type == null || name == null || parameterType == null) {
			return null;
		}

		for (Method method : type.getMethods()) {
			if (!name.equals(method.getName()) || method.getParameterCount() != 1) {
				continue;
			}
			Class<?> candidateType = method.getParameterTypes()[0];
			if (candidateType.isAssignableFrom(parameterType) || parameterType.isAssignableFrom(candidateType)) {
				return method;
			}
		}
		return null;
	}

	private static void reloadIfNeeded(net.minecraft.server.MinecraftServer server) {
		long now = System.currentTimeMillis();
		if (now < nextReloadAtMillis) {
			return;
		}
		reloadNow(server);
	}

	private static synchronized void reloadNow(net.minecraft.server.MinecraftServer server) {
		long now = System.currentTimeMillis();
		try {
			Path rootDirectory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(LOOT_CONFIG_ROOT_FOLDER_NAME);
			Path settingsFile = resolveJsonFile(rootDirectory, LOOT_CONFIG_SETTINGS_FILE_NAME);
			JsonObject defaults = LootTableConfigManager.buildSettingsDefaults();
			JsonObject normalizedSettings = JSONFormatManager.ensureManagedFile(settingsFile, defaults);
			Settings loadedSettings = Settings.fromJson(normalizedSettings);
			JSONFormatManager.writeManagedFile(settingsFile, loadedSettings.toConfigJson(), defaults);

			Path tablesDirectory = rootDirectory.resolve(LOOT_CONFIG_TABLES_FOLDER_NAME);
			Map<String, JsonObject> staticDefaults = buildEntityStaticDefaults();

			Map<String, JsonObject> normalizedFiles = JSONFormatManager.ensureManagedFolder(
				tablesDirectory,
				staticDefaults,
				ignored -> new JsonObject(),
				LootTableEntitiesManager::isSupportedLootTableFile,
				LootTableEntitiesManager::copyDynamicEntry
			);

			Map<String, MadokuLootTableManager.SharedLootTable> resolvedTables = new HashMap<>();
			Map<String, MadokuLootTableManager.SharedLootTable> resolvedFileTables = new HashMap<>();
			for (Map.Entry<String, JsonObject> entry : normalizedFiles.entrySet()) {
				String fileKey = normalizeFileKey(entry.getKey());
				JsonObject tableRoot = entry.getValue();
				MadokuLootTableManager.SharedLootTable table = MadokuLootTableManager.parseSharedTable(tableRoot);
				if (table == null) {
					continue;
				}
				resolvedTables.put(table.tableId(), table);
				if (!fileKey.isBlank()) {
					resolvedFileTables.put(fileKey, table);
				}
			}

			settings = loadedSettings;
			tablesById = Map.copyOf(resolvedTables);
			tablesByFileKey = Map.copyOf(resolvedFileTables);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to reload Madoku loot tables; preserving last valid cache.", exception);
		} finally {
			nextReloadAtMillis = now + MadokuLootTableManager.resolveReloadIntervalMillis(server);
		}
	}

	private static boolean isSupportedLootTableFile(String fileKey, JsonObject sourceRoot) {
		if (sourceRoot == null || sourceRoot.isEmpty()) {
			return false;
		}
		if (!readBoolean(sourceRoot, LootTableConfigManager.FIELD_ENABLED, true)) {
			return true;
		}
		String tableId = normalizeTableId(readString(sourceRoot, LootTableConfigManager.FIELD_TABLE_ID, ""));
		return !tableId.isBlank();
	}

	private static JsonElement copyDynamicEntry(String key, JsonElement sourceValue) {
		if (sourceValue == null || sourceValue.isJsonNull()) {
			return null;
		}
		return sourceValue.deepCopy();
	}

	private static Map<String, JsonObject> buildEntityStaticDefaults() {
		return EntitiesConfigManager.buildDefaultEntityTableFiles();
	}

	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Loot config file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private static String normalizeTableId(String tableId) {
		if (tableId == null || tableId.isBlank()) {
			return "";
		}
		return MadokuJSONManager.normalizeRegistryIdentifierForLookup(tableId);
	}

	private static String resolveEntityLootTableId(LivingEntity entity) {
		if (entity == null) {
			return "";
		}
		var entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		if (entityId == null) {
			return "";
		}
		return normalizeTableId(entityId.getNamespace() + ":entities/" + entityId.getPath());
	}

	private static String normalizeFileKey(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
		int slashIndex = normalized.lastIndexOf('/');
		if (slashIndex >= 0 && slashIndex < normalized.length() - 1) {
			normalized = normalized.substring(slashIndex + 1);
		}
		if (normalized.endsWith(".json")) {
			normalized = normalized.substring(0, normalized.length() - ".json".length());
		}
		return normalized;
	}

	private static MadokuLootTableManager.SharedLootTable resolveManagedTableByLootId(String rawLootTableId) {
		String normalized = normalizeTableId(rawLootTableId);
		if (normalized.isBlank()) {
			return null;
		}
		String canonicalStructureId = resolveEntityCanonicalId(normalized);
		if (!canonicalStructureId.isBlank()) {
			return tablesById.get(canonicalStructureId);
		}
		return tablesById.get(normalized);
	}

	private static MadokuLootTableManager.SharedLootTable resolveManagedTableByConfigReference(String rawReference) {
		if (rawReference == null || rawReference.isBlank()) {
			return null;
		}
		String fileKey = normalizeFileKey(rawReference);
		if (!fileKey.isBlank()) {
			MadokuLootTableManager.SharedLootTable fromFileKey = tablesByFileKey.get(fileKey);
			if (fromFileKey != null) {
				return fromFileKey;
			}
		}
		return resolveManagedTableByLootId(rawReference);
	}

	private static String resolveEntityCanonicalId(String rawLootTableId) {
		var identifier = net.minecraft.resources.Identifier.tryParse(normalizeTableId(rawLootTableId));
		if (identifier == null) {
			return "";
		}
		if (!ENTITY_LOOT_NAMESPACE.equals(identifier.getNamespace())) {
			return "";
		}
		String path = identifier.getPath();
		if (path == null || path.isBlank()) {
			return "";
		}
		if (path.startsWith("entities/")) {
			return ENTITY_LOOT_PREFIX + path.substring("entities/".length());
		}
		return "";
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null || key == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
			return fallback;
		}
		return primitive.getAsBoolean();
	}

	private static String readString(JsonObject root, String key, String fallback) {
		if (root == null || key == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
			return fallback;
		}
		return primitive.getAsString();
	}

	private static final class Settings {
		private final boolean enabled;
		private final boolean useMadokuLuck;
		private final boolean overrideStructureLootTables;
		private final boolean overrideEntityLootTables;

		private Settings(
			boolean enabled,
			boolean useMadokuLuck,
			boolean overrideStructureLootTables,
			boolean overrideEntityLootTables
		) {
			this.enabled = enabled;
			this.useMadokuLuck = useMadokuLuck;
			this.overrideStructureLootTables = overrideStructureLootTables;
			this.overrideEntityLootTables = overrideEntityLootTables;
		}

		private static Settings defaults() {
			JsonObject defaults = LootTableConfigManager.buildSettingsDefaults();
			return new Settings(
				readBoolean(defaults, LootTableConfigManager.FIELD_ENABLED, true),
				readBoolean(defaults, LootTableConfigManager.FIELD_USE_MADOKU_LUCK, true),
				readBoolean(defaults, LootTableConfigManager.FIELD_OVERRIDE_STRUCTURE_LOOT_TABLES, true),
				readBoolean(defaults, LootTableConfigManager.FIELD_OVERRIDE_ENTITY_LOOT_TABLES, true)
			);
		}

		private static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();

			return new Settings(
				readBoolean(source, LootTableConfigManager.FIELD_ENABLED, defaults.enabled),
				readBoolean(source, LootTableConfigManager.FIELD_USE_MADOKU_LUCK, defaults.useMadokuLuck),
				readBoolean(
					source,
					LootTableConfigManager.FIELD_OVERRIDE_STRUCTURE_LOOT_TABLES,
					defaults.overrideStructureLootTables
				),
				readBoolean(
					source,
					LootTableConfigManager.FIELD_OVERRIDE_ENTITY_LOOT_TABLES,
					defaults.overrideEntityLootTables
				)
			);
		}

		private JsonObject toConfigJson() {
			return madoku.craft.api.json.JSONFormatManager.object()
				.put(LootTableConfigManager.FIELD_ENABLED, enabled)
				.put(LootTableConfigManager.FIELD_USE_MADOKU_LUCK, useMadokuLuck)
				.put(LootTableConfigManager.FIELD_OVERRIDE_STRUCTURE_LOOT_TABLES, overrideStructureLootTables)
				.put(LootTableConfigManager.FIELD_OVERRIDE_ENTITY_LOOT_TABLES, overrideEntityLootTables)
				.build();
		}

	}
}







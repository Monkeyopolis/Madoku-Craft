package madoku.craft.api.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.MadokuJSONManager;
import madoku.craft.api.rarity.MadokuRarityManager;
import madoku.craft.api.rarity.RarityTierManager;
import madoku.craft.api.rarity.RarityTierManager.Tier;
import madoku.craft.attributes.MadokuLuckManager;
import madoku.craft.pet.PetHagManager;
import madoku.craft.pet.PetConfigManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class LootTableStructuresManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(LootTableStructuresManager.class);

	private static final String LOOT_CONFIG_ROOT_FOLDER_NAME = MadokuLootTableManager.CONFIG_ROOT_FOLDER_NAME;
	private static final String LOOT_CONFIG_SETTINGS_FILE_NAME = "madoku-loot-tables";
	private static final String LOOT_CONFIG_TABLES_FOLDER_NAME = "madoku-structures";
	private static final String STRUCTURE_CHEST_NAMESPACE = "minecraft";
	private static final String STRUCTURE_CHEST_PREFIX = "minecraft:structure_chests/";
	private static final String GROUP_TAG_MADOKU_PETS = "madoku-pets";
	private static final String GROUP_TAG_MADOKU_LUCK = "madoku-luck";
	private static final String GROUP_TAG_MADOKU_RARITY = "madoku-rarity";

	private static volatile Settings settings = Settings.defaults();
	private static volatile Map<String, ManagedLootTable> tablesById = Map.of();
	private static volatile long nextReloadAtMillis;

	private LootTableStructuresManager() {
	}

	public static void initialize() {
		reloadNow(null);
	}

	public static void reset() {
		settings = Settings.defaults();
		tablesById = Map.of();
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
		if (!activeSettings.enabled || !activeSettings.overrideStructureLootTables) {
			return false;
		}

		String tableId = normalizeTableId(lootTableKey.identifier().toString());
		ManagedLootTable managed = resolveManagedTableByLootId(tableId);
		if (managed == null) {
			return false;
		}

		RandomSource random = createRandom(level, lootTableSeed);
		List<ItemStack> generated = rollManagedLootTable(managed, random, player, activeSettings);
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
		if (!activeSettings.enabled || !activeSettings.overrideStructureLootTables) {
			return null;
		}

		String tableId = resolveQueriedLootTableId(lootContext);
		if (tableId.isBlank()) {
			return null;
		}

		ManagedLootTable managed = resolveManagedTableByLootId(tableId);
		if (managed == null) {
			return null;
		}

		RandomSource random = lootContext.getRandom();
		if (random == null) {
			ServerLevel level = lootContext.getLevel();
			random = level == null ? RandomSource.create() : level.getRandom();
		}
		ServerPlayer player = resolveLootContextPlayer(lootContext);
		return rollManagedLootTable(managed, random, player, activeSettings);
	}

	private static void fillContainer(Container container, List<ItemStack> generated, RandomSource random) {
		if (container == null) {
			return;
		}
		if (generated == null || generated.isEmpty()) {
			container.setChanged();
			return;
		}
		List<ItemStack> consolidated = consolidateStacks(generated);
		if (consolidated.isEmpty()) {
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

		int maxPlacements = Math.min(targetSlots.size(), consolidated.size());
		for (int index = 0; index < maxPlacements; index++) {
			ItemStack stack = consolidated.get(index);
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			container.setItem(targetSlots.get(index), stack.copy());
		}
		container.setChanged();
	}

	private static List<ItemStack> consolidateStacks(List<ItemStack> generated) {
		List<ItemStack> consolidated = new ArrayList<>();
		for (ItemStack source : generated) {
			if (source == null || source.isEmpty()) {
				continue;
			}

			int remaining = source.getCount();
			int maxStackSize = Math.max(1, source.getMaxStackSize());
			for (ItemStack target : consolidated) {
				if (!ItemStack.isSameItemSameComponents(source, target) || target.getCount() >= maxStackSize) {
					continue;
				}

				int amount = Math.min(remaining, maxStackSize - target.getCount());
				target.grow(amount);
				remaining -= amount;
				if (remaining <= 0) {
					break;
				}
			}

			while (remaining > 0) {
				int amount = Math.min(maxStackSize, remaining);
				consolidated.add(source.copyWithCount(amount));
				remaining -= amount;
			}
		}
		return consolidated;
	}

	private static RandomSource createRandom(ServerLevel level, long lootTableSeed) {
		if (lootTableSeed != 0L) {
			return RandomSource.create(lootTableSeed);
		}
		return level == null ? RandomSource.create() : level.getRandom();
	}

	private static List<ItemStack> rollManagedLootTable(
		ManagedLootTable managed,
		RandomSource random,
		ServerPlayer player,
		Settings activeSettings
	) {
		boolean luckActive = isLuckActiveForLoot(player, activeSettings);
		double luckStat = resolveLuckStat(player, activeSettings);
		return rollTable(managed, random, luckStat, luckActive);
	}

	private static List<ItemStack> rollTable(
		ManagedLootTable table,
		RandomSource random,
		double luckStat,
		boolean luckActive
	) {
		if (table == null || random == null || table.groups().isEmpty()) {
			return List.of();
		}

		int minRolls = Math.max(0, table.minRolls());
		int maxRolls = Math.max(minRolls, table.maxRolls());
		int rolls = minRolls;
		if (maxRolls > minRolls) {
			rolls = minRolls + random.nextInt((maxRolls - minRolls) + 1);
		}
		rolls = applyRollLuckMultiplier(rolls, luckStat, luckActive, random);

		List<ItemStack> generated = new ArrayList<>(rolls);
		for (int roll = 0; roll < rolls; roll++) {
			ManagedLootGroup group = pickGroup(table.groups(), random, luckStat, luckActive);
			if (group == null) {
				continue;
			}

			ManagedLootEntry entry = pickEntry(group.entries(), random);
			if (entry == null || entry.item() == null) {
				continue;
			}

			int count = randomCount(entry.minCount(), entry.maxCount(), random);
			appendSingleStackForRoll(generated, entry.item(), count, entry.itemRarity());
		}
		return List.copyOf(generated);
	}

	private static ManagedLootGroup pickGroup(
		List<ManagedLootGroup> groups,
		RandomSource random,
		double luckStat,
		boolean luckActive
	) {
		if (groups == null || groups.isEmpty()) {
			return null;
		}

		double totalWeight = 0.0d;
		List<Double> effectiveWeights = new ArrayList<>(groups.size());
		for (ManagedLootGroup group : groups) {
			if (group == null
				|| group.weight() <= 0
				|| group.entries().isEmpty()
				|| !isGroupEnabledByTags(group.tags())) {
				effectiveWeights.add(0.0d);
				continue;
			}
			double rarityMultiplier = resolveGroupWeightMultiplier(group.rarity(), luckStat, luckActive);
			double weight = Math.max(0.0d, group.weight() * rarityMultiplier);
			effectiveWeights.add(weight);
			totalWeight += weight;
		}
		if (totalWeight <= 0.0d) {
			return null;
		}

		double pick = random.nextDouble() * totalWeight;
		double cursor = 0.0d;
		for (int index = 0; index < groups.size(); index++) {
			double weight = effectiveWeights.get(index);
			if (weight <= 0.0d) {
				continue;
			}
			cursor += weight;
			if (pick <= cursor) {
				return groups.get(index);
			}
		}
		return groups.getLast();
	}

	private static ManagedLootEntry pickEntry(List<ManagedLootEntry> entries, RandomSource random) {
		if (entries == null || entries.isEmpty()) {
			return null;
		}

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

	private static int randomCount(int minCount, int maxCount, RandomSource random) {
		int min = Math.max(1, minCount);
		int max = Math.max(min, maxCount);
		if (max == min) {
			return min;
		}
		return min + random.nextInt((max - min) + 1);
	}

	private static void appendSingleStackForRoll(List<ItemStack> into, Item item, int count, Tier itemRarity) {
		if (into == null || item == null || count <= 0) {
			return;
		}

		ItemStack probe = new ItemStack(item, 1);
		int maxStackSize = Math.max(1, probe.getMaxStackSize());
		int stackCount = Math.min(maxStackSize, count);
		ItemStack stack = new ItemStack(item, stackCount);
		if (itemRarity != null) {
			MadokuRarityManager.applyConfiguredRarity(stack, itemRarity);
		}
		PetHagManager.applyLore(stack);
		into.add(stack);
	}

	private static int applyRollLuckMultiplier(
		int baseRolls,
		double luckStat,
		boolean luckActive,
		RandomSource random
	) {
		if (baseRolls <= 0) {
			return 0;
		}
		double multiplier = resolveRollLuckMultiplier(luckStat, luckActive);
		if (!Double.isFinite(multiplier)) {
			return baseRolls;
		}
		double rawRolls = Math.max(0.0d, baseRolls * multiplier);
		int whole = (int) Math.floor(rawRolls);
		double fractional = rawRolls - whole;
		if (fractional > 0.0d && random != null && random.nextDouble() < fractional) {
			whole++;
		}
		return Math.max(0, whole);
	}

	private static double resolveRollLuckMultiplier(double luckStat, boolean luckActive) {
		if (!luckActive || !Double.isFinite(luckStat)) {
			return 1.0d;
		}
		return Math.max(0.0d, 1.0d + (Math.max(0.0d, luckStat) * 0.01d));
	}

	private static double resolveGroupWeightMultiplier(Tier rarity, double luckStat, boolean luckActive) {
		if (rarity == null) {
			return 0.0d;
		}
		return MadokuRarityManager.resolveWeightMultiplier(rarity, luckStat, luckActive);
	}

	private static boolean isLuckActiveForLoot(ServerPlayer player, Settings activeSettings) {
		return player != null
			&& activeSettings != null
			&& activeSettings.useMadokuLuck
			&& MadokuLuckManager.isEnabled();
	}

	private static double resolveLuckStat(ServerPlayer player, Settings activeSettings) {
		if (!isLuckActiveForLoot(player, activeSettings)) {
			return 0.0d;
		}
		return MadokuLuckManager.resolveLootLuckStat(player);
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

	private static ServerPlayer resolveLootContextPlayer(LootContext lootContext) {
		ServerPlayer player = resolveLootContextParameter(lootContext, "LAST_DAMAGE_PLAYER", ServerPlayer.class);
		if (player != null) {
			return player;
		}

		Entity attacker = resolveLootContextParameter(lootContext, "ATTACKING_ENTITY", Entity.class);
		if (attacker instanceof ServerPlayer serverPlayer) {
			return serverPlayer;
		}

		Entity directAttacker = resolveLootContextParameter(lootContext, "DIRECT_ATTACKING_ENTITY", Entity.class);
		if (directAttacker instanceof ServerPlayer serverPlayer) {
			return serverPlayer;
		}

		Entity thisEntity = resolveLootContextParameter(lootContext, "THIS_ENTITY", Entity.class);
		if (thisEntity instanceof ServerPlayer serverPlayer) {
			return serverPlayer;
		}

		Entity interactingEntity = resolveLootContextParameter(lootContext, "INTERACTING_ENTITY", Entity.class);
		if (interactingEntity instanceof ServerPlayer serverPlayer) {
			return serverPlayer;
		}

		return null;
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
			Map<String, JsonObject> staticDefaults = buildStructureChestStaticDefaults();

			Map<String, JsonObject> normalizedFiles = JSONFormatManager.ensureManagedFolder(
				tablesDirectory,
				staticDefaults,
				ignored -> new JsonObject(),
				LootTableStructuresManager::isSupportedLootTableFile,
				LootTableStructuresManager::copyDynamicEntry
			);

			Map<String, ManagedLootTable> resolvedTables = new HashMap<>();
			for (JsonObject tableRoot : normalizedFiles.values()) {
				ManagedLootTable table = parseTable(tableRoot);
				if (table == null) {
					continue;
				}
				resolvedTables.put(table.tableId(), table);
			}

			settings = loadedSettings;
			tablesById = Map.copyOf(resolvedTables);
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

	private static ManagedLootTable parseTable(JsonObject root) {
		if (root == null || !readBoolean(root, LootTableConfigManager.FIELD_ENABLED, true)) {
			return null;
		}

		String tableId = normalizeTableId(readString(root, LootTableConfigManager.FIELD_TABLE_ID, ""));
		if (tableId.isBlank()) {
			return null;
		}

		JsonObject rolls = readJsonObject(root, LootTableConfigManager.FIELD_ROLLS);
		int minRolls = Math.max(0, readInt(rolls, LootTableConfigManager.FIELD_MIN, 1));
		int maxRolls = Math.max(minRolls, readInt(rolls, LootTableConfigManager.FIELD_MAX, minRolls));

		List<ManagedLootGroup> groups = parseGroups(root.get(LootTableConfigManager.FIELD_GROUPS));
		if (groups.isEmpty()) {
			return null;
		}

		return new ManagedLootTable(tableId, minRolls, maxRolls, List.copyOf(groups));
	}

	private static Map<String, JsonObject> buildStructureChestStaticDefaults() {
		return StructuresConfigManager.buildDefaultStructureTableFiles();
	}

	private static List<ManagedLootGroup> parseGroups(JsonElement element) {
		if (!(element instanceof JsonArray groupsArray) || groupsArray.isEmpty()) {
			return List.of();
		}

		List<ManagedLootGroup> groups = new ArrayList<>();
		for (JsonElement entry : groupsArray) {
			if (!(entry instanceof JsonObject groupRoot)) {
				continue;
			}

			Tier parsedRarity = RarityTierManager.fromString(
				readString(groupRoot, LootTableConfigManager.FIELD_RARITY, Tier.COMMON.id())
			);
			if (parsedRarity == null) {
				parsedRarity = Tier.COMMON;
			}
			double weight = Math.max(0.0d, readDouble(groupRoot, LootTableConfigManager.FIELD_WEIGHT, 0.0d));
			if (weight <= 0.0d) {
				continue;
			}

			List<ManagedLootEntry> entries = parseEntries(groupRoot.get(LootTableConfigManager.FIELD_ENTRIES));
			if (entries.isEmpty()) {
				continue;
			}
			List<String> tags = parseGroupTags(groupRoot.get(LootTableConfigManager.FIELD_TAGS));
			groups.add(new ManagedLootGroup(parsedRarity, weight, List.copyOf(entries), tags));
		}
		return groups;
	}

	private static List<String> parseGroupTags(JsonElement element) {
		if (!(element instanceof JsonArray tagsArray) || tagsArray.isEmpty()) {
			return List.of();
		}

		Set<String> tags = new LinkedHashSet<>();
		for (JsonElement tagElement : tagsArray) {
			if (!(tagElement instanceof JsonPrimitive primitive) || !primitive.isString()) {
				continue;
			}
			String normalizedTag = normalizeGroupTag(primitive.getAsString());
			if (!normalizedTag.isBlank()) {
				tags.add(normalizedTag);
			}
		}
		return tags.isEmpty() ? List.of() : List.copyOf(tags);
	}

	private static boolean isGroupEnabledByTags(List<String> tags) {
		if (tags == null || tags.isEmpty()) {
			return true;
		}
		for (String tag : tags) {
			if (!isGroupTagEnabled(tag)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isGroupTagEnabled(String rawTag) {
		String tag = normalizeGroupTag(rawTag);
		if (tag.isBlank()) {
			return true;
		}
		return switch (tag) {
			case GROUP_TAG_MADOKU_PETS -> PetConfigManager.isEnabled();
			case GROUP_TAG_MADOKU_LUCK -> MadokuLuckManager.isEnabled();
			case GROUP_TAG_MADOKU_RARITY -> MadokuRarityManager.isEnabled();
			default -> true;
		};
	}

	private static String normalizeGroupTag(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private static List<ManagedLootEntry> parseEntries(JsonElement element) {
		if (!(element instanceof JsonArray entriesArray) || entriesArray.isEmpty()) {
			return List.of();
		}

		List<ManagedLootEntry> entries = new ArrayList<>();
		for (JsonElement rawEntry : entriesArray) {
			if (!(rawEntry instanceof JsonObject entryRoot)) {
				continue;
			}

			String itemId = readString(entryRoot, LootTableConfigManager.FIELD_ITEM, "");
			if (itemId.isBlank()) {
				itemId = readString(entryRoot, LootTableConfigManager.FIELD_BLOCK, "");
			}
			Item item = resolveItem(itemId);
			if (item == null) {
				continue;
			}

			int weight = Math.max(1, readInt(entryRoot, LootTableConfigManager.FIELD_WEIGHT, 1));
			int minCount = Math.max(1, readInt(entryRoot, LootTableConfigManager.FIELD_MIN_COUNT, 1));
			int maxCount = Math.max(minCount, readInt(entryRoot, LootTableConfigManager.FIELD_MAX_COUNT, minCount));
		Tier itemRarity = RarityTierManager.fromString(
				readString(entryRoot, LootTableConfigManager.FIELD_ITEM_RARITY, "")
			);
			entries.add(new ManagedLootEntry(item, weight, minCount, maxCount, itemRarity));
		}
		return entries;
	}

	private static Item resolveItem(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return null;
		}
		var identifier = net.minecraft.resources.Identifier.tryParse(
			MadokuJSONManager.normalizeRegistryIdentifierForLookup(itemId)
		);
		if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
			return null;
		}
		Item item = BuiltInRegistries.ITEM.getValue(identifier);
		return item == null ? null : item;
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

	private static ManagedLootTable resolveManagedTableByLootId(String rawLootTableId) {
		String normalized = normalizeTableId(rawLootTableId);
		if (normalized.isBlank()) {
			return null;
		}
		String canonicalStructureId = resolveStructureChestCanonicalId(normalized);
		if (!canonicalStructureId.isBlank()) {
			return tablesById.get(canonicalStructureId);
		}
		return tablesById.get(normalized);
	}

	private static String resolveStructureChestCanonicalId(String rawLootTableId) {
		var identifier = net.minecraft.resources.Identifier.tryParse(normalizeTableId(rawLootTableId));
		if (identifier == null) {
			return "";
		}
		if (!STRUCTURE_CHEST_NAMESPACE.equals(identifier.getNamespace())) {
			return "";
		}

		String path = identifier.getPath();
		if (path == null || !path.startsWith("chests/")) {
			return "";
		}
		String chestKey = path.substring("chests/".length());
		if (chestKey.isBlank()) {
			return "";
		}
		if (chestKey.startsWith("ancient_city")) {
			return STRUCTURE_CHEST_PREFIX + "ancient_city";
		}
		if (chestKey.startsWith("bastion_")) {
			return STRUCTURE_CHEST_PREFIX + "bastion_remnant";
		}
		if (chestKey.startsWith("shipwreck_")) {
			return STRUCTURE_CHEST_PREFIX + "shipwreck";
		}
		if (chestKey.startsWith("stronghold_")) {
			return STRUCTURE_CHEST_PREFIX + "stronghold";
		}
		if (chestKey.startsWith("underwater_ruin_")) {
			return STRUCTURE_CHEST_PREFIX + "underwater_ruin";
		}
		if (chestKey.startsWith("trial_chambers/") || chestKey.startsWith("trial_chambers_")) {
			return STRUCTURE_CHEST_PREFIX + "trial_chambers";
		}
		if (chestKey.startsWith("village/") || chestKey.startsWith("village_")) {
			return STRUCTURE_CHEST_PREFIX + "village";
		}

		return switch (chestKey) {
			case "abandoned_mineshaft" -> STRUCTURE_CHEST_PREFIX + "abandoned_mineshaft";
			case "buried_treasure" -> STRUCTURE_CHEST_PREFIX + "buried_treasure";
			case "desert_pyramid" -> STRUCTURE_CHEST_PREFIX + "desert_pyramid";
			case "end_city_treasure" -> STRUCTURE_CHEST_PREFIX + "end_city";
			case "igloo_chest" -> STRUCTURE_CHEST_PREFIX + "igloo";
			case "jungle_temple" -> STRUCTURE_CHEST_PREFIX + "jungle_temple";
			case "nether_bridge" -> STRUCTURE_CHEST_PREFIX + "nether_fortress";
			case "pillager_outpost" -> STRUCTURE_CHEST_PREFIX + "pillager_outpost";
			case "ruined_portal" -> STRUCTURE_CHEST_PREFIX + "ruined_portal";
			case "simple_dungeon" -> STRUCTURE_CHEST_PREFIX + "dungeon";
			case "spawn_bonus_chest" -> STRUCTURE_CHEST_PREFIX + "starter_chest";
			case "woodland_mansion" -> STRUCTURE_CHEST_PREFIX + "woodland_mansion";
			default -> "";
		};
	}

	private static JsonObject readJsonObject(JsonObject root, String key) {
		if (root == null || key == null) {
			return new JsonObject();
		}
		JsonElement element = root.get(key);
		if (element instanceof JsonObject object) {
			return object;
		}
		return new JsonObject();
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

	private static int readInt(JsonObject root, String key, int fallback) {
		if (root == null || key == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
			return fallback;
		}
		return primitive.getAsInt();
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		if (root == null || key == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
			return fallback;
		}
		return primitive.getAsDouble();
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

	private record ManagedLootTable(String tableId, int minRolls, int maxRolls, List<ManagedLootGroup> groups) {
	}

	private record ManagedLootGroup(
		Tier rarity,
		double weight,
		List<ManagedLootEntry> entries,
		List<String> tags
	) {
	}

	private record ManagedLootEntry(Item item, int weight, int minCount, int maxCount, Tier itemRarity) {
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




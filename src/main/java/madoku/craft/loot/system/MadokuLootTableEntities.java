package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import madoku.craft.config.DynamicStaticSystem;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.luck.MadokuLuckManager;
import madoku.craft.mob.system.MadokuMobManager;
import madoku.craft.pet.PlayerEntitiesSystem;
import madoku.craft.rarity.MadokuRarity;
import madoku.craft.rarity.MadokuRarityTier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MadokuLootTableEntities {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuLootTableEntities.class);

	private static final String LOOT_CONFIG_ROOT_FOLDER_NAME = "madoku-craft-loot-tables";
	private static final String LOOT_CONFIG_SETTINGS_FILE_NAME = "madoku-loot-tables";
	private static final String LOOT_CONFIG_TABLES_FOLDER_NAME = "madoku-entities";
	private static final String ENTITY_LOOT_NAMESPACE = "minecraft";
	private static final String ENTITY_LOOT_PREFIX = "minecraft:entities/";
	private static final String GROUP_TAG_MADOKU_PETS = "madoku-pets";
	private static final String GROUP_TAG_MADOKU_LUCK = "madoku-luck";
	private static final String GROUP_TAG_MADOKU_RARITY = "madoku-rarity";
	private static final String METRIC_ZOMBIE_DROPS_RESOLUTION = "mob.zombie_drop_loot_resolution";
	private static final String METRIC_LOOT_CONTEXT_PROBE = "mob.loot_context_probe";
	private static final long RELOAD_INTERVAL_MILLIS = 1_500L;

	private static volatile Settings settings = Settings.defaults();
	private static volatile Map<String, ManagedLootTable> tablesById = Map.of();
	private static volatile Map<String, ManagedLootTable> tablesByFileKey = Map.of();
	private static volatile long nextReloadAtMillis;

	private MadokuLootTableEntities() {
	}

	public static void initialize() {
		reloadNow();
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

		reloadIfNeeded();
		Settings activeSettings = settings;
		if (!activeSettings.enabled || !activeSettings.overrideEntityLootTables) {
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
			emitLootContextProbe(null, "skip_null_context", "", null, false, "", "none", null, true);
			return null;
		}

		reloadIfNeeded();
		Settings activeSettings = settings;
		if (!activeSettings.enabled || !activeSettings.overrideEntityLootTables) {
			emitLootContextProbe(lootContext, "skip_settings_disabled", "", null, false, "", "none", null, true);
			return null;
		}

		String tableId = resolveQueriedLootTableId(lootContext);
		if (tableId.isBlank()) {
			emitLootContextProbe(lootContext, "skip_blank_table_id", tableId, null, false, "", "none", null, true);
			return null;
		}

		ManagedLootTable managed = null;
		LivingEntity thisEntity = resolveLootContextParameter(lootContext, "THIS_ENTITY", LivingEntity.class);
		boolean zombieEntity = thisEntity != null && isZombieMobType(thisEntity.getType());
		String zombieConfiguredReference = "";
		boolean zombieCustomDropsEnabled = false;
		String zombieResolutionSource = "none";
		if (thisEntity != null && thisEntity.getType() == madoku.craft.entity.MadokuEntityTypes.BEE && MadokuMobManager.isEnabled()) {
			if (!MadokuMobManager.isBeeCustomMobDropsEnabled(thisEntity)) {
				emitLootContextProbe(
					lootContext,
					"skip_bee_custom_drops_disabled",
					tableId,
					thisEntity,
					false,
					"",
					"bee_custom_drops_disabled",
					null,
					true
				);
				return null;
			}
			String configuredReference = MadokuMobManager.resolveBeeMobDropsConfigReference(thisEntity);
			managed = resolveManagedTableByConfigReference(configuredReference);
		}
		if (managed == null && thisEntity != null && MadokuMobManager.isEnabled() && zombieEntity) {
			zombieCustomDropsEnabled = MadokuMobManager.isZombieCustomMobDropsEnabled(thisEntity);
			if (zombieCustomDropsEnabled) {
				zombieConfiguredReference = MadokuMobManager.resolveZombieMobDropsConfigReference(thisEntity);
				managed = resolveManagedTableByConfigReference(zombieConfiguredReference);
				zombieResolutionSource = managed != null ? "zombie_config_reference" : "zombie_config_reference_missing";
			} else {
				zombieResolutionSource = "zombie_custom_drops_disabled";
			}
		}
		if (managed == null) {
			managed = resolveManagedTableByLootId(tableId);
			if (zombieEntity && managed != null) {
				zombieResolutionSource = "loot_table_id_fallback";
			}
		}
		if (managed == null) {
			if (zombieEntity) {
				emitZombieDropsResolutionDebug(
					thisEntity,
					tableId,
					zombieConfiguredReference,
					zombieCustomDropsEnabled,
					zombieResolutionSource,
					"none"
				);
			}
			emitLootContextProbe(
				lootContext,
				"no_managed_table",
				tableId,
				thisEntity,
				zombieCustomDropsEnabled,
				zombieConfiguredReference,
				zombieResolutionSource,
				null,
				true
			);
			return null;
		}
		if (zombieEntity) {
			emitZombieDropsResolutionDebug(
				thisEntity,
				tableId,
				zombieConfiguredReference,
				zombieCustomDropsEnabled,
				zombieResolutionSource,
				managed.tableId()
			);
		}
		emitLootContextProbe(
			lootContext,
			"managed_table_resolved",
			tableId,
			thisEntity,
			zombieCustomDropsEnabled,
			zombieConfiguredReference,
			zombieResolutionSource,
			managed,
			false
		);

		RandomSource random = lootContext.getRandom();
		if (random == null) {
			ServerLevel level = lootContext.getLevel();
			random = level == null ? RandomSource.create() : level.getRandom();
		}
		ServerPlayer player = resolveLootContextPlayer(lootContext);
		return rollManagedLootTable(managed, random, player, activeSettings);
	}

	public static List<ItemStack> generateManagedLootForReference(String configuredReference, ServerPlayer player, RandomSource random) {
		reloadIfNeeded();
		Settings activeSettings = settings;
		if (!activeSettings.enabled || !activeSettings.overrideEntityLootTables) {
			return null;
		}
		ManagedLootTable managed = resolveManagedTableByConfigReference(configuredReference);
		if (managed == null) {
			return null;
		}
		RandomSource resolvedRandom = random == null ? RandomSource.create() : random;
		return rollManagedLootTable(managed, resolvedRandom, player, activeSettings);
	}

	private static void emitZombieDropsResolutionDebug(
		LivingEntity entity,
		String queriedLootTableId,
		String configuredReference,
		boolean customDropsEnabled,
		String resolutionSource,
		String resolvedManagedTableId
	) {
		if (entity == null || !MadokuDebug.shouldEmit("loot", "entities", "entities")) {
			return;
		}
		MadokuDebug.EventBuilder event = MadokuDebug.event(METRIC_ZOMBIE_DROPS_RESOLUTION, "loot", "entities", "entities")
			.side(MadokuDebug.Side.SERVER)
			.subject("zombie:" + entity.getUUID())
			.field("mob_type", entity.getType().toShortString())
			.field("is_baby", entity instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie && zombie.isBaby())
			.field("custom_drops_enabled", customDropsEnabled)
			.field("configured_reference", configuredReference == null || configuredReference.isBlank() ? "unset" : configuredReference)
			.field("queried_loot_table_id", queriedLootTableId == null || queriedLootTableId.isBlank() ? "unset" : queriedLootTableId)
			.field("resolution_source", resolutionSource == null || resolutionSource.isBlank() ? "unknown" : resolutionSource)
			.field("resolved_managed_table", resolvedManagedTableId == null || resolvedManagedTableId.isBlank() ? "none" : resolvedManagedTableId);
		if (entity.level() instanceof ServerLevel level) {
			event.tick(level.getGameTime()).world(level.dimension().toString());
		}
		event.log();
	}

	private static void emitLootContextProbe(
		LootContext lootContext,
		String phase,
		String queriedLootTableId,
		LivingEntity thisEntity,
		boolean customDropsEnabled,
		String configuredReference,
		String resolutionSource,
		ManagedLootTable managed,
		boolean vanillaFallback
	) {
		if (!MadokuDebug.shouldEmit("loot", "entities", "entities")) {
			return;
		}
		String subject = thisEntity == null ? "loot-context:no-entity" : "loot-context:" + thisEntity.getUUID();
		MadokuDebug.EventBuilder event = MadokuDebug.event(METRIC_LOOT_CONTEXT_PROBE, "loot", "entities", "entities")
			.side(MadokuDebug.Side.SERVER)
			.subject(subject)
			.field("phase", phase == null || phase.isBlank() ? "unknown" : phase)
			.field("queried_loot_table_id", queriedLootTableId == null || queriedLootTableId.isBlank() ? "unset" : queriedLootTableId)
			.field("has_this_entity", thisEntity != null)
			.field("this_entity_type", thisEntity == null ? "none" : thisEntity.getType().toShortString())
			.field("this_entity_is_baby", thisEntity instanceof net.minecraft.world.entity.AgeableMob ageableMob && ageableMob.isBaby())
			.field("is_zombie_entity", thisEntity != null && isZombieMobType(thisEntity.getType()))
			.field("mob_system_enabled", MadokuMobManager.isEnabled())
			.field("custom_drops_enabled", customDropsEnabled)
			.field("configured_reference", configuredReference == null || configuredReference.isBlank() ? "unset" : configuredReference)
			.field("resolution_source", resolutionSource == null || resolutionSource.isBlank() ? "none" : resolutionSource)
			.field("resolved_managed_table", managed == null ? "none" : managed.tableId())
			.field("vanilla_fallback", vanillaFallback);
		ServerLevel level = lootContext == null ? null : lootContext.getLevel();
		if (level != null) {
			event.tick(level.getGameTime()).world(level.dimension().toString());
		}
		event.log();
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

	private static List<ItemStack> rollManagedLootTable(
		ManagedLootTable managed,
		RandomSource random,
		ServerPlayer player,
		Settings activeSettings
	) {
		boolean luckActive = isLuckActiveForLoot(player, activeSettings);
		double luckStat = resolveLuckStat(player, activeSettings);
		return rollTable(managed, random, luckStat, luckActive, activeSettings);
	}

	private static List<ItemStack> rollTable(
		ManagedLootTable table,
		RandomSource random,
		double luckStat,
		boolean luckActive,
		Settings activeSettings
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
		rolls = applyRollLuckMultiplier(rolls, luckStat, luckActive, activeSettings, random);

		List<ItemStack> generated = new ArrayList<>(rolls);
		for (int roll = 0; roll < rolls; roll++) {
			ManagedLootGroup group = pickGroup(table.groups(), random, luckStat, luckActive, activeSettings);
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
		boolean luckActive,
		Settings activeSettings
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
			double rarityMultiplier = resolveRarityLuckMultiplier(group.rarity(), luckStat, luckActive, activeSettings);
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

	private static void appendSingleStackForRoll(List<ItemStack> into, Item item, int count, MadokuRarityTier itemRarity) {
		if (into == null || item == null || count <= 0) {
			return;
		}

		ItemStack probe = new ItemStack(item, 1);
		int maxStackSize = Math.max(1, probe.getMaxStackSize());
		int stackCount = Math.min(maxStackSize, count);
		ItemStack stack = new ItemStack(item, stackCount);
		if (itemRarity != null) {
			MadokuRarity.applyConfiguredRarity(stack, itemRarity);
		}
		PlayerEntitiesSystem.applySupportedSpawnEggLore(stack);
		into.add(stack);
	}

	private static int applyRollLuckMultiplier(
		int baseRolls,
		double luckStat,
		boolean luckActive,
		Settings activeSettings,
		RandomSource random
	) {
		if (baseRolls <= 0) {
			return 0;
		}
		double multiplier = resolveRollLuckMultiplier(luckStat, luckActive, activeSettings);
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

	private static double resolveRarityLuckMultiplier(
		MadokuLootRarity rarity,
		double luckStat,
		boolean luckActive,
		Settings activeSettings
	) {
		if (!luckActive || rarity == null || activeSettings == null) {
			return 1.0d;
		}

		LuckCurve curve = activeSettings.rarityCurves.get(rarity);
		if (curve == null) {
			return 1.0d;
		}
		return Math.max(0.0d, curve.sample(luckStat));
	}

	private static double resolveRollLuckMultiplier(double luckStat, boolean luckActive, Settings activeSettings) {
		if (!luckActive || activeSettings == null || activeSettings.rollCurve == null) {
			return 1.0d;
		}
		return Math.max(0.0d, activeSettings.rollCurve.sample(luckStat));
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

	private static void reloadIfNeeded() {
		long now = System.currentTimeMillis();
		if (now < nextReloadAtMillis) {
			return;
		}
		reloadNow();
	}

	private static synchronized void reloadNow() {
		long now = System.currentTimeMillis();
		try {
			Path rootDirectory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(LOOT_CONFIG_ROOT_FOLDER_NAME);
			Path settingsFile = resolveJsonFile(rootDirectory, LOOT_CONFIG_SETTINGS_FILE_NAME);
			JsonObject defaults = LootTableConfigManager.buildSettingsDefaults();
			JsonObject normalizedSettings = JsonStaticSystem.ensureManagedFile(settingsFile, defaults);
			Settings loadedSettings = Settings.fromJson(normalizedSettings);
			JsonStaticSystem.writeManagedFile(settingsFile, loadedSettings.toConfigJson(), defaults);

			Path tablesDirectory = rootDirectory.resolve(LOOT_CONFIG_TABLES_FOLDER_NAME);
			Map<String, JsonObject> staticDefaults = buildEntityStaticDefaults();

			Map<String, JsonObject> normalizedFiles = DynamicStaticSystem.ensureManagedFolder(
				tablesDirectory,
				staticDefaults,
				ignored -> new JsonObject(),
				MadokuLootTableEntities::isSupportedLootTableFile,
				MadokuLootTableEntities::copyDynamicEntry
			);

			Map<String, ManagedLootTable> resolvedTables = new HashMap<>();
			Map<String, ManagedLootTable> resolvedFileTables = new HashMap<>();
			for (Map.Entry<String, JsonObject> entry : normalizedFiles.entrySet()) {
				String fileKey = normalizeFileKey(entry.getKey());
				JsonObject tableRoot = entry.getValue();
				ManagedLootTable table = parseTable(tableRoot);
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
			nextReloadAtMillis = now + RELOAD_INTERVAL_MILLIS;
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

	private static Map<String, JsonObject> buildEntityStaticDefaults() {
		return LootTableConfigEntities.buildDefaultEntityTableFiles();
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

			MadokuLootRarity rarity = MadokuLootRarity.fromString(
				readString(groupRoot, LootTableConfigManager.FIELD_RARITY, MadokuLootRarity.COMMON.id())
			);
			double weight = Math.max(0.0d, readDouble(groupRoot, LootTableConfigManager.FIELD_WEIGHT, 0.0d));
			if (weight <= 0.0d) {
				continue;
			}

			List<ManagedLootEntry> entries = parseEntries(groupRoot.get(LootTableConfigManager.FIELD_ENTRIES));
			if (entries.isEmpty()) {
				continue;
			}
			List<String> tags = parseGroupTags(groupRoot.get(LootTableConfigManager.FIELD_TAGS));
			groups.add(new ManagedLootGroup(rarity, weight, List.copyOf(entries), tags));
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
			case GROUP_TAG_MADOKU_PETS -> PlayerEntitiesSystem.isEnabled();
			case GROUP_TAG_MADOKU_LUCK -> MadokuLuckManager.isEnabled();
			case GROUP_TAG_MADOKU_RARITY -> MadokuRarity.isEnabled();
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
			MadokuRarityTier itemRarity = MadokuRarityTier.fromString(
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
		var identifier = net.minecraft.resources.Identifier.tryParse(itemId.trim().toLowerCase(Locale.ROOT));
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
		return tableId.trim().toLowerCase(Locale.ROOT);
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

	private static ManagedLootTable resolveManagedTableByLootId(String rawLootTableId) {
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

	private static ManagedLootTable resolveManagedTableByConfigReference(String rawReference) {
		if (rawReference == null || rawReference.isBlank()) {
			return null;
		}
		String fileKey = normalizeFileKey(rawReference);
		if (!fileKey.isBlank()) {
			ManagedLootTable fromFileKey = tablesByFileKey.get(fileKey);
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
		MadokuLootRarity rarity,
		double weight,
		List<ManagedLootEntry> entries,
		List<String> tags
	) {
	}

	private record ManagedLootEntry(Item item, int weight, int minCount, int maxCount, MadokuRarityTier itemRarity) {
	}

	private record LuckCurve(List<Double> points, List<Double> values) {
		private double sample(double x) {
			if (points == null || values == null || points.isEmpty() || values.isEmpty()) {
				return 1.0d;
			}
			if (points.size() != values.size()) {
				return values.getFirst();
			}
			if (points.size() == 1) {
				return values.getFirst();
			}

			double clampedX = x;
			double minPoint = points.getFirst();
			double maxPoint = points.getLast();
			if (clampedX <= minPoint) {
				return values.getFirst();
			}
			if (clampedX >= maxPoint) {
				return values.getLast();
			}

			for (int index = 1; index < points.size(); index++) {
				double right = points.get(index);
				double left = points.get(index - 1);
				if (clampedX > right) {
					continue;
				}

				double leftValue = values.get(index - 1);
				double rightValue = values.get(index);
				double range = right - left;
				if (range <= 0.0d) {
					return rightValue;
				}
				double t = (clampedX - left) / range;
				return leftValue + ((rightValue - leftValue) * t);
			}

			return values.getLast();
		}
	}

	private static final class Settings {
		private final boolean enabled;
		private final boolean useMadokuLuck;
		private final boolean overrideStructureLootTables;
		private final boolean overrideEntityLootTables;
		private final LuckCurve rollCurve;
		private final EnumMap<MadokuLootRarity, LuckCurve> rarityCurves;

		private Settings(
			boolean enabled,
			boolean useMadokuLuck,
			boolean overrideStructureLootTables,
			boolean overrideEntityLootTables,
			LuckCurve rollCurve,
			EnumMap<MadokuLootRarity, LuckCurve> rarityCurves
		) {
			this.enabled = enabled;
			this.useMadokuLuck = useMadokuLuck;
			this.overrideStructureLootTables = overrideStructureLootTables;
			this.overrideEntityLootTables = overrideEntityLootTables;
			this.rollCurve = rollCurve;
			this.rarityCurves = rarityCurves;
		}

		private static Settings defaults() {
			EnumMap<MadokuLootRarity, LuckCurve> curves = new EnumMap<>(MadokuLootRarity.class);
			JsonObject defaults = LootTableConfigManager.buildSettingsDefaults();
			for (MadokuLootRarity rarity : MadokuLootRarity.values()) {
				curves.put(rarity, defaultCurve(rarity));
			}
			LuckCurve rollCurve = defaultRollCurve();
			return new Settings(
				readBoolean(defaults, LootTableConfigManager.FIELD_ENABLED, true),
				readBoolean(defaults, LootTableConfigManager.FIELD_USE_MADOKU_LUCK, true),
				readBoolean(defaults, LootTableConfigManager.FIELD_OVERRIDE_STRUCTURE_LOOT_TABLES, true),
				readBoolean(defaults, LootTableConfigManager.FIELD_OVERRIDE_ENTITY_LOOT_TABLES, true),
				rollCurve,
				curves
			);
		}

		private static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			EnumMap<MadokuLootRarity, LuckCurve> curves = new EnumMap<>(MadokuLootRarity.class);
			for (MadokuLootRarity rarity : MadokuLootRarity.values()) {
				curves.put(rarity, defaults.rarityCurves.get(rarity));
			}
			LuckCurve rollCurve = defaults.rollCurve;

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
				),
				rollCurve,
				curves
			);
		}

		private JsonObject toConfigJson() {
			return madoku.craft.config.JsonFormatBuilder.object()
				.put(LootTableConfigManager.FIELD_ENABLED, enabled)
				.put(LootTableConfigManager.FIELD_USE_MADOKU_LUCK, useMadokuLuck)
				.put(LootTableConfigManager.FIELD_OVERRIDE_STRUCTURE_LOOT_TABLES, overrideStructureLootTables)
				.put(LootTableConfigManager.FIELD_OVERRIDE_ENTITY_LOOT_TABLES, overrideEntityLootTables)
				.build();
		}

		private static LuckCurve defaultRollCurve() {
			return new LuckCurve(
				List.of(0.0d, 25.0d, 50.0d, 75.0d, 100.0d),
				List.of(1.0d, 1.25d, 1.5d, 1.75d, 2.0d)
			);
		}

		private static LuckCurve defaultCurve(MadokuLootRarity rarity) {
			return switch (rarity) {
				case COMMON -> new LuckCurve(
					List.of(0.0d, 25.0d, 50.0d, 75.0d, 100.0d),
					List.of(1.0d, 0.875d, 0.75d, 0.625d, 0.5d)
				);
				case RARE -> new LuckCurve(
					List.of(0.0d, 25.0d, 50.0d, 75.0d, 100.0d),
					List.of(1.0d, 0.9375d, 0.875d, 0.8125d, 0.75d)
				);
				case EPIC -> new LuckCurve(
					List.of(0.0d, 25.0d, 50.0d, 75.0d, 100.0d),
					List.of(1.0d, 1.25d, 1.5d, 1.75d, 2.0d)
				);
				case MYTHIC -> new LuckCurve(
					List.of(0.0d, 25.0d, 50.0d, 75.0d, 100.0d),
					List.of(1.0d, 1.5d, 2.0d, 3.0d, 4.0d)
				);
			};
		}
	}
}






package madoku.craft.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import madoku.craft.mixin.item.ItemComponentsAccessor;
import madoku.craft.core.json.JSONFormatAPIManager;
import madoku.craft.core.json.JSONTypeAPIManager;
import madoku.craft.core.json.JSONAPIManager;
import madoku.craft.core.scheduler.SchedulerAPIManager;
import madoku.craft.core.sync.SyncConfigAPIManager;
import madoku.craft.mixin.item.ItemBuiltInRegistryHolderAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;
import net.minecraft.nbt.CompoundTag;

public final class ItemsCategoriesManager {
	private static final String DURABILITY_PREFIX = "Durability:";
	private static final String LEVEL_PREFIX = "Level:";
	private static final String ITEM_LEVEL_DATA_KEY = "madoku_item_level";
	private static final String ITEM_LEVEL_SCALING_DATA_KEY = "madoku_item_level_scaling";
	private static final String RARITY_SCALING_DATA_KEY = "madoku_rarity_scaling";
	private static final double ATTACK_DAMAGE_SCALING_FACTOR = 0.50D;
	private static final double ATTACK_SPEED_SCALING_FACTOR = 0.50D;
	private static final double MINING_SPEED_SCALING_FACTOR = 0.50D;
	private static final double ARMOR_SCALING_FACTOR = 0.50D;
	private static final double ARMOR_TOUGHNESS_SCALING_FACTOR = 0.50D;
	private static final Logger LOGGER = LoggerFactory.getLogger(ItemsCategoriesManager.class);
	private static final AttackRange DEFAULT_REACH = new AttackRange(0.0F, 3.0F, 0.0F, 5.0F, 0.3F, 1.0F);
	private static final int MAX_FUEL_TICKS = 201600;
	private static final long PLAYER_COMPONENT_SYNC_INTERVAL_TICKS = 5L;
	private static final String TASK_TYPE_ITEM_PLAYER_TICK = "item_player_tick";
	private static final String ITEM_PLAYER_TICK_SCHEDULER_KEY = "item_player_tick";
	private static final long ITEM_PLAYER_TICK_DELAY = 1L;

	private static final String ITEM_CONFIG_ROOT_FOLDER_NAME = "madoku-craft-items";
	private static final String ITEM_CONFIG_SETTINGS_FILE_NAME = "madoku-items";
	private static final String ITEM_CONFIG_ITEMS_FOLDER_NAME = "madoku-items";
	private static final String FIELD_ENABLED = "enabled";
	private static final String FIELD_SYNC_ITEM_LEVELS = "item-levels";
	private static final String FIELD_SYNC_STACKS_ENABLED = "stacks-enabled";
	private static final String FIELD_SYNC_STACK_LIMIT = "stack-limit";
	private static final String FIELD_SYNC_FUEL_TICKS = "fuel-ticks";
	private static final String FIELD_SYNC_STACK_MODES = "stack-modes";
	private static final String FIELD_SYNC_CATEGORIES = "categories";
	private static final String FIELD_SYNC_TOOL_ITEMS = "tool-items";
	private static final String FIELD_SYNC_WEAPON_ITEMS = "weapon-items";
	private static final String FIELD_SYNC_ARMOR_ITEMS = "armor-items";
	private static final String FIELD_SYNC_TOOL_PROFILES = "tool-profiles";
	private static final String FIELD_SYNC_ARMOR_PROFILES = "armor-profiles";
	private static final String FUEL_ITEMS_FOLDER_NAME = "items-fuel";
	private static final String OTHER_ITEMS_FOLDER_NAME = "items-other";
	private static final String TOOL_ITEMS_FOLDER_NAME = "items-tool";
	private static final String WEAPON_ITEMS_FOLDER_NAME = "items-weapon";
	private static final String ARMOR_ITEMS_FOLDER_NAME = "items-armor";

	private static volatile boolean enabled = true;
	private static volatile boolean itemLevelsEnabled = true;
	private static volatile int itemStartingLevel = 1;
	private static volatile int itemMaximumLevel = 5;
	private static volatile Map<Item, Integer> fuelTicksByItem = Map.of();
	private static volatile Map<Item, StackMode> stackModesByItem = Map.of();
	private static volatile Map<Item, CategoriesToolManager> toolProfilesByItem = Map.of();
	private static volatile Map<Item, CategoriesArmorManager> armorProfilesByItem = Map.of();
	private static volatile Map<Item, Set<String>> categoriesByItem = Map.of();
	private static volatile Set<Item> toolCategoryItems = Set.of();
	private static volatile Set<Item> weaponCategoryItems = Set.of();
	private static volatile Set<Item> armorCategoryItems = Set.of();
	private static volatile boolean clientSynchronized;
	private static boolean savedEnabled;
	private static boolean savedItemLevelsEnabled;
	private static int savedItemStartingLevel;
	private static int savedItemMaximumLevel;
	private static Map<Item, Integer> savedFuelTicksByItem = Map.of();
	private static Map<Item, StackMode> savedStackModesByItem = Map.of();
	private static Map<Item, CategoriesToolManager> savedToolProfilesByItem = Map.of();
	private static Map<Item, CategoriesArmorManager> savedArmorProfilesByItem = Map.of();
	private static Map<Item, Set<String>> savedCategoriesByItem = Map.of();
	private static Set<Item> savedToolCategoryItems = Set.of();
	private static Set<Item> savedWeaponCategoryItems = Set.of();
	private static Set<Item> savedArmorCategoryItems = Set.of();
	private static Map<Item, DataComponentMap> savedClientComponents = Map.of();
	private static volatile String schedulerId = "";
	private static volatile boolean tickQueued;

	private ItemsCategoriesManager() {
	}

	public static void initialize() {
		ItemsCategoriesAPIManager.registerProvider(new MadokuItemsCategoriesProvider());
		loadStaticConfig();
		SchedulerAPIManager.registerTaskHandler(TASK_TYPE_ITEM_PLAYER_TICK, ItemsCategoriesManager::runPlayerTickTask);
		SyncConfigAPIManager.register(
			"items",
			ItemsCategoriesManager::createClientSyncSnapshot,
			ItemsCategoriesManager::applySynchronizedProfiles,
			ItemsCategoriesManager::resetClientSynchronizedState
		);
	}

	public static void onServerStarted(MinecraftServer server) {
		applyConfiguredItemMetadata();
		ensureQueued(server, ITEM_PLAYER_TICK_DELAY);
	}

	public static void applyConfiguredItemMetadata() {
		if (!enabled) {
			return;
		}
		if (toolProfilesByItem.isEmpty() && armorProfilesByItem.isEmpty()) {
			return;
		}
		captureClientComponents(toolProfilesByItem.keySet());
		captureClientComponents(armorProfilesByItem.keySet());
		applyToolProfiles(toolProfilesByItem);
		applyArmorProfiles(armorProfilesByItem);
	}

	public static void reset() {
		schedulerId = "";
		tickQueued = false;
		savedClientComponents = Map.of();
		itemLevelsEnabled = true;
		itemStartingLevel = 1;
		itemMaximumLevel = 5;
	}

	private static void runPlayerTickTask(MinecraftServer server, SchedulerAPIManager.TaskContext context, JsonObject payload) {
		tickQueued = false;
		if (server == null || context == null) {
			return;
		}

		schedulerId = context.getSchedulerId();
		long gameplayTick = context.getNowTick();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			onPlayerTick(server, player, gameplayTick);
		}
		ensureQueued(server, ITEM_PLAYER_TICK_DELAY);
	}

	private static void ensureQueued(MinecraftServer server, long delayTicks) {
		if (server == null || tickQueued) {
			return;
		}

		String currentSchedulerId = ensureScheduler();
		if (SchedulerAPIManager.hasQueuedTask(currentSchedulerId, TASK_TYPE_ITEM_PLAYER_TICK)) {
			tickQueued = true;
			return;
		}
		if (enqueue(currentSchedulerId, delayTicks)) {
			tickQueued = true;
			return;
		}

		schedulerId = SchedulerAPIManager.createOrGetScheduler(
			SchedulerAPIManager.SchedulerBinding.global(ITEM_PLAYER_TICK_SCHEDULER_KEY)
		);
		if (enqueue(schedulerId, delayTicks)) {
			tickQueued = true;
			return;
		}

		LOGGER.error("Failed to enqueue item player tick task.");
	}

	private static String ensureScheduler() {
		String current = schedulerId;
		if (current != null && !current.isBlank()) {
			return current;
		}
		schedulerId = SchedulerAPIManager.createOrGetScheduler(
			SchedulerAPIManager.SchedulerBinding.global(ITEM_PLAYER_TICK_SCHEDULER_KEY)
		);
		return schedulerId;
	}

	private static boolean enqueue(String targetSchedulerId, long delayTicks) {
		if (targetSchedulerId == null || targetSchedulerId.isBlank()) {
			return false;
		}
		SchedulerAPIManager.EnqueueStatus status = SchedulerAPIManager.enqueue(
			targetSchedulerId,
			Math.max(0L, delayTicks),
			TASK_TYPE_ITEM_PLAYER_TICK,
			new JsonObject(),
			SchedulerAPIManager.TickDomain.GAMEPLAY
		);
		return status == SchedulerAPIManager.EnqueueStatus.ACCEPTED
			|| status == SchedulerAPIManager.EnqueueStatus.QUEUE_FULL;
	}

	public static String createClientSyncSnapshot() {
		JsonObject snapshot = JSONFormatAPIManager.object()
			.put(FIELD_ENABLED, enabled)
			.put(FIELD_SYNC_STACKS_ENABLED, ItemsStacksManager.isEnabled())
			.put(FIELD_SYNC_STACK_LIMIT, ItemsStacksManager.getStackLimit())
			.put(FIELD_SYNC_TOOL_PROFILES, writeToolProfilesSnapshot(toolProfilesByItem))
			.put(FIELD_SYNC_ARMOR_PROFILES, writeArmorProfilesSnapshot(armorProfilesByItem))
			.build();
		JsonObject itemLevels = new JsonObject();
		itemLevels.addProperty(ItemsConfigManager.FIELD_CATEGORY_ENABLED, itemLevelsEnabled);
		itemLevels.addProperty(ItemsConfigManager.FIELD_STARTING_LEVEL, itemStartingLevel);
		itemLevels.addProperty(ItemsConfigManager.FIELD_MAXIMUM_LEVEL, itemMaximumLevel);
		snapshot.add(FIELD_SYNC_ITEM_LEVELS, itemLevels);
		snapshot.add(FIELD_SYNC_FUEL_TICKS, writeFuelTicksSnapshot(fuelTicksByItem));
		snapshot.add(FIELD_SYNC_STACK_MODES, writeStackModesSnapshot(stackModesByItem));
		snapshot.add(FIELD_SYNC_CATEGORIES, writeCategoriesSnapshot(categoriesByItem));
		snapshot.add(FIELD_SYNC_TOOL_ITEMS, writeItemSetSnapshot(toolCategoryItems));
		snapshot.add(FIELD_SYNC_WEAPON_ITEMS, writeItemSetSnapshot(weaponCategoryItems));
		snapshot.add(FIELD_SYNC_ARMOR_ITEMS, writeItemSetSnapshot(armorCategoryItems));
		return snapshot.toString();
	}

	public static void applySynchronizedProfiles(String snapshotJson) {
		if (snapshotJson == null || snapshotJson.isBlank()) {
			return;
		}

		try {
			JsonElement parsed = JsonParser.parseString(snapshotJson);
			if (!parsed.isJsonObject()) {
				return;
			}

			JsonObject root = parsed.getAsJsonObject();
			captureClientStateIfNeeded();
			boolean syncedEnabled = readBoolean(root, FIELD_ENABLED, enabled);
			JsonObject syncedLevels = readJsonObject(root, FIELD_SYNC_ITEM_LEVELS);
			boolean syncedLevelsEnabled = readBoolean(syncedLevels, ItemsConfigManager.FIELD_CATEGORY_ENABLED, itemLevelsEnabled);
			int syncedStartingLevel = Math.max(1, readInt(syncedLevels, ItemsConfigManager.FIELD_STARTING_LEVEL, itemStartingLevel));
			int syncedMaximumLevel = Math.max(syncedStartingLevel,
				readInt(syncedLevels, ItemsConfigManager.FIELD_MAXIMUM_LEVEL, itemMaximumLevel));
			Map<Item, Integer> syncedFuelTicks = readFuelTicksSnapshot(readJsonObject(root, FIELD_SYNC_FUEL_TICKS));
			Map<Item, StackMode> syncedStackModes = readStackModesSnapshot(readJsonObject(root, FIELD_SYNC_STACK_MODES));
			Map<Item, Set<String>> syncedCategories = readCategoriesSnapshot(readJsonObject(root, FIELD_SYNC_CATEGORIES));
			Set<Item> syncedToolItems = readItemSetSnapshot(root.get(FIELD_SYNC_TOOL_ITEMS));
			Set<Item> syncedWeaponItems = readItemSetSnapshot(root.get(FIELD_SYNC_WEAPON_ITEMS));
			Set<Item> syncedArmorItems = readItemSetSnapshot(root.get(FIELD_SYNC_ARMOR_ITEMS));
			Map<Item, CategoriesToolManager> syncedTools = readToolProfilesSnapshot(readJsonObject(root, FIELD_SYNC_TOOL_PROFILES));
			Map<Item, CategoriesArmorManager> syncedArmor = readArmorProfilesSnapshot(readJsonObject(root, FIELD_SYNC_ARMOR_PROFILES));

			enabled = syncedEnabled;
			itemLevelsEnabled = syncedLevelsEnabled;
			itemStartingLevel = syncedStartingLevel;
			itemMaximumLevel = syncedMaximumLevel;
			fuelTicksByItem = Map.copyOf(syncedFuelTicks);
			stackModesByItem = Map.copyOf(syncedStackModes);
			categoriesByItem = Map.copyOf(syncedCategories);
			toolCategoryItems = Set.copyOf(syncedToolItems);
			weaponCategoryItems = Set.copyOf(syncedWeaponItems);
			armorCategoryItems = Set.copyOf(syncedArmorItems);
			toolProfilesByItem = Map.copyOf(syncedTools);
			armorProfilesByItem = Map.copyOf(syncedArmor);
			ItemsStacksManager.applySynchronizedSettings(
				readBoolean(root, FIELD_SYNC_STACKS_ENABLED, ItemsStacksManager.isEnabled()),
				Math.max(1, readInt(root, FIELD_SYNC_STACK_LIMIT, ItemsStacksManager.getStackLimit()))
			);
			clientSynchronized = true;

			if (enabled) {
				captureClientComponents(toolProfilesByItem.keySet());
				captureClientComponents(armorProfilesByItem.keySet());
				applyToolProfiles(toolProfilesByItem);
				applyArmorProfiles(armorProfilesByItem);
			}
		} catch (RuntimeException exception) {
			LOGGER.error("Failed to apply synchronized item profiles.", exception);
		}
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static int applySingleStackRule(ItemStack stack, int currentLimit) {
		if (!enabled || stack == null || stack.isEmpty()) {
			return currentLimit;
		}
		StackMode stackMode = stackModesByItem.get(stack.getItem());
		if (stackMode == StackMode.SINGLE) {
			return 1;
		}
		if (stackMode == StackMode.MULTI && ItemsStacksManager.isEnabled()) {
			return Math.max(currentLimit, ItemsStacksManager.getStackLimit());
		}
		return currentLimit;
	}

	public static int adjustFuelTicks(ItemStack stack, int originalTicks) {
		if (!enabled || stack == null || stack.isEmpty()) {
			return originalTicks;
		}
		Integer configured = fuelTicksByItem.get(stack.getItem());
		if (configured == null || configured <= 0) {
			return originalTicks;
		}
		return configured;
	}

	public static boolean isConfiguredFuel(ItemStack stack) {
		if (!enabled || stack == null || stack.isEmpty()) {
			return false;
		}
		Integer configured = fuelTicksByItem.get(stack.getItem());
		return configured != null && configured > 0;
	}

	private static void onPlayerTick(MinecraftServer server, ServerPlayer player, long gameplayTick) {
		if (!enabled || player == null) {
			return;
		}
		if (toolCategoryItems.isEmpty() && weaponCategoryItems.isEmpty() && armorCategoryItems.isEmpty()) {
			return;
		}
		if (gameplayTick % PLAYER_COMPONENT_SYNC_INTERVAL_TICKS != 0L) {
			return;
		}
		if (!syncInventoryComponents(player)) {
			return;
		}

		player.getInventory().setChanged();
		player.containerMenu.broadcastChanges();
	}

	private static boolean syncInventoryComponents(ServerPlayer player) {
		if (player == null) {
			return false;
		}

		boolean changed = false;
		changed |= syncStackComponents(player.getMainHandItem());
		changed |= syncStackComponents(player.getOffhandItem());
		changed |= syncStackComponents(player.getItemBySlot(EquipmentSlot.HEAD));
		changed |= syncStackComponents(player.getItemBySlot(EquipmentSlot.CHEST));
		changed |= syncStackComponents(player.getItemBySlot(EquipmentSlot.LEGS));
		changed |= syncStackComponents(player.getItemBySlot(EquipmentSlot.FEET));
		changed |= syncStackComponents(player.containerMenu.getCarried());
		return changed;
	}

	private static boolean syncStackComponents(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}

		Item item = stack.getItem();
		boolean syncTool = isToolCategoryItem(item) || isWeaponCategoryItem(item);
		boolean syncArmor = isArmorCategoryItem(item);
		if (!syncTool && !syncArmor) {
			return false;
		}

		int hashBefore = ItemStack.hashItemAndComponents(stack);
		DataComponentMap prototype = stack.getPrototype();
		if (prototype == null) {
			return false;
		}

		if (syncTool) {
			syncComponentFromPrototype(stack, prototype, DataComponents.TOOL);
			syncComponentFromPrototype(stack, prototype, DataComponents.ATTACK_RANGE);
		}
		if (syncTool || syncArmor) {
			syncComponentFromPrototype(stack, prototype, DataComponents.ATTRIBUTE_MODIFIERS);
			syncComponentFromPrototype(stack, prototype, DataComponents.MAX_DAMAGE);
		}

		return hashBefore != ItemStack.hashItemAndComponents(stack);
	}

	private static <T> void syncComponentFromPrototype(
		ItemStack stack,
		DataComponentMap prototype,
		DataComponentType<T> componentType
	) {
		if (stack == null || prototype == null || componentType == null) {
			return;
		}
		if (stack.hasNonDefault(componentType)) {
			return;
		}

		T prototypeValue = prototype.get(componentType);
		if (prototypeValue == null) {
			return;
		}
		stack.set(componentType, prototypeValue);
	}

	public static boolean isToolCategoryItem(Item item) {
		if (!enabled || item == null) {
			return false;
		}
		return toolCategoryItems.contains(item) || hasCategory(item, ItemsConfigManager.CATEGORY_TOOL);
	}

	public static boolean isToolCategoryItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return isToolCategoryItem(stack.getItem());
	}

	public static boolean isArmorCategoryItem(Item item) {
		if (!enabled || item == null) {
			return false;
		}
		return armorCategoryItems.contains(item) || hasCategory(item, ItemsConfigManager.CATEGORY_ARMOR);
	}

	public static boolean isArmorCategoryItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return isArmorCategoryItem(stack.getItem());
	}

	public static boolean isWeaponCategoryItem(Item item) {
		if (!enabled || item == null) {
			return false;
		}
		return weaponCategoryItems.contains(item) || hasCategory(item, ItemsConfigManager.CATEGORY_WEAPON);
	}

	public static boolean isRarityCategoryItem(Item item) {
		return isToolCategoryItem(item) || isWeaponCategoryItem(item) || isArmorCategoryItem(item);
	}

	public static boolean isRarityCategoryItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return isRarityCategoryItem(stack.getItem());
	}

	public static boolean areItemLevelsEnabled() {
		return enabled && itemLevelsEnabled;
	}

	public static int getItemStartingLevel() {
		return itemStartingLevel;
	}

	public static int getItemMaximumLevel() {
		return itemMaximumLevel;
	}

	public static Integer getItemLevel(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return null;
		}
		return customData.copyTag().getInt(ITEM_LEVEL_DATA_KEY).orElse(null);
	}

	public static void applyGeneratedItemLevel(ItemStack stack, RandomSource randomSource) {
		if (!areItemLevelsEnabled() || stack == null || stack.isEmpty() || !isRarityCategoryItem(stack)
			|| getItemLevel(stack) != null) {
			return;
		}

		// Loot and other generated outputs always begin at level 1. Item upgrades are
		// handled explicitly by their owning runtime systems.
		applyConfiguredItemLevel(stack, 1);
	}

	public static void applyConfiguredItemLevel(ItemStack stack, int level) {
		applyConfiguredItemLevel(stack, level, true);
	}

	/** Applies an item level, optionally deferring lore until another item modifier finishes. */
	public static void applyConfiguredItemLevel(ItemStack stack, int level, boolean updateLore) {
		if (!areItemLevelsEnabled() || stack == null || stack.isEmpty() || !isRarityCategoryItem(stack)
			|| getItemLevel(stack) != null) {
			return;
		}

		int resolvedLevel = Math.max(1, Math.min(itemMaximumLevel, level));
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = customData == null ? new CompoundTag() : customData.copyTag();
		tag.putInt(ITEM_LEVEL_DATA_KEY, resolvedLevel);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

		double adjustmentPercent = itemMaximumLevel <= itemStartingLevel
			? 0.0D
			: (double) (resolvedLevel - itemStartingLevel) / (double) (itemMaximumLevel - itemStartingLevel);
		applyItemLevelScaling(stack, 1.0D + Math.max(0.0D, Math.min(1.0D, adjustmentPercent)));
		if (updateLore) {
			updateDurabilityLore(stack);
		}
	}

	private static void captureClientStateIfNeeded() {
		if (clientSynchronized) return;
		savedEnabled = enabled;
		savedItemLevelsEnabled = itemLevelsEnabled;
		savedItemStartingLevel = itemStartingLevel;
		savedItemMaximumLevel = itemMaximumLevel;
		savedFuelTicksByItem = fuelTicksByItem;
		savedStackModesByItem = stackModesByItem;
		savedToolProfilesByItem = toolProfilesByItem;
		savedArmorProfilesByItem = armorProfilesByItem;
		savedCategoriesByItem = categoriesByItem;
		savedToolCategoryItems = toolCategoryItems;
		savedWeaponCategoryItems = weaponCategoryItems;
		savedArmorCategoryItems = armorCategoryItems;
	}

	public static void resetClientSynchronizedState() {
		if (!clientSynchronized) {
			savedClientComponents = Map.of();
			ItemsStacksManager.resetClientSynchronizedSettings();
			return;
		}
		restoreClientComponents();
		enabled = savedEnabled;
		itemLevelsEnabled = savedItemLevelsEnabled;
		itemStartingLevel = savedItemStartingLevel;
		itemMaximumLevel = savedItemMaximumLevel;
		fuelTicksByItem = savedFuelTicksByItem;
		stackModesByItem = savedStackModesByItem;
		toolProfilesByItem = savedToolProfilesByItem;
		armorProfilesByItem = savedArmorProfilesByItem;
		categoriesByItem = savedCategoriesByItem;
		toolCategoryItems = savedToolCategoryItems;
		weaponCategoryItems = savedWeaponCategoryItems;
		armorCategoryItems = savedArmorCategoryItems;
		clientSynchronized = false;
		savedClientComponents = Map.of();
		ItemsStacksManager.resetClientSynchronizedSettings();
	}

	/** Sets an existing managed item's level while preserving the rarity and item components. */
	public static void setItemLevel(ItemStack stack, int level) {
		if (!areItemLevelsEnabled() || stack == null || stack.isEmpty() || !isRarityCategoryItem(stack)) {
			return;
		}

		int resolvedLevel = Math.max(itemStartingLevel, Math.min(itemMaximumLevel, level));
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = customData == null ? new CompoundTag() : customData.copyTag();
		tag.putInt(ITEM_LEVEL_DATA_KEY, resolvedLevel);
		double adjustmentPercent = itemMaximumLevel <= itemStartingLevel
			? 0.0D
			: (double) (resolvedLevel - itemStartingLevel) / (double) (itemMaximumLevel - itemStartingLevel);
		double multiplier = 1.0D + Math.max(0.0D, Math.min(1.0D, adjustmentPercent));
		if (multiplier <= 1.0D) {
			tag.remove(ITEM_LEVEL_SCALING_DATA_KEY);
		} else {
			tag.putDouble(ITEM_LEVEL_SCALING_DATA_KEY, multiplier);
		}
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		rebuildStatScaling(stack);
		updateDurabilityLore(stack);
	}

	/** Applies rarity's requested multiplier to the item mechanics owned by this system. */
	public static void applyRarityScaling(ItemStack stack, double multiplier) {
		applyStatScaling(stack, multiplier, RARITY_SCALING_DATA_KEY);
	}

	/** Applies an item level's requested multiplier to the item mechanics owned by this system. */
	public static void applyItemLevelScaling(ItemStack stack, double multiplier) {
		applyStatScaling(stack, multiplier, ITEM_LEVEL_SCALING_DATA_KEY);
	}

	private static void applyStatScaling(ItemStack stack, double multiplier, String scalingDataKey) {
		if (!enabled || stack == null || stack.isEmpty() || !isRarityCategoryItem(stack)
			|| !Double.isFinite(multiplier) || multiplier <= 1.0D) {
			return;
		}
		rememberScalingMultiplier(stack, scalingDataKey, multiplier);
		rebuildStatScaling(stack);
	}

	private static void rememberScalingMultiplier(ItemStack stack, String key, double multiplier) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = customData == null ? new CompoundTag() : customData.copyTag();
		tag.putDouble(key, multiplier);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	private static void rebuildStatScaling(ItemStack stack) {
		double rarityMultiplier = readScalingMultiplier(stack, RARITY_SCALING_DATA_KEY);
		double levelMultiplier = readScalingMultiplier(stack, ITEM_LEVEL_SCALING_DATA_KEY);
		double combinedMultiplier = rarityMultiplier + levelMultiplier - 1.0D;
		if (!Double.isFinite(combinedMultiplier) || combinedMultiplier <= 1.0D) {
			return;
		}

		DataComponentMap prototype = stack.getPrototype();
		Integer baseMaxDamage = prototype == null ? stack.get(DataComponents.MAX_DAMAGE) : prototype.get(DataComponents.MAX_DAMAGE);
		ItemAttributeModifiers baseAttributes = prototype == null
			? stack.get(DataComponents.ATTRIBUTE_MODIFIERS)
			: prototype.get(DataComponents.ATTRIBUTE_MODIFIERS);
		Tool baseTool = prototype == null ? stack.get(DataComponents.TOOL) : prototype.get(DataComponents.TOOL);

		scaleMaxDurability(stack, baseMaxDamage, combinedMultiplier);
		scaleMainHandAttackAttributes(stack, baseAttributes,
			combinedEffectMultiplier(rarityMultiplier, levelMultiplier, ATTACK_DAMAGE_SCALING_FACTOR),
			combinedEffectMultiplier(rarityMultiplier, levelMultiplier, ATTACK_SPEED_SCALING_FACTOR));
		scaleArmorAttributes(stack, baseAttributes,
			combinedEffectMultiplier(rarityMultiplier, levelMultiplier, ARMOR_SCALING_FACTOR),
			combinedEffectMultiplier(rarityMultiplier, levelMultiplier, ARMOR_TOUGHNESS_SCALING_FACTOR));
		scaleMiningSpeed(stack, baseTool,
			combinedEffectMultiplier(rarityMultiplier, levelMultiplier, MINING_SPEED_SCALING_FACTOR));
	}

	private static double readScalingMultiplier(ItemStack stack, String key) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return 1.0D;
		}
		return customData.copyTag().getDouble(key).orElse(1.0D);
	}

	private static double combinedEffectMultiplier(double rarityMultiplier, double levelMultiplier, double factor) {
		return 1.0D
			+ (rarityMultiplier - 1.0D) * factor
			+ (levelMultiplier - 1.0D) * factor;
	}

	public static void updateDurabilityLore(ItemStack stack) {
		if (!enabled || stack == null || stack.isEmpty() || !stack.isDamageableItem()
			|| !isRarityCategoryItem(stack)) {
			return;
		}
		int maxDurability = stack.getMaxDamage();
		if (maxDurability <= 0) {
			return;
		}

		int currentDurability = Math.max(0, maxDurability - stack.getDamageValue());
		Component durabilityLine = Component.literal(DURABILITY_PREFIX + " " + currentDurability + "/" + maxDurability)
			.withStyle(ChatFormatting.GRAY);
		ItemLore currentLore = stack.get(DataComponents.LORE);
		List<Component> updatedLines = new ArrayList<>();
		if (currentLore != null) {
			for (Component line : currentLore.lines()) {
				if (!line.getString().startsWith(DURABILITY_PREFIX)
					&& !line.getString().startsWith(LEVEL_PREFIX)) {
					updatedLines.add(line);
				}
			}
		}
		Integer itemLevel = getItemLevel(stack);
		if (itemLevel != null) {
			updatedLines.add(Component.literal(LEVEL_PREFIX + " " + itemLevel).withStyle(ChatFormatting.AQUA));
		}
		updatedLines.add(durabilityLine);
		if (currentLore == null || !currentLore.lines().equals(updatedLines)) {
			stack.set(DataComponents.LORE, new ItemLore(updatedLines));
		}
	}

	public static Set<String> getCategories(Item item) {
		if (!enabled || item == null) {
			return Set.of();
		}
		Set<String> categories = categoriesByItem.get(item);
		return categories == null || categories.isEmpty() ? Set.of() : Set.copyOf(categories);
	}

	public static boolean hasCategory(Item item, String category) {
		if (!enabled || item == null) {
			return false;
		}
		String normalizedCategory = normalizeCategoryValue(category);
		if (normalizedCategory.isEmpty()) {
			return false;
		}
		return getCategories(item).contains(normalizedCategory);
	}

	public static boolean hasCategory(ItemStack stack, String category) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return hasCategory(stack.getItem(), category);
	}

	private static void loadStaticConfig() {
		try {
			Path rootDirectory = JSONAPIManager.getOrCreateGlobalSystemDirectory(ITEM_CONFIG_ROOT_FOLDER_NAME);
			Path settingsFile = resolveJsonFile(rootDirectory, ITEM_CONFIG_SETTINGS_FILE_NAME);
			JSONFormatAPIManager.ManagedDocument settingsDocument = JSONFormatAPIManager.readManagedDocument(settingsFile);
			JsonObject rawSettingsRoot = settingsDocument.data();
			boolean itemSystemEnabled = readBoolean(settingsDocument.settings(), FIELD_ENABLED,
				readBoolean(rawSettingsRoot, FIELD_ENABLED, true));
			JsonObject settingsRoot = normalizeCategoryFeatureSettings(rawSettingsRoot);
			JsonObject settingsGeneral = settingsDocument.settings();
			settingsGeneral.addProperty(FIELD_ENABLED, itemSystemEnabled);
			JSONFormatAPIManager.writeManagedDocument(settingsFile, settingsRoot, settingsGeneral, JSONTypeAPIManager.STATIC_CONFIG);
			JsonObject itemLevels = settingsRoot.getAsJsonObject(ItemsConfigManager.FIELD_ITEM_LEVELS);
			boolean itemLevelsEnabled = readBoolean(itemLevels, ItemsConfigManager.FIELD_CATEGORY_ENABLED, true);
			int itemStartingLevel = Math.max(1, readInt(itemLevels, ItemsConfigManager.FIELD_STARTING_LEVEL, 1));
			int itemMaximumLevel = Math.max(itemStartingLevel,
				readInt(itemLevels, ItemsConfigManager.FIELD_MAXIMUM_LEVEL, 5));
			boolean armorCategoryEnabled = readCategoryEnabled(settingsRoot, ItemsConfigManager.FIELD_ARMOR_CATEGORY);
			boolean toolCategoryEnabled = readCategoryEnabled(settingsRoot, ItemsConfigManager.FIELD_TOOL_CATEGORY);
			boolean weaponCategoryEnabled = readCategoryEnabled(settingsRoot, ItemsConfigManager.FIELD_WEAPON_CATEGORY);
			boolean fuelCategoryEnabled = readCategoryEnabled(settingsRoot, ItemsConfigManager.FIELD_FUEL_CATEGORY);
			boolean otherCategoryEnabled = readCategoryEnabled(settingsRoot, ItemsConfigManager.FIELD_OTHER_CATEGORY);

			Path itemsDirectory = rootDirectory.resolve(ITEM_CONFIG_ITEMS_FOLDER_NAME);
			Path fuelDirectory = itemsDirectory.resolve(FUEL_ITEMS_FOLDER_NAME);
			Path otherDirectory = itemsDirectory.resolve(OTHER_ITEMS_FOLDER_NAME);
			Path toolDirectory = itemsDirectory.resolve(TOOL_ITEMS_FOLDER_NAME);
			Path weaponDirectory = itemsDirectory.resolve(WEAPON_ITEMS_FOLDER_NAME);
			Path armorDirectory = itemsDirectory.resolve(ARMOR_ITEMS_FOLDER_NAME);

			Map<String, JsonObject> normalizedFuel = JSONFormatAPIManager.ensureManagedFolder(
				fuelDirectory,
				ItemsConfigManager.buildDefaultFuelFileDefaults(),
				ItemsCategoriesManager::buildDynamicFuelDefaultsForFile,
				ItemsCategoriesManager::isSupportedFuelItemFile,
				null
			);

			Map<String, JsonObject> normalizedOther = JSONFormatAPIManager.ensureManagedFolder(
				otherDirectory,
				ItemsConfigManager.buildDefaultOtherFileDefaults(),
				ItemsCategoriesManager::buildDynamicOtherDefaultsForFile,
				ItemsCategoriesManager::isSupportedOtherItemFile,
				null
			);

			Map<String, JsonObject> normalizedTool = JSONFormatAPIManager.ensureManagedFolder(
				toolDirectory,
				ItemsConfigManager.buildDefaultToolsCategoryFileDefaults(),
				ItemsCategoriesManager::buildDynamicToolDefaultsForFile,
				ItemsCategoriesManager::isSupportedToolItemFile,
				ItemsCategoriesManager::normalizeToolDynamicEntry
			);

			Map<String, JsonObject> normalizedWeapon = JSONFormatAPIManager.ensureManagedFolder(
				weaponDirectory,
				ItemsConfigManager.buildDefaultWeaponFileDefaults(),
				ItemsCategoriesManager::buildDynamicWeaponDefaultsForFile,
				ItemsCategoriesManager::isSupportedWeaponItemFile,
				ItemsCategoriesManager::normalizeToolDynamicEntry
			);

			Map<String, JsonObject> normalizedArmor = JSONFormatAPIManager.ensureManagedFolder(
				armorDirectory,
				ItemsConfigManager.buildDefaultArmorFileDefaults(),
				ItemsCategoriesManager::buildDynamicArmorDefaultsForFile,
				ItemsCategoriesManager::isSupportedArmorItemFile,
				null
			);

			if (!itemSystemEnabled) {
				enabled = false;
				ItemsCategoriesManager.itemLevelsEnabled = false;
				fuelTicksByItem = Map.of();
				stackModesByItem = Map.of();
				toolProfilesByItem = Map.of();
				armorProfilesByItem = Map.of();
				categoriesByItem = Map.of();
				toolCategoryItems = Set.of();
				weaponCategoryItems = Set.of();
				armorCategoryItems = Set.of();
				return;
			}

			applyResolvedData(
				normalizedFuel,
				normalizedOther,
				normalizedTool,
				normalizedWeapon,
				normalizedArmor,
				fuelCategoryEnabled,
				otherCategoryEnabled,
				toolCategoryEnabled,
				weaponCategoryEnabled,
				armorCategoryEnabled,
				itemLevelsEnabled,
				itemStartingLevel,
				itemMaximumLevel
			);
		} catch (IOException | RuntimeException exception) {
			enabled = false;
			itemLevelsEnabled = false;
			fuelTicksByItem = Map.of();
			stackModesByItem = Map.of();
			toolProfilesByItem = Map.of();
			armorProfilesByItem = Map.of();
			categoriesByItem = Map.of();
			toolCategoryItems = Set.of();
			weaponCategoryItems = Set.of();
			armorCategoryItems = Set.of();
			LOGGER.error("Failed to load ItemsCategoriesManager folder config; disabling custom item rules.", exception);
		}
	}

	private static void applyResolvedData(
		Map<String, JsonObject> normalizedFuelFiles,
		Map<String, JsonObject> normalizedOtherFiles,
		Map<String, JsonObject> normalizedToolFiles,
		Map<String, JsonObject> normalizedWeaponFiles,
		Map<String, JsonObject> normalizedArmorFiles,
		boolean fuelCategoryEnabled,
		boolean otherCategoryEnabled,
		boolean toolCategoryEnabled,
		boolean weaponCategoryEnabled,
		boolean armorCategoryEnabled,
		boolean configuredItemLevelsEnabled,
		int configuredItemStartingLevel,
		int configuredItemMaximumLevel
	) {
		Map<Item, Integer> resolvedFuel = new LinkedHashMap<>();
		Map<Item, StackMode> resolvedStackModes = new LinkedHashMap<>();
		Map<Item, CategoriesToolManager> resolvedTools = new LinkedHashMap<>();
		Map<Item, CategoriesArmorManager> resolvedArmor = new LinkedHashMap<>();
		Map<Item, Set<String>> resolvedCategories = new LinkedHashMap<>();
		Set<Item> resolvedToolCategoryItems = new LinkedHashSet<>();
		Set<Item> resolvedWeaponCategoryItems = new LinkedHashSet<>();
		Set<Item> resolvedArmorCategoryItems = new LinkedHashSet<>();
		Set<String> enabledCategories = new LinkedHashSet<>();
		if (fuelCategoryEnabled) {
			enabledCategories.add(ItemsConfigManager.CATEGORY_FUEL);
		}
		if (otherCategoryEnabled) {
			enabledCategories.add(ItemsConfigManager.CATEGORY_OTHER);
		}
		if (toolCategoryEnabled) {
			enabledCategories.add(ItemsConfigManager.CATEGORY_TOOL);
		}
		if (weaponCategoryEnabled) {
			enabledCategories.add(ItemsConfigManager.CATEGORY_WEAPON);
		}
		if (armorCategoryEnabled) {
			enabledCategories.add(ItemsConfigManager.CATEGORY_ARMOR);
		}

		if (fuelCategoryEnabled) {
			for (Map.Entry<String, JsonObject> entry : normalizedFuelFiles.entrySet()) {
				JsonObject root = entry.getValue();
				if (root == null) {
					continue;
				}

				String itemId = resolveItemId(entry.getKey(), root);
				Item item = resolveItem(itemId);
				if (item == null) {
					continue;
				}

				int fuelTicks = clampFuelTicks(readInt(root, ItemsConfigManager.FIELD_FUEL_TICKS, 0));
				if (fuelTicks > 0) {
					resolvedFuel.put(item, fuelTicks);
				}
				resolvedStackModes.put(item, readStackMode(root, StackMode.MULTI));
				resolvedCategories.put(item, readCategories(root, enabledCategories));
			}
		}

		if (otherCategoryEnabled) {
			for (Map.Entry<String, JsonObject> entry : normalizedOtherFiles.entrySet()) {
				JsonObject root = entry.getValue();
				if (root == null) {
					continue;
				}

				String itemId = resolveItemId(entry.getKey(), root);
				Item item = resolveItem(itemId);
				if (item == null) {
					continue;
				}

				resolvedStackModes.put(item, readStackMode(root, StackMode.MULTI));
				resolvedCategories.put(item, readCategories(root, enabledCategories));
			}
		}

		if (toolCategoryEnabled) {
			for (Map.Entry<String, JsonObject> entry : normalizedToolFiles.entrySet()) {
				JsonObject root = entry.getValue();
				if (root == null) {
					continue;
				}

				String itemId = resolveItemId(entry.getKey(), root);
				Item item = resolveItem(itemId);
				if (item == null) {
					continue;
				}

				resolvedToolCategoryItems.add(item);
				resolvedStackModes.put(item, readStackMode(root, StackMode.SINGLE));
				resolvedCategories.put(item, readCategories(root, enabledCategories));

				CategoriesToolManager profile = parseToolProfile(root);
				if (isConfiguredToolProfile(profile)) {
					resolvedTools.put(item, profile);
				}
			}
		}

		if (weaponCategoryEnabled) {
			for (Map.Entry<String, JsonObject> entry : normalizedWeaponFiles.entrySet()) {
				JsonObject root = entry.getValue();
				if (root == null) continue;
				Item item = resolveItem(resolveItemId(entry.getKey(), root));
				if (item == null) continue;
				resolvedWeaponCategoryItems.add(item);
				resolvedToolCategoryItems.add(item);
				resolvedStackModes.put(item, readStackMode(root, StackMode.SINGLE));
				resolvedCategories.put(item, readCategories(root, enabledCategories));
				CategoriesToolManager profile = parseToolProfile(root);
				if (isConfiguredToolProfile(profile)) resolvedTools.put(item, profile);
			}
		}

		if (armorCategoryEnabled) {
			for (Map.Entry<String, JsonObject> entry : normalizedArmorFiles.entrySet()) {
				JsonObject root = entry.getValue();
				if (root == null) {
					continue;
				}

				String itemId = resolveItemId(entry.getKey(), root);
				Item item = resolveItem(itemId);
				if (item == null) {
					continue;
				}

				resolvedArmorCategoryItems.add(item);
				resolvedStackModes.put(item, readStackMode(root, StackMode.SINGLE));
				resolvedCategories.put(item, readCategories(root, enabledCategories));

				CategoriesArmorManager profile = parseArmorProfile(root);
				if (isConfiguredArmorProfile(profile)) {
					resolvedArmor.put(item, profile);
				}
			}
		}

			enabled = true;
			itemLevelsEnabled = configuredItemLevelsEnabled;
			itemStartingLevel = Math.max(1, configuredItemStartingLevel);
			itemMaximumLevel = Math.max(itemStartingLevel, configuredItemMaximumLevel);
			fuelTicksByItem = Map.copyOf(resolvedFuel);
			stackModesByItem = Map.copyOf(resolvedStackModes);
			toolProfilesByItem = Map.copyOf(resolvedTools);
			armorProfilesByItem = Map.copyOf(resolvedArmor);
			categoriesByItem = Map.copyOf(resolvedCategories);
			toolCategoryItems = Set.copyOf(resolvedToolCategoryItems);
			weaponCategoryItems = Set.copyOf(resolvedWeaponCategoryItems);
			armorCategoryItems = Set.copyOf(resolvedArmorCategoryItems);
		}

	private static JsonObject buildDynamicFuelDefaultsForFile(String fileKey) {
		String itemId = resolveItemId(fileKey, null);
		if (itemId == null) {
			itemId = "minecraft:" + normalizeFileKey(fileKey);
		}
		return ItemsConfigManager.buildFuelItemDefaults(itemId, 0.0d, ItemsConfigManager.STACK_MULTI);
	}

	private static JsonObject buildDynamicToolDefaultsForFile(String fileKey) {
		String itemId = resolveItemId(fileKey, null);
		if (itemId == null) {
			itemId = "minecraft:" + normalizeFileKey(fileKey);
		}
		if (isSpearItemId(itemId)) {
			return ItemsConfigManager.buildSpearItemDefaults(itemId);
		}
		return ItemsConfigManager.buildToolItemDefaults(itemId);
	}

	private static JsonObject buildDynamicOtherDefaultsForFile(String fileKey) {
		String itemId = resolveItemId(fileKey, null);
		if (itemId == null) {
			itemId = "minecraft:" + normalizeFileKey(fileKey);
		}
		return ItemsConfigManager.buildOtherItemDefaults(itemId, ItemsConfigManager.STACK_MULTI);
	}

	private static JsonObject buildDynamicWeaponDefaultsForFile(String fileKey) {
		String itemId = resolveItemId(fileKey, null);
		if (itemId == null) itemId = "minecraft:" + normalizeFileKey(fileKey);
		if (isSpearItemId(itemId)) return ItemsConfigManager.buildSpearItemDefaults(itemId);
		return ItemsConfigManager.buildWeaponItemDefaults(itemId);
	}

	private static JsonObject buildDynamicArmorDefaultsForFile(String fileKey) {
		String itemId = resolveItemId(fileKey, null);
		if (itemId == null) {
			itemId = "minecraft:" + normalizeFileKey(fileKey);
		}
		return ItemsConfigManager.buildArmorItemDefaults(itemId);
	}

	private static boolean isSupportedFuelItemFile(String fileKey, JsonObject sourceRoot) {
		return isSupportedItemFileForCategory(fileKey, sourceRoot, ItemsConfigManager.CATEGORY_FUEL);
	}

	private static boolean isSupportedToolItemFile(String fileKey, JsonObject sourceRoot) {
		return isSupportedItemFileForCategory(fileKey, sourceRoot, ItemsConfigManager.CATEGORY_TOOL);
	}

	private static boolean isSupportedWeaponItemFile(String fileKey, JsonObject sourceRoot) {
		return isSupportedItemFileForCategory(fileKey, sourceRoot, ItemsConfigManager.CATEGORY_WEAPON);
	}

	private static boolean isSupportedOtherItemFile(String fileKey, JsonObject sourceRoot) {
		return isSupportedItemFileForCategory(fileKey, sourceRoot, ItemsConfigManager.CATEGORY_OTHER);
	}

	private static boolean isSupportedArmorItemFile(String fileKey, JsonObject sourceRoot) {
		return isSupportedItemFileForCategory(fileKey, sourceRoot, ItemsConfigManager.CATEGORY_ARMOR);
	}

	private static boolean isSupportedItemFileForCategory(String fileKey, JsonObject sourceRoot, String expectedCategory) {
		if (resolveItemId(fileKey, sourceRoot) == null) {
			return false;
		}
		return hasDominantCategory(sourceRoot, expectedCategory);
	}

	private static String resolveItemId(String fileKey, JsonObject sourceRoot) {
		String explicit = readString(sourceRoot, ItemsConfigManager.FIELD_ITEM_ID, "");
		String explicitNormalized = normalizeItemId(explicit);
		if (explicitNormalized != null) {
			return explicitNormalized;
		}

		String inferred = normalizeItemId("minecraft:" + normalizeFileKey(fileKey));
		return inferred;
	}

	private static String normalizeFileKey(String fileKey) {
		if (fileKey == null) {
			return "";
		}
		return fileKey.trim().toLowerCase(Locale.ROOT).replace('-', '_');
	}

	private static Item resolveItem(String itemId) {
		Identifier id = Identifier.tryParse(JSONAPIManager.normalizeRegistryIdentifierForLookup(itemId));
		if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
			return null;
		}
		return BuiltInRegistries.ITEM.getValue(id);
	}

	private static String normalizeItemId(String rawValue) {
		Identifier id = Identifier.tryParse(JSONAPIManager.normalizeRegistryIdentifierForLookup(rawValue));
		if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
			return null;
		}
		return id.toString();
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return element.getAsBoolean();
	}

	private static JsonObject normalizeCategoryFeatureSettings(JsonObject source) {
		JsonObject normalized = ItemsConfigManager.buildCategoryFeatureDefaults();
		JsonObject itemLevels = normalized.getAsJsonObject(ItemsConfigManager.FIELD_ITEM_LEVELS);
		JsonObject sourceItemLevels = source != null
			&& source.get(ItemsConfigManager.FIELD_ITEM_LEVELS) != null
			&& source.get(ItemsConfigManager.FIELD_ITEM_LEVELS).isJsonObject()
			? source.getAsJsonObject(ItemsConfigManager.FIELD_ITEM_LEVELS)
			: null;
		int startingLevel = Math.max(1, readInt(sourceItemLevels, ItemsConfigManager.FIELD_STARTING_LEVEL, 1));
		int maximumLevel = Math.max(startingLevel,
			readInt(sourceItemLevels, ItemsConfigManager.FIELD_MAXIMUM_LEVEL, 5));
		itemLevels.addProperty(ItemsConfigManager.FIELD_CATEGORY_ENABLED,
			readBoolean(sourceItemLevels, ItemsConfigManager.FIELD_CATEGORY_ENABLED, true));
		itemLevels.addProperty(ItemsConfigManager.FIELD_STARTING_LEVEL, startingLevel);
		itemLevels.addProperty(ItemsConfigManager.FIELD_MAXIMUM_LEVEL, maximumLevel);
		for (String categoryField : CategoriesConfigManager.categoryFields()) {
			JsonObject category = normalized.getAsJsonObject(categoryField);
			category.addProperty(ItemsConfigManager.FIELD_CATEGORY_ENABLED, readCategoryEnabled(source, categoryField));
		}
		return normalized;
	}

	private static boolean readCategoryEnabled(JsonObject root, String categoryField) {
		JsonElement value = root == null ? null : root.get(categoryField);
		return value != null && value.isJsonObject()
			? readBoolean(value.getAsJsonObject(), ItemsConfigManager.FIELD_CATEGORY_ENABLED, true)
			: true;
	}

	private static int readInt(JsonObject root, String key, int fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsInt();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			double value = element.getAsDouble();
			return Double.isFinite(value) ? value : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static String readString(JsonObject root, String key, String fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}
		String value = element.getAsString();
		return value == null ? fallback : value;
	}

	private static Set<String> readCategories(JsonObject root, Set<String> enabledCategories) {
		if (root == null || enabledCategories == null || enabledCategories.isEmpty()) {
			return Set.of();
		}

		JsonElement element = root.get(ItemsConfigManager.FIELD_CATEGORY);
		if (element == null || !element.isJsonArray()) {
			return Set.of();
		}

		JsonArray array = element.getAsJsonArray();
		Set<String> categories = new LinkedHashSet<>();
		for (JsonElement categoryElement : array) {
			if (categoryElement == null || !categoryElement.isJsonPrimitive() || !categoryElement.getAsJsonPrimitive().isString()) {
				continue;
			}
			String normalizedCategory = normalizeCategoryValue(categoryElement.getAsString());
			if (normalizedCategory.isEmpty() || !enabledCategories.contains(normalizedCategory)) {
				continue;
			}
			categories.add(normalizedCategory);
		}

		return categories.isEmpty() ? Set.of() : Set.copyOf(categories);
	}

	private static boolean hasDominantCategory(JsonObject root, String expectedCategory) {
		String dominantCategory = resolveDominantCategory(root);
		if (dominantCategory == null || expectedCategory == null) {
			return false;
		}
		return dominantCategory.equals(normalizeCategoryValue(expectedCategory));
	}

	private static String resolveDominantCategory(JsonObject root) {
		if (root == null) {
			return null;
		}

		JsonElement element = root.get(ItemsConfigManager.FIELD_CATEGORY);
		if (element == null || !element.isJsonArray()) {
			return null;
		}

		Set<String> categories = readCategories(root, Set.of(
			ItemsConfigManager.CATEGORY_ARMOR,
			ItemsConfigManager.CATEGORY_TOOL,
			ItemsConfigManager.CATEGORY_WEAPON,
			ItemsConfigManager.CATEGORY_FUEL,
			ItemsConfigManager.CATEGORY_OTHER
		));
		return CategoriesConfigManager.dominantCategory(categories);
	}

	private static CategoriesToolManager parseToolProfile(JsonObject root) {
		Integer durability = readOptionalInt(root, ItemsConfigManager.FIELD_DURABILITY, ItemsConfigManager.TOOL_INT_UNSET);
		if (durability != null && durability <= 0) {
			durability = null;
		}

		Integer materialLevel = readOptionalInt(root, ItemsConfigManager.FIELD_MATERIAL_LEVEL, ItemsConfigManager.TOOL_INT_UNSET);
		if (materialLevel != null && materialLevel < 0) {
			materialLevel = null;
		}

		Double attackDamage = readOptionalDouble(root, ItemsConfigManager.FIELD_ATTACK_DAMAGE, ItemsConfigManager.TOOL_DOUBLE_UNSET);
		Double attackSpeed = readOptionalDouble(root, ItemsConfigManager.FIELD_ATTACK_SPEED, ItemsConfigManager.TOOL_DOUBLE_UNSET);
		Double miningSpeed = readOptionalDouble(root, ItemsConfigManager.FIELD_MINING_SPEED, ItemsConfigManager.TOOL_DOUBLE_UNSET);

		CategoriesToolManager.ReachProfile reach = null;
		Double reachMin = readOptionalDouble(root, ItemsConfigManager.FIELD_REACH_MIN, ItemsConfigManager.TOOL_DOUBLE_UNSET);
		Double reachMax = readOptionalDouble(root, ItemsConfigManager.FIELD_REACH_MAX, ItemsConfigManager.TOOL_DOUBLE_UNSET);
		CategoriesToolManager.ReachProfile parsedReach = new CategoriesToolManager.ReachProfile(
			reachMin,
			reachMax,
			reachMin,
			reachMax,
			null,
			null
		);
		if (parsedReach.hasValues()) {
			reach = parsedReach;
		}

		return new CategoriesToolManager(
			durability,
			attackDamage,
			attackSpeed,
			miningSpeed,
			materialLevel,
			reach
		);
	}

	private static CategoriesArmorManager parseArmorProfile(JsonObject root) {
		Integer durability = readOptionalInt(root, ItemsConfigManager.FIELD_DURABILITY, ItemsConfigManager.TOOL_INT_UNSET);
		if (durability != null && durability <= 0) {
			durability = null;
		}

		Double armor = readOptionalDouble(root, ItemsConfigManager.FIELD_ARMOR, ItemsConfigManager.TOOL_DOUBLE_UNSET);
		Double armorToughness = readOptionalDouble(root, ItemsConfigManager.FIELD_ARMOR_TOUGHNESS, ItemsConfigManager.TOOL_DOUBLE_UNSET);
		return new CategoriesArmorManager(durability, armor, armorToughness);
	}

	private static JsonObject writeToolProfilesSnapshot(Map<Item, CategoriesToolManager> profiles) {
		JSONFormatAPIManager.ObjectBuilder root = JSONFormatAPIManager.object();
		if (profiles == null || profiles.isEmpty()) {
			return root.build();
		}

		for (Map.Entry<Item, CategoriesToolManager> entry : profiles.entrySet()) {
			Item item = entry.getKey();
			CategoriesToolManager profile = entry.getValue();
			if (item == null || profile == null) {
				continue;
			}

			Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
			if (itemId == null) {
				continue;
			}

			JsonObject profileRoot = writeToolProfile(profile);
			if (!profileRoot.entrySet().isEmpty()) {
				root.put(itemId.toString(), profileRoot);
			}
		}
		return root.build();
	}

	private static JsonObject writeFuelTicksSnapshot(Map<Item, Integer> fuelTicks) {
		JSONFormatAPIManager.ObjectBuilder root = JSONFormatAPIManager.object();
		if (fuelTicks != null) {
			for (Map.Entry<Item, Integer> entry : fuelTicks.entrySet()) {
				Identifier itemId = entry.getKey() == null ? null : BuiltInRegistries.ITEM.getKey(entry.getKey());
				if (itemId != null && entry.getValue() != null && entry.getValue() > 0) {
					root.put(itemId.toString(), entry.getValue());
				}
			}
		}
		return root.build();
	}

	private static JsonObject writeStackModesSnapshot(Map<Item, StackMode> stackModes) {
		JSONFormatAPIManager.ObjectBuilder root = JSONFormatAPIManager.object();
		if (stackModes != null) {
			for (Map.Entry<Item, StackMode> entry : stackModes.entrySet()) {
				Identifier itemId = entry.getKey() == null ? null : BuiltInRegistries.ITEM.getKey(entry.getKey());
				if (itemId != null && entry.getValue() != null) {
					root.put(itemId.toString(), entry.getValue() == StackMode.SINGLE
						? ItemsConfigManager.STACK_SINGLE : ItemsConfigManager.STACK_MULTI);
				}
			}
		}
		return root.build();
	}

	private static JsonObject writeCategoriesSnapshot(Map<Item, Set<String>> categoriesByItem) {
		JsonObject root = new JsonObject();
		if (categoriesByItem == null) return root;
		for (Map.Entry<Item, Set<String>> entry : categoriesByItem.entrySet()) {
			Identifier itemId = entry.getKey() == null ? null : BuiltInRegistries.ITEM.getKey(entry.getKey());
			if (itemId == null || entry.getValue() == null || entry.getValue().isEmpty()) continue;
			JsonArray categories = new JsonArray();
			for (String category : entry.getValue()) {
				if (category != null && !category.isBlank()) categories.add(category);
			}
			if (!categories.isEmpty()) root.add(itemId.toString(), categories);
		}
		return root;
	}

	private static JsonArray writeItemSetSnapshot(Set<Item> items) {
		JsonArray root = new JsonArray();
		if (items == null) return root;
		for (Item item : items) {
			Identifier itemId = item == null ? null : BuiltInRegistries.ITEM.getKey(item);
			if (itemId != null) root.add(itemId.toString());
		}
		return root;
	}

	private static JsonObject writeArmorProfilesSnapshot(Map<Item, CategoriesArmorManager> profiles) {
		JSONFormatAPIManager.ObjectBuilder root = JSONFormatAPIManager.object();
		if (profiles == null || profiles.isEmpty()) {
			return root.build();
		}

		for (Map.Entry<Item, CategoriesArmorManager> entry : profiles.entrySet()) {
			Item item = entry.getKey();
			CategoriesArmorManager profile = entry.getValue();
			if (item == null || profile == null) {
				continue;
			}

			Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
			if (itemId == null) {
				continue;
			}

			JsonObject profileRoot = writeArmorProfile(profile);
			if (!profileRoot.entrySet().isEmpty()) {
				root.put(itemId.toString(), profileRoot);
			}
		}
		return root.build();
	}

	private static JsonObject writeToolProfile(CategoriesToolManager profile) {
		JSONFormatAPIManager.ObjectBuilder root = JSONFormatAPIManager.object();
		if (profile == null) {
			return root.build();
		}

		if (profile.hasDurability()) {
			root.put(ItemsConfigManager.FIELD_DURABILITY, profile.durability());
		}
		if (profile.hasAttackDamage()) {
			root.put(ItemsConfigManager.FIELD_ATTACK_DAMAGE, profile.attackDamage());
		}
		if (profile.hasAttackSpeed()) {
			root.put(ItemsConfigManager.FIELD_ATTACK_SPEED, profile.attackSpeed());
		}
		if (profile.hasMiningSpeed()) {
			root.put(ItemsConfigManager.FIELD_MINING_SPEED, profile.miningSpeed());
		}
		if (profile.hasMaterialLevel()) {
			root.put(ItemsConfigManager.FIELD_MATERIAL_LEVEL, profile.materialLevel());
		}

		CategoriesToolManager.ReachProfile reach = profile.reach();
		if (reach != null) {
			addOptionalDouble(root, ItemsConfigManager.FIELD_REACH_MIN, reach.minRange());
			addOptionalDouble(root, ItemsConfigManager.FIELD_REACH_MAX, reach.maxRange());
		}
		return root.build();
	}

	private static JsonObject writeArmorProfile(CategoriesArmorManager profile) {
		JSONFormatAPIManager.ObjectBuilder root = JSONFormatAPIManager.object();
		if (profile == null) {
			return root.build();
		}

		if (profile.hasDurability()) {
			root.put(ItemsConfigManager.FIELD_DURABILITY, profile.durability());
		}
		if (profile.hasArmor()) {
			root.put(ItemsConfigManager.FIELD_ARMOR, profile.armor());
		}
		if (profile.hasArmorToughness()) {
			root.put(ItemsConfigManager.FIELD_ARMOR_TOUGHNESS, profile.armorToughness());
		}
		return root.build();
	}

	private static Map<Item, CategoriesToolManager> readToolProfilesSnapshot(JsonObject root) {
		Map<Item, CategoriesToolManager> resolved = new LinkedHashMap<>();
		if (root == null || root.entrySet().isEmpty()) {
			return resolved;
		}

		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (entry == null || entry.getValue() == null || !entry.getValue().isJsonObject()) {
				continue;
			}

			Item item = resolveItem(entry.getKey());
			if (item == null) {
				continue;
			}

			CategoriesToolManager profile = parseToolProfile(entry.getValue().getAsJsonObject());
			if (isConfiguredToolProfile(profile)) {
				resolved.put(item, profile);
			}
		}
		return resolved;
	}

	private static Map<Item, CategoriesArmorManager> readArmorProfilesSnapshot(JsonObject root) {
		Map<Item, CategoriesArmorManager> resolved = new LinkedHashMap<>();
		if (root == null || root.entrySet().isEmpty()) {
			return resolved;
		}

		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (entry == null || entry.getValue() == null || !entry.getValue().isJsonObject()) {
				continue;
			}

			Item item = resolveItem(entry.getKey());
			if (item == null) {
				continue;
			}

			CategoriesArmorManager profile = parseArmorProfile(entry.getValue().getAsJsonObject());
			if (isConfiguredArmorProfile(profile)) {
				resolved.put(item, profile);
			}
		}
		return resolved;
	}

	private static Map<Item, Integer> readFuelTicksSnapshot(JsonObject root) {
		Map<Item, Integer> resolved = new LinkedHashMap<>();
		if (root == null) return resolved;
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (entry.getValue() == null || !entry.getValue().isJsonPrimitive()
				|| !entry.getValue().getAsJsonPrimitive().isNumber()) continue;
			Item item = resolveItem(entry.getKey());
			if (item == null) continue;
			resolved.put(item, clampFuelTicks(readIntValue(entry.getValue(), 0)));
		}
		return resolved;
	}

	private static Map<Item, StackMode> readStackModesSnapshot(JsonObject root) {
		Map<Item, StackMode> resolved = new LinkedHashMap<>();
		if (root == null) return resolved;
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (entry.getValue() == null || !entry.getValue().isJsonPrimitive()) continue;
			Item item = resolveItem(entry.getKey());
			if (item == null) continue;
			String mode = entry.getValue().getAsString();
			if (ItemsConfigManager.STACK_SINGLE.equalsIgnoreCase(mode)) {
				resolved.put(item, StackMode.SINGLE);
			} else if (ItemsConfigManager.STACK_MULTI.equalsIgnoreCase(mode)) {
				resolved.put(item, StackMode.MULTI);
			}
		}
		return resolved;
	}

	private static Map<Item, Set<String>> readCategoriesSnapshot(JsonObject root) {
		Map<Item, Set<String>> resolved = new LinkedHashMap<>();
		if (root == null) return resolved;
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (entry.getValue() == null || !entry.getValue().isJsonArray()) continue;
			Item item = resolveItem(entry.getKey());
			if (item == null) continue;
			Set<String> categories = new LinkedHashSet<>();
			for (JsonElement category : entry.getValue().getAsJsonArray()) {
				if (category != null && category.isJsonPrimitive()) {
					String normalized = normalizeCategoryValue(category.getAsString());
					if (!normalized.isEmpty()) categories.add(normalized);
				}
			}
			if (!categories.isEmpty()) resolved.put(item, Set.copyOf(categories));
		}
		return resolved;
	}

	private static Set<Item> readItemSetSnapshot(JsonElement element) {
		Set<Item> resolved = new LinkedHashSet<>();
		if (element == null || !element.isJsonArray()) return resolved;
		for (JsonElement itemElement : element.getAsJsonArray()) {
			if (itemElement == null || !itemElement.isJsonPrimitive()) continue;
			Item item = resolveItem(itemElement.getAsString());
			if (item != null) resolved.add(item);
		}
		return resolved;
	}

	private static int readIntValue(JsonElement element, int fallback) {
		try {
			return element == null || !element.isJsonPrimitive() ? fallback : element.getAsInt();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static JsonObject readJsonObject(JsonObject source, String key) {
		if (source == null || key == null || key.isBlank()) {
			return new JsonObject();
		}
		JsonElement element = source.get(key);
		if (element == null || !element.isJsonObject()) {
			return new JsonObject();
		}
		return element.getAsJsonObject();
	}

	private static void addOptionalDouble(JSONFormatAPIManager.ObjectBuilder root, String key, Double value) {
		if (root == null || key == null || key.isBlank() || value == null) {
			return;
		}
		root.put(key, value);
	}

	private static StackMode readStackMode(JsonObject root, StackMode fallback) {
		String fallbackValue = fallback == StackMode.SINGLE ? ItemsConfigManager.STACK_SINGLE : ItemsConfigManager.STACK_MULTI;
		String value = readString(root, ItemsConfigManager.FIELD_STACK, fallbackValue);
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (ItemsConfigManager.STACK_SINGLE.equals(normalized)) {
			return StackMode.SINGLE;
		}
		if (ItemsConfigManager.STACK_MULTI.equals(normalized)) {
			return StackMode.MULTI;
		}
		return fallback;
	}

	private static boolean isSpearItemId(String itemId) {
		return itemId != null && itemId.endsWith("_spear");
	}

	private static JsonElement normalizeToolDynamicEntry(String key, JsonElement sourceValue) {
		if (key == null || sourceValue == null || !sourceValue.isJsonPrimitive()) {
			return null;
		}
		if (!sourceValue.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		return switch (key) {
			case ItemsConfigManager.FIELD_REACH_MIN,
				ItemsConfigManager.FIELD_REACH_MAX -> sourceValue.deepCopy();
			default -> null;
		};
	}

	private static String normalizeCategoryValue(String rawCategoryValue) {
		return CategoriesConfigManager.normalize(rawCategoryValue);
	}

	private static Integer readOptionalInt(JsonObject root, String key, int sentinelValue) {
		int value = readInt(root, key, sentinelValue);
		return value == sentinelValue ? null : value;
	}

	private static Double readOptionalDouble(JsonObject root, String key, double sentinelValue) {
		double value = readDouble(root, key, sentinelValue);
		return Double.compare(value, sentinelValue) == 0 ? null : value;
	}

	private static boolean isConfiguredToolProfile(CategoriesToolManager profile) {
		if (profile == null) {
			return false;
		}
		return profile.hasDurability()
			|| profile.hasAttackDamage()
			|| profile.hasAttackSpeed()
			|| profile.hasMiningSpeed()
			|| profile.hasMaterialLevel()
			|| profile.hasReach();
	}

	private static boolean isConfiguredArmorProfile(CategoriesArmorManager profile) {
		if (profile == null) {
			return false;
		}
		return profile.hasDurability()
			|| profile.hasArmor()
			|| profile.hasArmorToughness();
	}

	private static void scaleMaxDurability(ItemStack stack, Integer baseMaxDamage, double multiplier) {
		Integer maxDamage = baseMaxDamage;
		if (maxDamage != null && maxDamage > 0) {
			long rounded = Math.round((maxDamage * multiplier) / 8.0D) * 8L;
			stack.set(DataComponents.MAX_DAMAGE, (int) Math.max(8L, Math.min(Integer.MAX_VALUE, rounded)));
		}
	}

	private static void scaleMainHandAttackAttributes(
		ItemStack stack,
		ItemAttributeModifiers current,
		double damageMultiplier,
		double speedMultiplier
	) {
		if (current == null) {
			return;
		}
		boolean changed = false;
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
			AttributeModifier modifier = entry.modifier();
			AttributeModifier updated = modifier;
			if (isMainHandAddValue(entry, modifier) && modifier.id().equals(Item.BASE_ATTACK_DAMAGE_ID)
				&& isAttribute(entry, Attributes.ATTACK_DAMAGE)) {
				double value = roundQuarter((1.0D + modifier.amount()) * damageMultiplier);
				updated = new AttributeModifier(modifier.id(), value - 1.0D, modifier.operation());
				changed = true;
			} else if (isMainHandAddValue(entry, modifier) && modifier.id().equals(Item.BASE_ATTACK_SPEED_ID)
				&& isAttribute(entry, Attributes.ATTACK_SPEED)) {
				double value = roundIncrement((4.0D + modifier.amount()) * speedMultiplier, 0.025D);
				updated = new AttributeModifier(modifier.id(), value - 4.0D, modifier.operation());
				changed = true;
			}
			builder.add(entry.attribute(), updated, entry.slot(), entry.display());
		}
		if (changed) {
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
		}
	}

	private static void scaleArmorAttributes(
		ItemStack stack,
		ItemAttributeModifiers current,
		double armorMultiplier,
		double toughnessMultiplier
	) {
		if (current == null) {
			return;
		}
		boolean changed = false;
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
			AttributeModifier modifier = entry.modifier();
			AttributeModifier updated = modifier;
			if (isAddValueModifier(modifier) && isAttribute(entry, Attributes.ARMOR)) {
				updated = new AttributeModifier(modifier.id(), roundArmorValue(modifier.amount() * armorMultiplier), modifier.operation());
				changed = true;
			} else if (isAddValueModifier(modifier) && isAttribute(entry, Attributes.ARMOR_TOUGHNESS)) {
				updated = new AttributeModifier(modifier.id(), roundArmorValue(modifier.amount() * toughnessMultiplier), modifier.operation());
				changed = true;
			}
			builder.add(entry.attribute(), updated, entry.slot(), entry.display());
		}
		if (changed) {
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
		}
	}

	private static void scaleMiningSpeed(ItemStack stack, Tool current, double multiplier) {
		if (current == null) {
			return;
		}
		boolean changed = false;
		List<Tool.Rule> rules = new ArrayList<>(current.rules().size());
		for (Tool.Rule rule : current.rules()) {
			Optional<Float> speed = rule.speed();
			if (speed.isPresent()) {
				rules.add(new Tool.Rule(rule.blocks(), Optional.of((float) roundQuarter(speed.get() * multiplier)), rule.correctForDrops()));
				changed = true;
			} else {
				rules.add(rule);
			}
		}
		float defaultSpeed = (float) roundQuarter(current.defaultMiningSpeed() * multiplier);
		changed |= defaultSpeed != current.defaultMiningSpeed();
		if (changed) {
			stack.set(DataComponents.TOOL, new Tool(rules, defaultSpeed, current.damagePerBlock(), current.canDestroyBlocksInCreative()));
		}
	}

	private static boolean isMainHandAddValue(ItemAttributeModifiers.Entry entry, AttributeModifier modifier) {
		return entry.slot() == EquipmentSlotGroup.MAINHAND && isAddValueModifier(modifier);
	}

	private static boolean isAddValueModifier(AttributeModifier modifier) {
		return modifier.operation() == AttributeModifier.Operation.ADD_VALUE;
	}

	private static double roundQuarter(double value) {
		return roundIncrement(value, 0.25D);
	}

	private static double roundArmorValue(double value) {
		return roundIncrement(value, 0.125D);
	}

	private static double roundIncrement(double value, double increment) {
		return Math.round(value / increment) * increment;
	}

	private static void applyToolProfiles(Map<Item, CategoriesToolManager> profiles) {
		if (profiles.isEmpty()) {
			return;
		}
		for (Map.Entry<Item, CategoriesToolManager> entry : profiles.entrySet()) {
			applyToolProfile(entry.getKey(), entry.getValue());
		}
	}

	private static void applyArmorProfiles(Map<Item, CategoriesArmorManager> profiles) {
		if (profiles.isEmpty()) {
			return;
		}
		for (Map.Entry<Item, CategoriesArmorManager> entry : profiles.entrySet()) {
			applyArmorProfile(entry.getKey(), entry.getValue());
		}
	}

	private static void captureClientComponents(Set<Item> items) {
		if (items == null || items.isEmpty()) return;
		Map<Item, DataComponentMap> captured = new LinkedHashMap<>(savedClientComponents);
		for (Item item : items) {
			if (item != null && !captured.containsKey(item)) captured.put(item, item.components());
		}
		savedClientComponents = Map.copyOf(captured);
	}

	private static void restoreClientComponents() {
		for (Map.Entry<Item, DataComponentMap> entry : savedClientComponents.entrySet()) {
			Item item = entry.getKey();
			DataComponentMap components = entry.getValue();
			if (item == null || components == null) continue;
			((ItemComponentsAccessor) ((ItemBuiltInRegistryHolderAccessor) item).madokuCraft$getBuiltInRegistryHolder())
				.madokuCraft$bindComponents(components);
		}
	}

	private static void applyToolProfile(Item item, CategoriesToolManager profile) {
		if (item == null) {
			return;
		}

		DataComponentMap base = item.components();

		DataComponentMap.Builder builder = DataComponentMap.builder().addAll(base);
		boolean changed = false;

		if (profile.hasDurability()) {
			builder.set(DataComponents.MAX_DAMAGE, profile.durability());
			changed = true;
		}

		ItemAttributeModifiers attributes = base.get(DataComponents.ATTRIBUTE_MODIFIERS);
		ItemAttributeModifiers updatedAttributes = applyAttackStats(attributes, profile);
		if (updatedAttributes != null && !Objects.equals(attributes, updatedAttributes)) {
			builder.set(DataComponents.ATTRIBUTE_MODIFIERS, updatedAttributes);
			changed = true;
		}

		Tool toolComponent = base.get(DataComponents.TOOL);
		Tool updatedTool = applyToolStats(toolComponent, profile);
		if (updatedTool != null && !Objects.equals(toolComponent, updatedTool)) {
			builder.set(DataComponents.TOOL, updatedTool);
			changed = true;
		}

		AttackRange attackRange = base.get(DataComponents.ATTACK_RANGE);
		AttackRange updatedAttackRange = applyReachStats(attackRange, profile);
		if (updatedAttackRange != null && !Objects.equals(attackRange, updatedAttackRange)) {
			builder.set(DataComponents.ATTACK_RANGE, updatedAttackRange);
			changed = true;
		}

		if (changed) {
			((ItemComponentsAccessor) ((ItemBuiltInRegistryHolderAccessor) item).madokuCraft$getBuiltInRegistryHolder())
				.madokuCraft$bindComponents(builder.build());
		}
	}

	private static ItemAttributeModifiers applyAttackStats(ItemAttributeModifiers current, CategoriesToolManager profile) {
		boolean hasDamage = profile.hasAttackDamage();
		boolean hasSpeed = profile.hasAttackSpeed();
		if (!hasDamage && !hasSpeed) {
			return current;
		}

		List<ItemAttributeModifiers.Entry> existingEntries = current != null ? current.modifiers() : List.of();
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();

		for (ItemAttributeModifiers.Entry entry : existingEntries) {
			if (hasDamage && isMainHandAttack(entry, Attributes.ATTACK_DAMAGE)) {
				continue;
			}
			if (hasSpeed && isMainHandAttack(entry, Attributes.ATTACK_SPEED)) {
				continue;
			}
			builder.add(entry.attribute(), entry.modifier(), entry.slot(), entry.display());
		}

		if (hasDamage) {
			AttributeModifier damageModifier = new AttributeModifier(
				Item.BASE_ATTACK_DAMAGE_ID,
				profile.attackDamage() - 1.0D,
				AttributeModifier.Operation.ADD_VALUE
			);
			builder.add(Attributes.ATTACK_DAMAGE, damageModifier, EquipmentSlotGroup.MAINHAND);
		}

		if (hasSpeed) {
			AttributeModifier speedModifier = new AttributeModifier(
				Item.BASE_ATTACK_SPEED_ID,
				profile.attackSpeed() - 4.0D,
				AttributeModifier.Operation.ADD_VALUE
			);
			builder.add(Attributes.ATTACK_SPEED, speedModifier, EquipmentSlotGroup.MAINHAND);
		}

		return builder.build();
	}

	private static void applyArmorProfile(Item item, CategoriesArmorManager profile) {
		if (item == null) {
			return;
		}

		DataComponentMap base = item.components();

		DataComponentMap.Builder builder = DataComponentMap.builder().addAll(base);
		boolean changed = false;

		if (profile.hasDurability()) {
			builder.set(DataComponents.MAX_DAMAGE, profile.durability());
			changed = true;
		}

		ItemAttributeModifiers attributes = base.get(DataComponents.ATTRIBUTE_MODIFIERS);
		ItemAttributeModifiers updatedAttributes = applyArmorStats(attributes, profile);
		if (updatedAttributes != null && !Objects.equals(attributes, updatedAttributes)) {
			builder.set(DataComponents.ATTRIBUTE_MODIFIERS, updatedAttributes);
			changed = true;
		}

		if (changed) {
			((ItemComponentsAccessor) ((ItemBuiltInRegistryHolderAccessor) item).madokuCraft$getBuiltInRegistryHolder())
				.madokuCraft$bindComponents(builder.build());
		}
	}

	private static ItemAttributeModifiers applyArmorStats(ItemAttributeModifiers current, CategoriesArmorManager profile) {
		boolean hasArmor = profile.hasArmor();
		boolean hasToughness = profile.hasArmorToughness();
		if (!hasArmor && !hasToughness) {
			return current;
		}
		if (current == null) {
			return current;
		}

		List<ItemAttributeModifiers.Entry> existingEntries = current.modifiers();
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();

		EquipmentSlotGroup armorSlot = null;
		AttributeModifier armorModifier = null;
		EquipmentSlotGroup toughnessSlot = null;
		AttributeModifier toughnessModifier = null;

		for (ItemAttributeModifiers.Entry entry : existingEntries) {
			if (hasArmor && isAttribute(entry, Attributes.ARMOR)) {
				armorSlot = entry.slot();
				armorModifier = entry.modifier();
				continue;
			}
			if (hasToughness && isAttribute(entry, Attributes.ARMOR_TOUGHNESS)) {
				toughnessSlot = entry.slot();
				toughnessModifier = entry.modifier();
				continue;
			}
			builder.add(entry.attribute(), entry.modifier(), entry.slot(), entry.display());
		}

		boolean changed = false;
		if (hasArmor && armorSlot != null && armorModifier != null) {
			AttributeModifier replacement = new AttributeModifier(
				armorModifier.id(),
				profile.armor(),
				armorModifier.operation()
			);
			builder.add(Attributes.ARMOR, replacement, armorSlot);
			changed = true;
		}

		if (hasToughness && toughnessSlot != null && toughnessModifier != null) {
			AttributeModifier replacement = new AttributeModifier(
				toughnessModifier.id(),
				profile.armorToughness(),
				toughnessModifier.operation()
			);
			builder.add(Attributes.ARMOR_TOUGHNESS, replacement, toughnessSlot);
			changed = true;
		}

		return changed ? builder.build() : current;
	}

	private static boolean isMainHandAttack(ItemAttributeModifiers.Entry entry, Holder<Attribute> attribute) {
		return entry.slot() == EquipmentSlotGroup.MAINHAND
			&& isAttribute(entry, attribute);
	}

	private static boolean isAttribute(ItemAttributeModifiers.Entry entry, Holder<Attribute> attribute) {
		return entry.attribute().value() == attribute.value();
	}

	private static Tool applyToolStats(Tool current, CategoriesToolManager profile) {
		if (current == null) {
			return null;
		}

		boolean hasMiningSpeed = profile.hasMiningSpeed();
		boolean hasMaterialLevel = profile.hasMaterialLevel();
		if (!hasMiningSpeed && !hasMaterialLevel) {
			return current;
		}

		List<Tool.Rule> rules = current.rules();
		if (rules.isEmpty()) {
			return current;
		}

		HolderSet<Block> incorrectForDrops = null;
		if (hasMaterialLevel) {
			incorrectForDrops = mapMaterialTier(profile.materialLevel());
		}

		boolean changed = false;
		List<Tool.Rule> updatedRules = new ArrayList<>(rules.size());
		for (Tool.Rule rule : rules) {
			if (hasMiningSpeed && shouldReplaceSpeed(rule, rules.size())) {
				updatedRules.add(new Tool.Rule(rule.blocks(), Optional.of(profile.miningSpeed().floatValue()), rule.correctForDrops()));
				changed = true;
				continue;
			}

			if (hasMaterialLevel && isIncorrectForDropsRule(rule) && incorrectForDrops != null) {
				updatedRules.add(Tool.Rule.deniesDrops(incorrectForDrops));
				changed = true;
				continue;
			}

			updatedRules.add(rule);
		}

		if (!changed) {
			return current;
		}

		return new Tool(
			updatedRules,
			current.defaultMiningSpeed(),
			current.damagePerBlock(),
			current.canDestroyBlocksInCreative()
		);
	}

	private static boolean shouldReplaceSpeed(Tool.Rule rule, int totalRules) {
		return totalRules <= 2
			&& rule.speed().isPresent()
			&& rule.correctForDrops().isPresent()
			&& rule.correctForDrops().get();
	}

	private static boolean isIncorrectForDropsRule(Tool.Rule rule) {
		return rule.correctForDrops().isPresent() && !rule.correctForDrops().get();
	}

	private static HolderSet<Block> mapMaterialTier(Integer materialLevel) {
		if (materialLevel == null) {
			return null;
		}

		TagKey<Block> tag = switch (materialLevel) {
			case 0 -> BlockTags.INCORRECT_FOR_WOODEN_TOOL;
			case 1 -> BlockTags.INCORRECT_FOR_STONE_TOOL;
			case 2 -> BlockTags.INCORRECT_FOR_IRON_TOOL;
			case 3 -> BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
			default -> BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
		};

		List<Holder<Block>> holders;
		try {
			holders = StreamSupport.stream(BuiltInRegistries.BLOCK.getTagOrEmpty(tag).spliterator(), false)
				.toList();
		} catch (IllegalStateException exception) {
			// Tags can be unavailable during early bootstrap; skip until server start reapplies profiles.
			return null;
		}
		if (holders.isEmpty()) {
			return null;
		}
		return HolderSet.direct(holders);
	}

	private static AttackRange applyReachStats(AttackRange current, CategoriesToolManager profile) {
		if (!profile.hasReach()) {
			return current;
		}

		AttackRange baseline = current != null ? current : DEFAULT_REACH;
		CategoriesToolManager.ReachProfile reach = profile.reach();
		return new AttackRange(
			valueOrDefault(reach.minRange(), baseline.minReach()),
			valueOrDefault(reach.maxRange(), baseline.maxReach()),
			valueOrDefault(reach.minCreativeRange(), baseline.minCreativeReach()),
			valueOrDefault(reach.maxCreativeRange(), baseline.maxCreativeReach()),
			valueOrDefault(reach.hitboxMargin(), baseline.hitboxMargin()),
			valueOrDefault(reach.mobFactor(), baseline.mobFactor())
		);
	}

	private static float valueOrDefault(Double value, float fallback) {
		return value != null ? value.floatValue() : fallback;
	}

	private static int clampFuelTicks(int configured) {
		if (configured <= 0) {
			return 0;
		}
		return Math.min(configured, MAX_FUEL_TICKS);
	}

	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Config file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private enum StackMode {
		SINGLE,
		MULTI
	}
}


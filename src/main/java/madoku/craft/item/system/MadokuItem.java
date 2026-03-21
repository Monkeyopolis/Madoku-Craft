package madoku.craft.item.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import madoku.craft.config.DynamicJsonSystem;
import madoku.craft.config.StaticJsonSystem;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.composter.system.MadokuComposterConfig;
import madoku.craft.farming.system.MadokuFarmingConfig;
import madoku.craft.itemstack.system.MadokuItemStack;
import madoku.craft.mixin.ItemComponentsAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.ItemAttributeModifiers;
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

public final class MadokuItem {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuItem.class);
	private static final AttackRange DEFAULT_REACH = new AttackRange(0.0F, 3.0F, 0.0F, 5.0F, 0.3F, 1.0F);
	private static final int MAX_FUEL_TICKS = 201600;

	private static final String ITEM_CONFIG_ROOT_FOLDER_NAME = "madoku-craft-items";
	private static final String ITEM_CONFIG_SETTINGS_FILE_NAME = "madoku-items";
	private static final String ITEM_CONFIG_ITEMS_FOLDER_NAME = "madoku-items";
	private static final String FARMING_CONFIG_ROOT_FOLDER_NAME = "madoku-craft-farming";
	private static final String FARMING_CONFIG_SETTINGS_FILE_NAME = "madoku-farming";
	private static final String FUEL_ITEMS_FOLDER_NAME = "fuel-items";
	private static final String MISC_ITEMS_FOLDER_NAME = "misc-items";
	private static final String FARMING_ITEMS_FOLDER_NAME = "farming-items";
	private static final String COMPOSTER_ITEMS_FOLDER_NAME = "composting-items";
	private static final String TOOL_ITEMS_FOLDER_NAME = "tool-items";
	private static final String ARMOR_ITEMS_FOLDER_NAME = "armor-items";

	private static volatile boolean enabled = true;
	private static volatile Map<Item, Integer> fuelTicksByItem = Map.of();
	private static volatile Map<Item, StackMode> stackModesByItem = Map.of();
	private static volatile Map<Item, MadokuToolProfile> toolProfilesByItem = Map.of();
	private static volatile Map<Item, MadokuArmorProfile> armorProfilesByItem = Map.of();
	private static volatile Map<Item, Integer> composterAdjustmentsByItem = Map.of();
	private static volatile Map<Item, Set<String>> secondaryCategoriesByItem = Map.of();
	private static volatile Set<Item> toolCategoryItems = Set.of();
	private static volatile Set<Item> armorCategoryItems = Set.of();

	private MadokuItem() {
	}

	public static void initialize() {
		loadStaticConfig();
	}

	public static void onServerStarted() {
		if (!enabled) {
			return;
		}
		if (toolProfilesByItem.isEmpty() && armorProfilesByItem.isEmpty()) {
			return;
		}
		applyToolProfiles(toolProfilesByItem);
		applyArmorProfiles(armorProfilesByItem);
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
		if (stackMode == StackMode.MULTI && MadokuItemStack.isEnabled()) {
			return Math.max(currentLimit, MadokuItemStack.getStackLimit());
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

	public static boolean isToolCategoryItem(Item item) {
		if (!enabled || item == null) {
			return false;
		}
		return toolCategoryItems.contains(item) || hasSecondaryCategory(item, MadokuItemConfig.PRIMARY_CATEGORY_TOOL);
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
		return armorCategoryItems.contains(item) || hasSecondaryCategory(item, MadokuItemConfig.PRIMARY_CATEGORY_ARMOR);
	}

	public static boolean isArmorCategoryItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return isArmorCategoryItem(stack.getItem());
	}

	public static boolean isRarityCategoryItem(Item item) {
		return isToolCategoryItem(item) || isArmorCategoryItem(item);
	}

	public static boolean isRarityCategoryItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return isRarityCategoryItem(stack.getItem());
	}

	public static Set<String> getSecondaryCategories(Item item) {
		if (!enabled || item == null) {
			return Set.of();
		}
		Set<String> categories = secondaryCategoriesByItem.get(item);
		return categories == null || categories.isEmpty() ? Set.of() : Set.copyOf(categories);
	}

	public static boolean hasSecondaryCategory(Item item, String category) {
		if (!enabled || item == null) {
			return false;
		}
		String normalizedCategory = normalizeCategoryValue(category);
		if (normalizedCategory.isEmpty()) {
			return false;
		}
		return getSecondaryCategories(item).contains(normalizedCategory);
	}

	public static boolean hasSecondaryCategory(ItemStack stack, String category) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return hasSecondaryCategory(stack.getItem(), category);
	}

	public static int getComposterAdjustment(ItemStack stack) {
		if (!enabled || stack == null || stack.isEmpty()) {
			return 0;
		}
		Integer configured = composterAdjustmentsByItem.get(stack.getItem());
		return configured == null ? 0 : configured;
	}


	private static void loadStaticConfig() {
		try {
			Path rootDirectory = StaticJsonSystem.getOrCreateGlobalSystemDirectory(ITEM_CONFIG_ROOT_FOLDER_NAME);
			Path settingsFile = resolveJsonFile(rootDirectory, ITEM_CONFIG_SETTINGS_FILE_NAME);
			JsonObject settingsRoot = StaticJsonSystem.ensureManagedFile(
				settingsFile,
				MadokuItemConfig.buildItemSystemDefaults()
			);
			boolean itemSystemEnabled = readBoolean(
				settingsRoot,
				MadokuItemConfig.FIELD_ITEM_SYSTEM_ENABLED,
				true
			);
			boolean farmingSystemEnabled = readFarmingSystemEnabled();

			Path itemsDirectory = rootDirectory.resolve(ITEM_CONFIG_ITEMS_FOLDER_NAME);
			Path fuelDirectory = itemsDirectory.resolve(FUEL_ITEMS_FOLDER_NAME);
			Path miscDirectory = itemsDirectory.resolve(MISC_ITEMS_FOLDER_NAME);
			Path farmingDirectory = itemsDirectory.resolve(FARMING_ITEMS_FOLDER_NAME);
			Path composterDirectory = itemsDirectory.resolve(COMPOSTER_ITEMS_FOLDER_NAME);
			Path toolDirectory = itemsDirectory.resolve(TOOL_ITEMS_FOLDER_NAME);
			Path armorDirectory = itemsDirectory.resolve(ARMOR_ITEMS_FOLDER_NAME);

			Map<String, JsonObject> normalizedFuel = DynamicJsonSystem.ensureManagedFolder(
				fuelDirectory,
				MadokuItemConfig.buildDefaultFuelFileDefaults(),
				MadokuItem::buildDynamicFuelDefaultsForFile,
				MadokuItem::isSupportedFuelItemFile,
				null
			);

			Map<String, JsonObject> normalizedMisc = DynamicJsonSystem.ensureManagedFolder(
				miscDirectory,
				MadokuItemConfig.buildDefaultMiscFileDefaults(),
				MadokuItem::buildDynamicMiscDefaultsForFile,
				MadokuItem::isSupportedMiscItemFile,
				null
			);

			Map<String, JsonObject> normalizedFarming = DynamicJsonSystem.ensureManagedFolder(
				farmingDirectory,
				farmingSystemEnabled ? MadokuItemConfig.buildDefaultFarmingFileDefaults() : Map.of(),
				farmingSystemEnabled ? MadokuItem::buildDynamicFarmingDefaultsForFile : null,
				farmingSystemEnabled ? MadokuItem::isSupportedFarmingItemFile : (fileKey, sourceRoot) -> false,
				null
			);

				Map<String, JsonObject> normalizedComposter = DynamicJsonSystem.ensureManagedFolder(
					composterDirectory,
					MadokuComposterConfig.buildDefaultComposterFileDefaults(farmingSystemEnabled),
					MadokuComposterConfig::buildDynamicComposterDefaultsForFile,
					(fileKey, sourceRoot) -> MadokuItem.isSupportedComposterItemFile(fileKey, sourceRoot, farmingSystemEnabled),
					null
			);

			Map<String, JsonObject> normalizedTool = DynamicJsonSystem.ensureManagedFolder(
				toolDirectory,
				MadokuItemConfig.buildDefaultToolsCategoryFileDefaults(),
				MadokuItem::buildDynamicToolDefaultsForFile,
				MadokuItem::isSupportedToolItemFile,
				MadokuItem::normalizeToolDynamicEntry
			);

			Map<String, JsonObject> normalizedArmor = DynamicJsonSystem.ensureManagedFolder(
				armorDirectory,
				MadokuItemConfig.buildDefaultArmorFileDefaults(),
				MadokuItem::buildDynamicArmorDefaultsForFile,
				MadokuItem::isSupportedArmorItemFile,
				null
			);

			if (!itemSystemEnabled) {
				enabled = false;
				fuelTicksByItem = Map.of();
				stackModesByItem = Map.of();
				toolProfilesByItem = Map.of();
				armorProfilesByItem = Map.of();
				composterAdjustmentsByItem = Map.of();
				secondaryCategoriesByItem = Map.of();
				toolCategoryItems = Set.of();
				armorCategoryItems = Set.of();
				emitConfigLoaded();
				return;
			}

			applyResolvedData(normalizedFuel, normalizedMisc, normalizedFarming, normalizedComposter, normalizedTool, normalizedArmor);
			emitConfigLoaded();
		} catch (IOException | RuntimeException exception) {
			enabled = false;
			fuelTicksByItem = Map.of();
			stackModesByItem = Map.of();
			toolProfilesByItem = Map.of();
			armorProfilesByItem = Map.of();
			composterAdjustmentsByItem = Map.of();
			secondaryCategoriesByItem = Map.of();
			toolCategoryItems = Set.of();
			armorCategoryItems = Set.of();
			LOGGER.error("Failed to load MadokuItem folder config; disabling custom item rules.", exception);
		}
	}

	private static void applyResolvedData(
		Map<String, JsonObject> normalizedFuelFiles,
		Map<String, JsonObject> normalizedMiscFiles,
		Map<String, JsonObject> normalizedFarmingFiles,
		Map<String, JsonObject> normalizedComposterFiles,
		Map<String, JsonObject> normalizedToolFiles,
		Map<String, JsonObject> normalizedArmorFiles
	) {
		Map<Item, Integer> resolvedFuel = new LinkedHashMap<>();
		Map<Item, StackMode> resolvedStackModes = new LinkedHashMap<>();
		Map<Item, MadokuToolProfile> resolvedTools = new LinkedHashMap<>();
		Map<Item, MadokuArmorProfile> resolvedArmor = new LinkedHashMap<>();
		Map<Item, Integer> resolvedComposterAdjustments = new LinkedHashMap<>();
		Map<Item, Set<String>> resolvedSecondaryCategories = new LinkedHashMap<>();
		Set<Item> resolvedToolCategoryItems = new LinkedHashSet<>();
		Set<Item> resolvedArmorCategoryItems = new LinkedHashSet<>();

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

			int fuelTicks = clampFuelTicks(readInt(root, MadokuItemConfig.FIELD_FUEL_TICKS, 0));
			if (fuelTicks > 0) {
				resolvedFuel.put(item, fuelTicks);
			}
			resolvedStackModes.put(item, readStackMode(root, StackMode.MULTI));
			resolvedSecondaryCategories.put(item, readSecondaryCategories(root));
		}

		for (Map.Entry<String, JsonObject> entry : normalizedMiscFiles.entrySet()) {
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
			resolvedSecondaryCategories.put(item, readSecondaryCategories(root));
		}

		for (Map.Entry<String, JsonObject> entry : normalizedFarmingFiles.entrySet()) {
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
			resolvedSecondaryCategories.put(item, readSecondaryCategories(root));
			resolvedComposterAdjustments.put(item, readComposterAdjustment(root));
		}

		for (Map.Entry<String, JsonObject> entry : normalizedComposterFiles.entrySet()) {
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
			resolvedSecondaryCategories.put(item, readSecondaryCategories(root));
			resolvedComposterAdjustments.put(item, readComposterAdjustment(root));
		}

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
			resolvedSecondaryCategories.put(item, readSecondaryCategories(root));

			MadokuToolProfile profile = parseToolProfile(root);
			if (isConfiguredToolProfile(profile)) {
				resolvedTools.put(item, profile);
			}
		}

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
			resolvedSecondaryCategories.put(item, readSecondaryCategories(root));

			MadokuArmorProfile profile = parseArmorProfile(root);
			if (isConfiguredArmorProfile(profile)) {
				resolvedArmor.put(item, profile);
			}
		}

		enabled = true;
		fuelTicksByItem = Map.copyOf(resolvedFuel);
		stackModesByItem = Map.copyOf(resolvedStackModes);
		toolProfilesByItem = Map.copyOf(resolvedTools);
		armorProfilesByItem = Map.copyOf(resolvedArmor);
		composterAdjustmentsByItem = Map.copyOf(resolvedComposterAdjustments);
		secondaryCategoriesByItem = Map.copyOf(resolvedSecondaryCategories);
		toolCategoryItems = Set.copyOf(resolvedToolCategoryItems);
		armorCategoryItems = Set.copyOf(resolvedArmorCategoryItems);
		applyToolProfiles(toolProfilesByItem);
		applyArmorProfiles(armorProfilesByItem);
	}

	private static JsonObject buildDynamicFuelDefaultsForFile(String fileKey) {
		String itemId = resolveItemId(fileKey, null);
		if (itemId == null) {
			itemId = "minecraft:" + normalizeFileKey(fileKey);
		}
		return MadokuItemConfig.buildFuelItemDefaults(itemId, 0.0d, MadokuItemConfig.STACK_MULTI);
	}

	private static JsonObject buildDynamicToolDefaultsForFile(String fileKey) {
		String itemId = resolveItemId(fileKey, null);
		if (itemId == null) {
			itemId = "minecraft:" + normalizeFileKey(fileKey);
		}
		if (isSpearItemId(itemId)) {
			return MadokuItemConfig.buildSpearItemDefaults(itemId);
		}
		return MadokuItemConfig.buildToolItemDefaults(itemId);
	}

	private static JsonObject buildDynamicMiscDefaultsForFile(String fileKey) {
		String itemId = resolveItemId(fileKey, null);
		if (itemId == null) {
			itemId = "minecraft:" + normalizeFileKey(fileKey);
		}
		return MadokuItemConfig.buildMiscItemDefaults(itemId, MadokuItemConfig.STACK_MULTI);
	}

	private static JsonObject buildDynamicFarmingDefaultsForFile(String fileKey) {
		String itemId = resolveItemId(fileKey, null);
		if (itemId == null) {
			itemId = "minecraft:" + normalizeFileKey(fileKey);
		}
		return MadokuItemConfig.buildFarmingItemDefaults(itemId, MadokuItemConfig.STACK_MULTI);
	}

	private static JsonObject buildDynamicArmorDefaultsForFile(String fileKey) {
		String itemId = resolveItemId(fileKey, null);
		if (itemId == null) {
			itemId = "minecraft:" + normalizeFileKey(fileKey);
		}
		return MadokuItemConfig.buildArmorItemDefaults(itemId);
	}

	private static boolean isSupportedFuelItemFile(String fileKey, JsonObject sourceRoot) {
		return resolveItemId(fileKey, sourceRoot) != null;
	}

	private static boolean isSupportedToolItemFile(String fileKey, JsonObject sourceRoot) {
		return resolveItemId(fileKey, sourceRoot) != null;
	}

	private static boolean isSupportedMiscItemFile(String fileKey, JsonObject sourceRoot) {
		return resolveItemId(fileKey, sourceRoot) != null;
	}

	private static boolean isSupportedFarmingItemFile(String fileKey, JsonObject sourceRoot) {
		return resolveItemId(fileKey, sourceRoot) != null;
	}

	private static boolean isSupportedArmorItemFile(String fileKey, JsonObject sourceRoot) {
		return resolveItemId(fileKey, sourceRoot) != null;
	}

	private static boolean isSupportedComposterItemFile(String fileKey, JsonObject sourceRoot, boolean farmingSystemEnabled) {
		String itemId = resolveComposterItemId(fileKey, sourceRoot);
		if (itemId == null) {
			return false;
		}
		if (farmingSystemEnabled) {
			return !MadokuItemConfig.buildDefaultFarmingItems().containsKey(itemId);
		}
		return true;
	}

	private static String resolveItemId(String fileKey, JsonObject sourceRoot) {
		String explicit = readString(sourceRoot, MadokuItemConfig.FIELD_ITEM_ID, "");
		String explicitNormalized = normalizeItemId(explicit);
		if (explicitNormalized != null) {
			return explicitNormalized;
		}

		String inferred = normalizeItemId("minecraft:" + normalizeFileKey(fileKey));
		return inferred;
	}

	private static String resolveComposterItemId(String fileKey, JsonObject sourceRoot) {
		String explicit = readString(sourceRoot, MadokuComposterConfig.FIELD_ITEM_ID, "");
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
		return fileKey.trim().toLowerCase(Locale.ROOT);
	}

	private static Item resolveItem(String itemId) {
		Identifier id = Identifier.tryParse(itemId == null ? "" : itemId.trim());
		if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
			return null;
		}
		return BuiltInRegistries.ITEM.getValue(id);
	}

	private static String normalizeItemId(String rawValue) {
		Identifier id = Identifier.tryParse(rawValue == null ? "" : rawValue.trim());
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

	private static boolean readFarmingSystemEnabled() throws IOException {
		Path rootDirectory = StaticJsonSystem.getOrCreateGlobalSystemDirectory(FARMING_CONFIG_ROOT_FOLDER_NAME);
		Path settingsFile = resolveJsonFile(rootDirectory, FARMING_CONFIG_SETTINGS_FILE_NAME);
		JsonObject settingsRoot = StaticJsonSystem.ensureManagedFile(
			settingsFile,
			MadokuFarmingConfig.buildFarmingDefaults()
		);
		return readBoolean(settingsRoot, MadokuFarmingConfig.FIELD_FARMING_SYSTEM_ENABLED, true);
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

	private static Set<String> readSecondaryCategories(JsonObject root) {
		if (root == null) {
			return Set.of();
		}

		JsonElement element = root.get(MadokuItemConfig.FIELD_SECONDARY_CATEGORIES);
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
			if (normalizedCategory.isEmpty()) {
				continue;
			}
			categories.add(normalizedCategory);
		}

		return categories.isEmpty() ? Set.of() : Set.copyOf(categories);
	}

	private static int readComposterAdjustment(JsonObject root) {
		int fallback = readInt(root, MadokuItemConfig.FIELD_COMPOSTER_ADJUSTMENT, 1);
		if (fallback <= 0) {
			fallback = readInt(root, "adjustment", 1);
		}
		return Math.max(1, fallback);
	}

	private static void emitConfigLoaded() {
		String metricId = "item.config_loaded";
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ITEM, metricId)) {
			return;
		}

		MadokuDebug.event(metricId, MadokuDebug.Domain.ITEM)
			.side(MadokuDebug.Side.SERVER)
			.subject("item:global")
			.field("enabled", enabled)
			.field("fuel_items", fuelTicksByItem.size())
			.field("single_stack_items", countSingleStackItems())
			.field("secondary_category_items", countSecondaryCategoryItems())
			.field("tool_items", toolProfilesByItem.size())
			.field("armor_items", armorProfilesByItem.size())
			.log();
	}

	private static MadokuToolProfile parseToolProfile(JsonObject root) {
		Integer durability = readOptionalInt(root, MadokuItemConfig.FIELD_DURABILITY, MadokuItemConfig.TOOL_INT_UNSET);
		if (durability != null && durability <= 0) {
			durability = null;
		}

		Integer materialLevel = readOptionalInt(root, MadokuItemConfig.FIELD_MATERIAL_LEVEL, MadokuItemConfig.TOOL_INT_UNSET);
		if (materialLevel != null && materialLevel < 0) {
			materialLevel = null;
		}

		Double attackDamage = readOptionalDouble(root, MadokuItemConfig.FIELD_ATTACK_DAMAGE, MadokuItemConfig.TOOL_DOUBLE_UNSET);
		Double attackSpeed = readOptionalDouble(root, MadokuItemConfig.FIELD_ATTACK_SPEED, MadokuItemConfig.TOOL_DOUBLE_UNSET);
		Double miningSpeed = readOptionalDouble(root, MadokuItemConfig.FIELD_MINING_SPEED, MadokuItemConfig.TOOL_DOUBLE_UNSET);

		MadokuToolProfile.ReachProfile reach = null;
		MadokuToolProfile.ReachProfile parsedReach = new MadokuToolProfile.ReachProfile(
			readOptionalDouble(root, MadokuItemConfig.FIELD_REACH_MIN, MadokuItemConfig.TOOL_DOUBLE_UNSET),
			readOptionalDouble(root, MadokuItemConfig.FIELD_REACH_MAX, MadokuItemConfig.TOOL_DOUBLE_UNSET),
			readOptionalDouble(root, MadokuItemConfig.FIELD_REACH_MIN_CREATIVE, MadokuItemConfig.TOOL_DOUBLE_UNSET),
			readOptionalDouble(root, MadokuItemConfig.FIELD_REACH_MAX_CREATIVE, MadokuItemConfig.TOOL_DOUBLE_UNSET),
			readOptionalDouble(root, MadokuItemConfig.FIELD_REACH_HITBOX_MARGIN, MadokuItemConfig.TOOL_DOUBLE_UNSET),
			readOptionalDouble(root, MadokuItemConfig.FIELD_REACH_MOB_FACTOR, MadokuItemConfig.TOOL_DOUBLE_UNSET)
		);
		if (parsedReach.hasValues()) {
			reach = parsedReach;
		}

		return new MadokuToolProfile(
			durability,
			attackDamage,
			attackSpeed,
			miningSpeed,
			materialLevel,
			reach
		);
	}

	private static MadokuArmorProfile parseArmorProfile(JsonObject root) {
		Integer durability = readOptionalInt(root, MadokuItemConfig.FIELD_DURABILITY, MadokuItemConfig.TOOL_INT_UNSET);
		if (durability != null && durability <= 0) {
			durability = null;
		}

		Double armor = readOptionalDouble(root, MadokuItemConfig.FIELD_ARMOR, MadokuItemConfig.TOOL_DOUBLE_UNSET);
		Double armorToughness = readOptionalDouble(root, MadokuItemConfig.FIELD_ARMOR_TOUGHNESS, MadokuItemConfig.TOOL_DOUBLE_UNSET);
		return new MadokuArmorProfile(durability, armor, armorToughness);
	}

	private static StackMode readStackMode(JsonObject root, StackMode fallback) {
		String fallbackValue = fallback == StackMode.SINGLE ? MadokuItemConfig.STACK_SINGLE : MadokuItemConfig.STACK_MULTI;
		String value = readString(root, MadokuItemConfig.FIELD_STACK, fallbackValue);
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (MadokuItemConfig.STACK_SINGLE.equals(normalized)) {
			return StackMode.SINGLE;
		}
		if (MadokuItemConfig.STACK_MULTI.equals(normalized)) {
			return StackMode.MULTI;
		}
		return fallback;
	}

	private static long countSingleStackItems() {
		return stackModesByItem.values().stream().filter(mode -> mode == StackMode.SINGLE).count();
	}

	private static long countSecondaryCategoryItems() {
		return secondaryCategoriesByItem.values().stream()
			.filter(categories -> categories != null && !categories.isEmpty())
			.count();
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
			case MadokuItemConfig.FIELD_REACH_MIN,
				MadokuItemConfig.FIELD_REACH_MAX,
				MadokuItemConfig.FIELD_REACH_MIN_CREATIVE,
				MadokuItemConfig.FIELD_REACH_MAX_CREATIVE,
				MadokuItemConfig.FIELD_REACH_HITBOX_MARGIN,
				MadokuItemConfig.FIELD_REACH_MOB_FACTOR -> sourceValue.deepCopy();
			default -> null;
		};
	}

	private static String normalizeCategoryValue(String rawCategoryValue) {
		return rawCategoryValue == null ? "" : rawCategoryValue.trim().toLowerCase(Locale.ROOT);
	}

	private static Integer readOptionalInt(JsonObject root, String key, int sentinelValue) {
		int value = readInt(root, key, sentinelValue);
		return value == sentinelValue ? null : value;
	}

	private static Double readOptionalDouble(JsonObject root, String key, double sentinelValue) {
		double value = readDouble(root, key, sentinelValue);
		return Double.compare(value, sentinelValue) == 0 ? null : value;
	}

	private static boolean isConfiguredToolProfile(MadokuToolProfile profile) {
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

	private static boolean isConfiguredArmorProfile(MadokuArmorProfile profile) {
		if (profile == null) {
			return false;
		}
		return profile.hasDurability()
			|| profile.hasArmor()
			|| profile.hasArmorToughness();
	}

	private static void applyToolProfiles(Map<Item, MadokuToolProfile> profiles) {
		if (profiles.isEmpty()) {
			return;
		}
		for (Map.Entry<Item, MadokuToolProfile> entry : profiles.entrySet()) {
			applyToolProfile(entry.getKey(), entry.getValue());
		}
	}

	private static void applyArmorProfiles(Map<Item, MadokuArmorProfile> profiles) {
		if (profiles.isEmpty()) {
			return;
		}
		for (Map.Entry<Item, MadokuArmorProfile> entry : profiles.entrySet()) {
			applyArmorProfile(entry.getKey(), entry.getValue());
		}
	}

	private static void applyToolProfile(Item item, MadokuToolProfile profile) {
		if (!(item instanceof ItemComponentsAccessor accessor)) {
			return;
		}

		DataComponentMap base = accessor.madokuCraft$getComponents();
		if (base == null) {
			return;
		}

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
			accessor.madokuCraft$setComponents(builder.build());
		}
	}

	private static ItemAttributeModifiers applyAttackStats(ItemAttributeModifiers current, MadokuToolProfile profile) {
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

	private static void applyArmorProfile(Item item, MadokuArmorProfile profile) {
		if (!(item instanceof ItemComponentsAccessor accessor)) {
			return;
		}

		DataComponentMap base = accessor.madokuCraft$getComponents();
		if (base == null) {
			return;
		}

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
			accessor.madokuCraft$setComponents(builder.build());
		}
	}

	private static ItemAttributeModifiers applyArmorStats(ItemAttributeModifiers current, MadokuArmorProfile profile) {
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

	private static Tool applyToolStats(Tool current, MadokuToolProfile profile) {
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

	private static AttackRange applyReachStats(AttackRange current, MadokuToolProfile profile) {
		if (!profile.hasReach()) {
			return current;
		}

		AttackRange baseline = current != null ? current : DEFAULT_REACH;
		MadokuToolProfile.ReachProfile reach = profile.reach();
		return new AttackRange(
			valueOrDefault(reach.minRange(), baseline.minRange()),
			valueOrDefault(reach.maxRange(), baseline.maxRange()),
			valueOrDefault(reach.minCreativeRange(), baseline.minCreativeRange()),
			valueOrDefault(reach.maxCreativeRange(), baseline.maxCreativeRange()),
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

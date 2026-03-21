package madoku.craft.composter.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.config.DynamicJsonSystem;
import madoku.craft.config.StaticJsonSystem;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.item.system.MadokuItem;
import madoku.craft.item.system.MadokuItemConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MadokuComposter {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuComposter.class);
	private static final String COMPOSTER_CONFIG_ROOT_FOLDER_NAME = "madoku-craft-composter";
	private static final String COMPOSTER_CONFIG_SETTINGS_FILE_NAME = "madoku-composter";
	private static final String COMPOSTER_ITEM_SYSTEM_ROOT_FOLDER_NAME = "madoku-craft-items";
	private static final String COMPOSTER_ITEM_SYSTEM_ITEMS_FOLDER_NAME = "madoku-items";
	private static final String COMPOSTER_ITEMS_FOLDER_NAME = "composting-items";
	private static final String COMPOSTER_SECONDARY_CATEGORY = MadokuItemConfig.SECONDARY_CATEGORY_COMPOSTER;

	private static volatile boolean enabled = true;
	private static volatile Set<Item> composterItems = Set.of();
	private static volatile Map<Item, Integer> composterAdjustmentsByItem = Map.of();

	private MadokuComposter() {
	}

	public static void initialize() {
		loadStaticConfig();
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static boolean isComposterItem(Item item) {
		if (!enabled || item == null) {
			return false;
		}
		return composterItems.contains(item) || MadokuItem.hasSecondaryCategory(item, COMPOSTER_SECONDARY_CATEGORY);
	}

	public static boolean isComposterItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return isComposterItem(stack.getItem());
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
			if (!MadokuItem.isEnabled()) {
				enabled = false;
				composterItems = Set.of();
				composterAdjustmentsByItem = Map.of();
				MadokuItem.setSecondaryCategoryItems(COMPOSTER_SECONDARY_CATEGORY, Set.of());
				return;
			}

			Path rootDirectory = StaticJsonSystem.getOrCreateGlobalSystemDirectory(COMPOSTER_CONFIG_ROOT_FOLDER_NAME);
			Path settingsFile = resolveJsonFile(rootDirectory, COMPOSTER_CONFIG_SETTINGS_FILE_NAME);
			JsonObject settingsRoot = StaticJsonSystem.ensureManagedFile(
				settingsFile,
				MadokuComposterConfig.buildComposterSystemDefaults()
			);
			boolean composterEnabled = readBoolean(
				settingsRoot,
				MadokuComposterConfig.FIELD_COMPOSTER_SYSTEM_ENABLED,
				true
			);

			Path composterDirectory = StaticJsonSystem.getOrCreateGlobalSystemDirectory(COMPOSTER_ITEM_SYSTEM_ROOT_FOLDER_NAME)
				.resolve(COMPOSTER_ITEM_SYSTEM_ITEMS_FOLDER_NAME)
				.resolve(COMPOSTER_ITEMS_FOLDER_NAME);
			Map<String, JsonObject> normalizedComposter = DynamicJsonSystem.ensureManagedFolder(
				composterDirectory,
				MadokuComposterConfig.buildDefaultComposterFileDefaults(),
				MadokuComposterConfig::buildDynamicComposterDefaultsForFile,
				MadokuComposter::isSupportedComposterItemFile,
				null
			);

			if (!composterEnabled) {
				enabled = false;
				composterItems = Set.of();
				composterAdjustmentsByItem = Map.of();
				MadokuItem.setSecondaryCategoryItems(COMPOSTER_SECONDARY_CATEGORY, Set.of());
				emitConfigLoaded();
				return;
			}

			applyResolvedData(normalizedComposter);
			emitConfigLoaded();
		} catch (IOException | RuntimeException exception) {
			enabled = false;
			composterItems = Set.of();
			composterAdjustmentsByItem = Map.of();
			MadokuItem.setSecondaryCategoryItems(COMPOSTER_SECONDARY_CATEGORY, Set.of());
			LOGGER.error("Failed to load MadokuComposter folder config; disabling custom composter rules.", exception);
		}
	}

	private static void applyResolvedData(Map<String, JsonObject> normalizedComposterFiles) {
		Set<Item> resolvedItems = new LinkedHashSet<>();
		Map<Item, Integer> resolvedAdjustments = new LinkedHashMap<>();

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

			resolvedItems.add(item);
			resolvedAdjustments.put(item, readComposterAdjustment(root));
		}

		enabled = true;
		composterItems = Set.copyOf(resolvedItems);
		composterAdjustmentsByItem = Map.copyOf(resolvedAdjustments);
		MadokuItem.setSecondaryCategoryItems(COMPOSTER_SECONDARY_CATEGORY, resolvedItems);
	}

	private static boolean isSupportedComposterItemFile(String fileKey, JsonObject sourceRoot) {
		return resolveItemId(fileKey, sourceRoot) != null;
	}

	private static String resolveItemId(String fileKey, JsonObject sourceRoot) {
		String explicit = readString(sourceRoot, MadokuComposterConfig.FIELD_ITEM_ID, "");
		String explicitNormalized = normalizeItemId(explicit);
		if (explicitNormalized != null) {
			return explicitNormalized;
		}

		String inferred = normalizeItemId("minecraft:" + normalizeFileKey(fileKey));
		return inferred;
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

	private static int readComposterAdjustment(JsonObject root) {
		int fallback = readInt(root, MadokuComposterConfig.FIELD_COMPOSTER_ADJUSTMENT, 1);
		if (fallback <= 0) {
			fallback = readInt(root, "adjustment", 1);
		}
		return Math.max(1, fallback);
	}

	private static void emitConfigLoaded() {
		String metricId = "composter.config_loaded";
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ITEM, metricId)) {
			return;
		}
		MadokuDebug.event(metricId, MadokuDebug.Domain.ITEM)
			.side(MadokuDebug.Side.SERVER)
			.subject("composter:global")
			.field("enabled", enabled)
			.field("composter_items", composterItems.size())
			.log();
	}

	private static String normalizeFileKey(String fileKey) {
		if (fileKey == null) {
			return "";
		}
		return fileKey.trim().toLowerCase(Locale.ROOT);
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
}

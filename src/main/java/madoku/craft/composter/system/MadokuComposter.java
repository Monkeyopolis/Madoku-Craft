package madoku.craft.composter.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.item.system.MadokuItem;
import madoku.craft.item.system.MadokuItemConfig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class MadokuComposter {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuComposter.class);
	private static final String COMPOSTER_CONFIG_ROOT_FOLDER_NAME = "madoku-craft-composter";
	private static final String COMPOSTER_CONFIG_SETTINGS_FILE_NAME = "madoku-composter";

	private static volatile boolean enabled = true;

	private MadokuComposter() {
	}

	public static void initialize() {
		loadStaticConfig();
	}

	public static boolean isEnabled() {
		return enabled && MadokuItem.isEnabled();
	}

	public static boolean isComposterItem(Item item) {
		if (!isEnabled() || item == null) {
			return false;
		}
		return MadokuItem.hasCategory(item, MadokuItemConfig.CATEGORY_COMPOSTER);
	}

	public static boolean isComposterItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return isComposterItem(stack.getItem());
	}

	public static int getComposterAdjustment(ItemStack stack) {
		if (!isEnabled() || stack == null || stack.isEmpty()) {
			return 0;
		}
		if (!isComposterItem(stack)) {
			return 0;
		}
		return MadokuItem.getComposterAdjustment(stack);
	}

	private static void loadStaticConfig() {
		try {
			if (!MadokuItem.isEnabled()) {
				enabled = false;
				emitConfigLoaded();
				return;
			}

			Path rootDirectory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(COMPOSTER_CONFIG_ROOT_FOLDER_NAME);
			Path settingsFile = resolveJsonFile(rootDirectory, COMPOSTER_CONFIG_SETTINGS_FILE_NAME);
			JsonObject settingsRoot = JsonStaticSystem.ensureManagedFile(
				settingsFile,
				MadokuComposterConfig.buildComposterSystemDefaults()
			);
			boolean composterEnabled = readBoolean(
				settingsRoot,
				MadokuComposterConfig.FIELD_COMPOSTER_SYSTEM_ENABLED,
				true
			);

			enabled = composterEnabled;
			emitConfigLoaded();
		} catch (IOException | RuntimeException exception) {
			enabled = false;
			LOGGER.error("Failed to load MadokuComposter folder config; disabling custom composter rules.", exception);
		}
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

	private static void emitConfigLoaded() {
		String metricId = "composter.config_loaded";
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ITEM, metricId)) {
			return;
		}
		MadokuDebug.event(metricId, MadokuDebug.Domain.ITEM)
			.side(MadokuDebug.Side.SERVER)
			.subject("composter:global")
			.field("enabled", enabled)
			.log();
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


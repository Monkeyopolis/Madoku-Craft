package madoku.craft.items;

import com.mojang.serialization.DataResult;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.JSONTypeManager;
import madoku.craft.api.json.MadokuJSONManager;
import madoku.craft.items.stacks.StacksConfigManager;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;

/** Runtime owner for custom stack limits. Death-drop behavior remains vanilla. */
public final class ItemsStacksManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(ItemsStacksManager.class);
	private static final String CONFIG_ROOT_FOLDER_NAME = "madoku-craft-items";
	private static final String CONFIG_FILE_NAME = "madoku-stacks";
	private static final StacksConfigManager configuration = new StacksConfigManager();

	private ItemsStacksManager() { }

	public static void initialize() { loadStaticConfig(); }

	public static void reset() { configuration.resetToDefaults(); }

	public static void onServerStarted(MinecraftServer server) { }

	public static boolean isEnabled() { return configuration.enabled; }

	public static int getStackLimit() { return configuration.customStackAmount; }

	public static int getMaxStackCap() { return StacksConfigManager.MAX_STACK_RUNTIME_CAP; }

	public static int adjustStackLimit(int originalLimit) {
		if (!isEnabled() || originalLimit <= 1) return originalLimit;
		return Math.max(originalLimit, getStackLimit());
	}

	public static boolean shouldExtendCodecRange(int minimum, int maximum) {
		return isEnabled() && minimum <= 1 && maximum == 99;
	}

	public static int getCodecUpperBound(int maximum) {
		return isEnabled() ? Math.max(maximum, getMaxStackCap()) : maximum;
	}

	public static DataResult<Integer> validateCodecCount(int minimum, int maximum, int value) {
		int upper = shouldExtendCodecRange(minimum, maximum) ? getCodecUpperBound(maximum) : maximum;
		if (value < minimum) return DataResult.error(() -> "Value must be within range [" + minimum + ";" + upper + "]: " + value);
		return DataResult.success(Math.min(value, upper));
	}

	public static String formatCompactStackCount(int count) {
		if (count < 1000) return Integer.toString(count);
		long value = count;
		if (value >= 1_000_000L) return formatCompactUnit(value, 1_000_000L, "M");
		return formatCompactUnit(value, 1_000L, "K");
	}

	private static String formatCompactUnit(long value, long unit, String suffix) {
		long whole = value / unit;
		if (whole >= 10L) return whole + suffix;
		long tenth = ((value % unit) * 10L) / unit;
		return tenth <= 0L ? whole + suffix : whole + "." + tenth + suffix;
	}

	private static void loadStaticConfig() {
		try {
			Path directory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(CONFIG_ROOT_FOLDER_NAME);
			Path configFile = directory.resolve(CONFIG_FILE_NAME + ".json");
			JSONFormatManager.ManagedDocument document = JSONFormatManager.readManagedDocument(configFile);
			JsonObject root = document.data();
			boolean changed = configuration.update(root);
			if (changed || !root.equals(document.data())) {
				JSONFormatManager.writeManagedDocument(configFile, root, document.settings(), JSONTypeManager.STATIC_CONFIG);
			}
		} catch (IOException | RuntimeException exception) {
			configuration.resetToDefaults();
			LOGGER.error("Failed to load Madoku stacks config; using defaults.", exception);
		}
	}
}

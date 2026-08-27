package madoku.craft.core.enchant;

import com.google.gson.JsonObject;

import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.json.MadokuJSONManager;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Owns the static Madoku Enchant configuration files. */
public final class EnchantConfigManager {
	private static final String CONFIG_FILE_NAME = "madoku-enchant.json";
	private static final String TABLE_GROUP = "enchantment-table";
	private static volatile EnchantSettings settings = EnchantSettings.DEFAULT;

	private EnchantConfigManager() {
	}

	public static void initialize() {
		load();
	}

	public static void reset() {
		settings = EnchantSettings.DEFAULT;
	}

	public static void onServerStarted(MinecraftServer server) {
		load();
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static boolean isEnchantmentTableEnabled() {
		return settings.enabled && settings.enchantmentTableEnabled;
	}

	public static Path enchantDirectory() {
		return MadokuJSONManager.getOrCreateGlobalSystemDirectory(MadokuEnchantManager.ENCHANT_FOLDER_NAME);
	}

	public static Path enchantmentsDirectory() {
		Path directory = enchantDirectory().resolve(MadokuEnchantManager.ENCHANTMENTS_FOLDER_NAME);
		try {
			Files.createDirectories(directory);
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to create Madoku Enchantments configuration directory.", exception);
		}
		return directory;
	}

	private static void load() {
		try {
			Path directory = enchantDirectory();
			Path file = directory.resolve(CONFIG_FILE_NAME);
			JsonObject normalized = JSONFormatManager.ensureManagedFile(file, buildDefaults());
			JSONFormatManager.ManagedDocument document = JSONFormatManager.readManagedDocument(file);
			JsonObject table = object(normalized, TABLE_GROUP);
			boolean enabled = booleanValue(document.settings(), "enabled", true);
			settings = new EnchantSettings(
				enabled,
				booleanValue(table, "enabled", true)
			);
			enchantmentsDirectory();
		} catch (IOException | RuntimeException exception) {
			settings = EnchantSettings.DEFAULT;
		}
	}

	private static JsonObject buildDefaults() {
		return JSONFormatManager.object()
			.group(TABLE_GROUP, group -> group.put("enabled", true))
			.build();
	}

	private static JsonObject object(JsonObject source, String key) {
		if (source == null || !source.has(key) || !source.get(key).isJsonObject()) return new JsonObject();
		return source.getAsJsonObject(key);
	}

	private static boolean booleanValue(JsonObject source, String key, boolean fallback) {
		try {
			return source != null && source.has(key) ? source.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static final class EnchantSettings {
		private static final EnchantSettings DEFAULT = new EnchantSettings(true, true);
		private final boolean enabled;
		private final boolean enchantmentTableEnabled;

		private EnchantSettings(boolean enabled, boolean enchantmentTableEnabled) {
			this.enabled = enabled;
			this.enchantmentTableEnabled = enchantmentTableEnabled;
		}
	}
}

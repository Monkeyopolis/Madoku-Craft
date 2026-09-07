package madoku.craft.java.core.enchant;

import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Owns the static Madoku Enchant configuration files. */
public final class EnchantConfigAPIManager {
	private static final String CONFIG_FILE_NAME = "madoku-enchant.json";
	private static final String TABLE_GROUP = "enchantment-table";
	private static final String CUSTOM_ENCHANTMENTS_GROUP = "custom-enchantments";
	private static volatile EnchantSettings settings = EnchantSettings.DEFAULT;
	private static volatile EnchantSettings clientSynchronizedSettings;

	private EnchantConfigAPIManager() {
	}

	public static void initialize() {
		load();
	}

	public static void reset() {
		settings = EnchantSettings.DEFAULT;
		clientSynchronizedSettings = null;
	}

	public static void onServerStarted(MinecraftServer server) {
		load();
	}

	public static boolean isEnabled() {
		return effectiveSettings().enabled;
	}

	public static boolean isEnchantmentTableEnabled() {
		EnchantSettings effective = effectiveSettings();
		return effective.enabled && effective.enchantmentTableEnabled;
	}

	public static boolean areCustomEnchantmentsEnabled() {
		EnchantSettings effective = effectiveSettings();
		return effective.enabled && effective.customEnchantmentsEnabled;
	}

	static void applyClientSynchronizedSettings(boolean enabled, boolean enchantmentTableEnabled, boolean customEnchantmentsEnabled) {
		clientSynchronizedSettings = new EnchantSettings(enabled, enchantmentTableEnabled, customEnchantmentsEnabled);
	}

	static void resetClientSynchronizedState() {
		clientSynchronizedSettings = null;
	}

	private static EnchantSettings effectiveSettings() {
		EnchantSettings synchronizedSettings = clientSynchronizedSettings;
		return synchronizedSettings == null ? settings : synchronizedSettings;
	}

	public static Path enchantDirectory() {
		return JSONAPIManager.getOrCreateGlobalSystemDirectory(EnchantAPIManager.ENCHANT_FOLDER_NAME);
	}

	public static Path enchantmentsDirectory() {
		Path directory = enchantDirectory().resolve(EnchantAPIManager.ENCHANTMENTS_FOLDER_NAME);
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
			JsonObject normalized = JSONFormatAPIManager.ensureManagedFile(file, buildDefaults());
			JSONFormatAPIManager.ManagedDocument document = JSONFormatAPIManager.readManagedDocument(file);
			JsonObject table = object(normalized, TABLE_GROUP);
			JsonObject customEnchantments = object(normalized, CUSTOM_ENCHANTMENTS_GROUP);
			boolean enabled = booleanValue(document.settings(), "enabled", true);
			settings = new EnchantSettings(
				enabled,
				booleanValue(table, "enabled", true),
				booleanValue(customEnchantments, "enabled", true)
			);
			enchantmentsDirectory();
		} catch (IOException | RuntimeException exception) {
			settings = EnchantSettings.DEFAULT;
		}
	}

	private static JsonObject buildDefaults() {
		return JSONFormatAPIManager.object()
			.group(TABLE_GROUP, group -> group.put("enabled", true))
			.group(CUSTOM_ENCHANTMENTS_GROUP, group -> group.put("enabled", true))
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
		private static final EnchantSettings DEFAULT = new EnchantSettings(true, true, true);
		private final boolean enabled;
		private final boolean enchantmentTableEnabled;
		private final boolean customEnchantmentsEnabled;

		private EnchantSettings(boolean enabled, boolean enchantmentTableEnabled, boolean customEnchantmentsEnabled) {
			this.enabled = enabled;
			this.enchantmentTableEnabled = enchantmentTableEnabled;
			this.customEnchantmentsEnabled = customEnchantmentsEnabled;
		}
	}
}


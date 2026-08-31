package madoku.craft.core.smithing;

import madoku.craft.core.MadokuCoreManager;
import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.json.MadokuJSONManager;
import madoku.craft.core.sync.SyncConfigManager;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Path;

/** Owns the static Madoku Smithing configuration. */
public final class SmithingConfigManager {
	private static final String CONFIG_ROOT_FOLDER_NAME = MadokuCoreManager.CORE_FOLDER_NAME + "/madoku-smithing";
	private static final String CONFIG_FILE_NAME = "madoku-smithing.json";
	private static final String FIELD_ENABLED = "enabled";
	private static volatile boolean enabled = true;
	private static volatile Boolean clientSynchronizedEnabled;

	private SmithingConfigManager() {
	}

	public static void initialize() {
		load();
		SyncConfigManager.register(
			"smithing",
			SmithingConfigManager::createClientSyncSnapshot,
			SmithingConfigManager::applyClientSyncSnapshot,
			SmithingConfigManager::resetClientSyncState
		);
	}

	public static void reset() {
		enabled = true;
		clientSynchronizedEnabled = null;
	}

	public static void onServerStarted(MinecraftServer server) {
		load();
	}

	public static boolean isEnabled() {
		Boolean synchronizedEnabled = clientSynchronizedEnabled;
		return synchronizedEnabled == null ? enabled : synchronizedEnabled;
	}

	public static String createClientSyncSnapshot() {
		return JSONFormatManager.object().put(FIELD_ENABLED, enabled).build().toString();
	}

	public static void applyClientSyncSnapshot(String snapshot) {
		try {
			var root = com.google.gson.JsonParser.parseString(snapshot).getAsJsonObject();
			clientSynchronizedEnabled = booleanValue(root, FIELD_ENABLED, enabled);
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Invalid smithing configuration snapshot.", exception);
		}
	}

	public static void resetClientSyncState() {
		clientSynchronizedEnabled = null;
	}

	public static Path smithingDirectory() {
		return MadokuJSONManager.getOrCreateGlobalSystemDirectory(CONFIG_ROOT_FOLDER_NAME);
	}

	private static void load() {
		try {
			Path file = smithingDirectory().resolve(CONFIG_FILE_NAME);
			var normalized = JSONFormatManager.ensureManagedFile(file, buildDefaults());
			enabled = booleanValue(normalized, FIELD_ENABLED, true);
		} catch (IOException | RuntimeException exception) {
			enabled = true;
		}
	}

	private static com.google.gson.JsonObject buildDefaults() {
		return JSONFormatManager.object()
			.put(FIELD_ENABLED, true)
			.build();
	}

	private static boolean booleanValue(com.google.gson.JsonObject source, String key, boolean fallback) {
		try {
			return source != null && source.has(key) ? source.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}
}

package madoku.craft.java.core.smithing;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;
import madoku.craft.java.core.sync.SyncConfigAPIManager;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Path;

/** Owns the static Madoku Smithing configuration. */
public final class SmithingConfigAPIManager {
	private static final String CONFIG_ROOT_FOLDER_NAME = "madoku-craft-core/madoku-smithing";
	private static final String CONFIG_FILE_NAME = "madoku-smithing.json";
	private static final String FIELD_ENABLED = "enabled";
	private static volatile boolean enabled = true;
	private static volatile Boolean clientSynchronizedEnabled;

	private SmithingConfigAPIManager() {
	}

	public static void initialize() {
		load();
		SyncConfigAPIManager.register(
			"smithing",
			SmithingConfigAPIManager::createClientSyncSnapshot,
			SmithingConfigAPIManager::applyClientSyncSnapshot,
			SmithingConfigAPIManager::resetClientSyncState
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
		return JSONFormatAPIManager.object().put(FIELD_ENABLED, enabled).build().toString();
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
		return JSONAPIManager.getOrCreateGlobalSystemDirectory(CONFIG_ROOT_FOLDER_NAME);
	}

	private static void load() {
		try {
			Path file = smithingDirectory().resolve(CONFIG_FILE_NAME);
			var normalized = JSONFormatAPIManager.ensureManagedFile(file, buildDefaults());
			enabled = booleanValue(normalized, FIELD_ENABLED, true);
		} catch (IOException | RuntimeException exception) {
			enabled = true;
		}
	}

	private static com.google.gson.JsonObject buildDefaults() {
		return JSONFormatAPIManager.object()
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



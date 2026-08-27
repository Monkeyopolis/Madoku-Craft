package madoku.craft.core.smithing;

import madoku.craft.core.MadokuCoreManager;
import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.json.MadokuJSONManager;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Path;

/** Owns the static Madoku Smithing configuration. */
public final class SmithingConfigManager {
	private static final String CONFIG_ROOT_FOLDER_NAME = MadokuCoreManager.API_FOLDER_NAME + "/madoku-smithing";
	private static final String CONFIG_FILE_NAME = "madoku-smithing.json";
	private static final String FIELD_ENABLED = "enabled";
	private static volatile boolean enabled = true;

	private SmithingConfigManager() {
	}

	public static void initialize() {
		load();
	}

	public static void reset() {
		enabled = true;
	}

	public static void onServerStarted(MinecraftServer server) {
		load();
	}

	public static boolean isEnabled() {
		return enabled;
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

package madoku.craft.loot.system;

import com.google.gson.JsonObject;
import madoku.craft.config.DynamicStaticSystem;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

public final class MadokuLootTableEquipments {
	private static final String EQUIPMENT_CONFIG_ROOT_FOLDER_NAME = "madoku-craft-loot-tables";
	private static final String EQUIPMENT_CONFIG_SETTINGS_FILE_NAME = "madoku-loot-tables";
	private static final String EQUIPMENT_CONFIG_TABLES_FOLDER_NAME = "madoku-equipments";

	private static volatile Snapshot snapshot = Snapshot.disabled();

	private MadokuLootTableEquipments() {
	}

	public static void initialize() {
		reloadNow();
	}

	public static synchronized void reloadNow() {
		try {
			Path rootDirectory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(EQUIPMENT_CONFIG_ROOT_FOLDER_NAME);
			Path settingsFile = resolveJsonFile(rootDirectory, EQUIPMENT_CONFIG_SETTINGS_FILE_NAME);
			JsonObject settingsRoot = JsonStaticSystem.ensureManagedFile(settingsFile, LootTableConfigManager.buildSettingsDefaults());
			boolean enabled = readBoolean(settingsRoot, LootTableConfigManager.FIELD_ENABLED, true);
			boolean customEntityEquipmentEnabled = readBoolean(
				settingsRoot,
				LootTableConfigManager.FIELD_CUSTOM_ENTITY_EQUIPMENT,
				true
			);

			Path tablesDirectory = rootDirectory.resolve(EQUIPMENT_CONFIG_TABLES_FOLDER_NAME);
			Map<String, JsonObject> normalizedFiles = DynamicStaticSystem.ensureManagedFolder(
				tablesDirectory,
				LootTableEquipmentsConfig.buildDefaultEquipmentTableFiles(),
				ignored -> new JsonObject(),
				(fileKey, sourceRoot) -> true,
				(key, sourceValue) -> null
			);
			snapshot = (enabled && customEntityEquipmentEnabled)
				? new Snapshot(true, Map.copyOf(normalizedFiles))
				: Snapshot.disabled();
		} catch (IOException | RuntimeException exception) {
			snapshot = Snapshot.disabled();
		}
	}

	public static JsonObject resolveEquipmentRootByReference(String rawReference) {
		Snapshot active = snapshot;
		if (!active.enabled()) {
			return new JsonObject();
		}
		String key = normalizeFileKey(rawReference);
		if (key.isBlank()) {
			return new JsonObject();
		}
		JsonObject root = active.files().get(key);
		return root == null ? new JsonObject() : root.deepCopy();
	}

	private static String normalizeFileKey(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
		int slashIndex = normalized.lastIndexOf('/');
		if (slashIndex >= 0 && slashIndex < normalized.length() - 1) {
			normalized = normalized.substring(slashIndex + 1);
		}
		if (normalized.endsWith(".json")) {
			normalized = normalized.substring(0, normalized.length() - ".json".length());
		}
		return normalized;
	}

	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Equipment config file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null || key == null) {
			return fallback;
		}
		var element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return element.getAsBoolean();
	}

	private record Snapshot(boolean enabled, Map<String, JsonObject> files) {
		private static Snapshot disabled() {
			return new Snapshot(false, Map.of());
		}
	}
}

package madoku.craft.api.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.MadokuAPIManager;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.JSONTypeManager;
import madoku.craft.api.json.MadokuJSONManager;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime group for indexed global world JSON data. */
public final class DataWorldManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataWorldManager.class);
	private static final String DATA_CONFIG_FOLDER = MadokuAPIManager.API_FOLDER_NAME + "/madoku-data";
	private static final String DATA_FILE_NAME = "madoku-data-world.json";
	private static final String FIELD_VERSION = "version";
	private static final String FIELD_SYSTEMS = "systems";
	private static final int DATA_VERSION = 1;

	private static final Map<String, JsonObject> SYSTEM_DATA = new LinkedHashMap<>();
	private static volatile boolean dirty;
	private static volatile boolean initialized;

	private DataWorldManager() { }

	public static void initialize() {
		SYSTEM_DATA.clear();
		dirty = false;
		initialized = true;
	}

	public static void reset() {
		SYSTEM_DATA.clear();
		dirty = false;
		initialized = false;
	}

	public static boolean isInitialized() {
		return initialized;
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) return;
		SYSTEM_DATA.clear();
		dirty = false;
		Path file = resolveDataFile(server);
		if (!Files.isRegularFile(file)) return;
		try {
			JSONFormatManager.ManagedDocument document = JSONFormatManager.readManagedDocument(file);
			JsonElement systemsElement = document.data().get(FIELD_SYSTEMS);
			if (systemsElement == null || !systemsElement.isJsonObject()) return;
			for (Map.Entry<String, JsonElement> entry : systemsElement.getAsJsonObject().entrySet()) {
				if (entry.getValue() != null && entry.getValue().isJsonObject()) {
					SYSTEM_DATA.put(normalizeSystemId(entry.getKey()), entry.getValue().getAsJsonObject().deepCopy());
				}
			}
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load Madoku world data file {}", file, exception);
		}
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server != null) {
			DataSystemsManager.registerSystem("world-data");
		}
	}

	public static void autosavePersistedData(MinecraftServer server) {
		savePersistedData(server);
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null || !dirty) return;
		Map<String, JsonObject> snapshot = new LinkedHashMap<>();
		for (Map.Entry<String, JsonObject> entry : SYSTEM_DATA.entrySet()) {
			if (entry.getKey() != null && entry.getValue() != null) {
				snapshot.put(entry.getKey(), entry.getValue().deepCopy());
			}
		}
		dirty = false;
		Path file = resolveDataFile(server);
		DataSaveCoordinatorManager.submit("world-data", file, () -> writeSnapshot(file, snapshot));
	}

	public static JsonObject getSystemData(String systemId) {
		JsonObject data = SYSTEM_DATA.get(normalizeSystemId(systemId));
		return data == null ? new JsonObject() : data.deepCopy();
	}

	public static void setSystemData(String systemId, JsonObject source) {
		String normalizedSystemId = normalizeSystemId(systemId);
		if (normalizedSystemId.isBlank()) return;
		JsonObject safeData = source == null ? new JsonObject() : source.deepCopy();
		JsonObject previous = SYSTEM_DATA.put(normalizedSystemId, safeData);
		if (previous == null || !previous.equals(safeData)) dirty = true;
	}

	private static void writeSnapshot(Path file, Map<String, JsonObject> snapshot) throws IOException {
		Path parent = file.toAbsolutePath().normalize().getParent();
		if (snapshot == null || snapshot.isEmpty()) {
			Files.deleteIfExists(file);
			return;
		}
		if (parent != null) Files.createDirectories(parent);
		JsonObject systems = new JsonObject();
		for (Map.Entry<String, JsonObject> entry : snapshot.entrySet()) {
			if (entry.getKey() != null && entry.getValue() != null) {
				systems.add(entry.getKey(), entry.getValue().deepCopy());
			}
		}
		JsonObject root = JSONFormatManager.object()
			.put(FIELD_VERSION, DATA_VERSION)
			.put(FIELD_SYSTEMS, systems)
			.build();
		Path temporary = Files.createTempFile(parent, "madoku-world-data-", ".tmp");
		try {
			JSONFormatManager.writeManagedDocument(temporary, root, new JsonObject(), JSONTypeManager.STATIC_DATA);
			try {
				Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.AtomicMoveNotSupportedException exception) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static Path resolveDataFile(MinecraftServer server) {
		return MadokuJSONManager.getWorldRootDirectory(server)
			.resolve(DATA_CONFIG_FOLDER)
			.resolve(DATA_FILE_NAME)
			.normalize();
	}

	private static String normalizeSystemId(String systemId) {
		return systemId == null ? "" : systemId.trim().toLowerCase(java.util.Locale.ROOT);
	}

}

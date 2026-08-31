package madoku.craft.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.core.MadokuCoreManager;
import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.json.JSONTypeManager;
import madoku.craft.core.json.MadokuJSONManager;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** Runtime group for indexed per-player JSON data. */
public final class DataPlayerManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataPlayerManager.class);
	private static final String DATA_CONFIG_FOLDER = MadokuCoreManager.CORE_FOLDER_NAME + "/madoku-data/madoku-data-player";
	private static final String FIELD_VERSION = "version";
	private static final String FIELD_PLAYER_UUID = "player-uuid";
	private static final String FIELD_SYSTEMS = "systems";
	private static final int DATA_VERSION = 1;

	private static final Map<UUID, Map<String, JsonObject>> PLAYER_DATA = new LinkedHashMap<>();
	private static final Set<UUID> DIRTY_PLAYERS = new LinkedHashSet<>();
	private static volatile boolean initialized;

	private DataPlayerManager() { }

	public static void initialize() {
		PLAYER_DATA.clear();
		DIRTY_PLAYERS.clear();
		initialized = true;
	}

	public static void reset() {
		PLAYER_DATA.clear();
		DIRTY_PLAYERS.clear();
		initialized = false;
	}

	public static boolean isInitialized() { return initialized; }

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) return;
		PLAYER_DATA.clear();
		DIRTY_PLAYERS.clear();
		Path directory = resolveDataDirectory(server);
		if (!Files.isDirectory(directory)) return;
		try (Stream<Path> files = Files.list(directory)) {
			files.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json"))
				.forEach(DataPlayerManager::loadPlayerFile);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to list Madoku player data directory {}", directory, exception);
		}
	}

	public static void onServerStarted(MinecraftServer server) { }

	public static void autosavePersistedData(MinecraftServer server) {
		if (server != null) savePersistedData(server);
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null || DIRTY_PLAYERS.isEmpty()) return;
		Set<UUID> dirtyPlayers = new LinkedHashSet<>(DIRTY_PLAYERS);
		DIRTY_PLAYERS.removeAll(dirtyPlayers);
		for (UUID playerId : dirtyPlayers) {
			Map<String, JsonObject> systems = PLAYER_DATA.get(playerId);
			Map<String, JsonObject> snapshot = new LinkedHashMap<>();
			if (systems != null) {
				for (Map.Entry<String, JsonObject> entry : systems.entrySet()) {
					if (entry.getKey() != null && entry.getValue() != null) snapshot.put(entry.getKey(), entry.getValue().deepCopy());
				}
			}
			Path file = resolvePlayerFile(server, playerId);
			DataSaveCoordinatorManager.submit("player-data-" + playerId, file, () -> writePlayerSnapshot(file, playerId, snapshot));
		}
	}

	public static JsonObject getSystemData(String systemId) {
		return getSystemData(systemId, "players", "uuid");
	}

	public static JsonObject getSystemData(String systemId, String entriesKey, String playerIdKey) {
		String normalizedSystemId = normalizeSystemId(systemId);
		String normalizedEntriesKey = normalizeKey(entriesKey, "players");
		String normalizedPlayerIdKey = normalizeKey(playerIdKey, "uuid");
		JsonArray entries = new JsonArray();
		if (normalizedSystemId.isBlank()) return JSONFormatManager.object().put(normalizedEntriesKey, entries).build();
		for (Map.Entry<UUID, Map<String, JsonObject>> playerEntry : PLAYER_DATA.entrySet()) {
			JsonObject data = playerEntry.getValue() == null ? null : playerEntry.getValue().get(normalizedSystemId);
			if (data == null) continue;
			JsonObject copy = data.deepCopy();
			copy.addProperty(normalizedPlayerIdKey, playerEntry.getKey().toString());
			entries.add(copy);
		}
		return JSONFormatManager.object().put(normalizedEntriesKey, entries).build();
	}

	public static void setSystemData(String systemId, JsonObject source) {
		setSystemData(systemId, source, "players", "uuid");
	}

	public static void setSystemData(String systemId, JsonObject source, String entriesKey, String playerIdKey) {
		String normalizedSystemId = normalizeSystemId(systemId);
		String normalizedEntriesKey = normalizeKey(entriesKey, "players");
		String normalizedPlayerIdKey = normalizeKey(playerIdKey, "uuid");
		if (normalizedSystemId.isBlank()) return;
		Set<UUID> seenPlayers = new LinkedHashSet<>();
		JsonElement entriesElement = source == null ? null : source.get(normalizedEntriesKey);
		if (entriesElement != null && entriesElement.isJsonArray()) {
			for (JsonElement element : entriesElement.getAsJsonArray()) {
				if (element == null || !element.isJsonObject()) continue;
				JsonObject data = element.getAsJsonObject().deepCopy();
				UUID playerId = parseUuid(data.get(normalizedPlayerIdKey));
				if (playerId == null) continue;
				data.remove(normalizedPlayerIdKey);
				seenPlayers.add(playerId);
				Map<String, JsonObject> systems = PLAYER_DATA.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>());
				JsonObject previous = systems.put(normalizedSystemId, data);
				if (previous == null || !previous.equals(data)) DIRTY_PLAYERS.add(playerId);
			}
		}
		for (Map.Entry<UUID, Map<String, JsonObject>> entry : PLAYER_DATA.entrySet()) {
			if (seenPlayers.contains(entry.getKey())) continue;
			if (entry.getValue().remove(normalizedSystemId) != null) DIRTY_PLAYERS.add(entry.getKey());
		}
		PLAYER_DATA.entrySet().removeIf(entry -> entry.getValue().isEmpty() && !DIRTY_PLAYERS.contains(entry.getKey()));
	}

	public static long getAutoSaveIntervalTicks() { return DataWorldChunkManager.getAutoSaveIntervalTicks(); }

	private static void loadPlayerFile(Path file) {
		try {
			JSONFormatManager.ManagedDocument document = JSONFormatManager.readManagedDocument(file);
			JsonObject root = document.data();
			UUID filePlayerId = parseUuid(root.get(FIELD_PLAYER_UUID));
			if (filePlayerId == null) filePlayerId = parseUuid(file.getFileName().toString().replaceFirst("\\.json$", ""));
			if (filePlayerId == null) return;
			JsonElement systemsElement = root.get(FIELD_SYSTEMS);
			if (systemsElement == null || !systemsElement.isJsonObject()) return;
			Map<String, JsonObject> systems = PLAYER_DATA.computeIfAbsent(filePlayerId, ignored -> new LinkedHashMap<>());
			for (Map.Entry<String, JsonElement> entry : systemsElement.getAsJsonObject().entrySet()) {
				if (entry.getValue() != null && entry.getValue().isJsonObject()) systems.put(normalizeSystemId(entry.getKey()), entry.getValue().getAsJsonObject().deepCopy());
			}
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load Madoku player data file {}", file, exception);
		}
	}

	private static void writePlayerSnapshot(Path file, UUID playerId, Map<String, JsonObject> systems) throws IOException {
		if (systems == null || systems.isEmpty()) {
			Files.deleteIfExists(file);
			return;
		}
		JsonObject systemData = new JsonObject();
		for (Map.Entry<String, JsonObject> entry : systems.entrySet()) {
			if (entry.getKey() != null && entry.getValue() != null) systemData.add(entry.getKey(), entry.getValue().deepCopy());
		}
		JsonObject root = JSONFormatManager.object()
			.put(FIELD_VERSION, DATA_VERSION)
			.put(FIELD_PLAYER_UUID, playerId.toString())
			.put(FIELD_SYSTEMS, systemData)
			.build();
		Path parent = file.toAbsolutePath().normalize().getParent();
		if (parent != null) Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, "madoku-player-data-", ".tmp");
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

	private static Path resolveDataDirectory(MinecraftServer server) {
		return MadokuJSONManager.getWorldRootDirectory(server).resolve(DATA_CONFIG_FOLDER).normalize();
	}

	private static Path resolvePlayerFile(MinecraftServer server, UUID playerId) {
		return resolveDataDirectory(server).resolve(playerId.toString() + ".json").normalize();
	}

	private static String normalizeSystemId(String systemId) {
		return systemId == null ? "" : systemId.trim().toLowerCase(java.util.Locale.ROOT);
	}

	private static String normalizeKey(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static UUID parseUuid(JsonElement element) {
		if (element == null || element.isJsonNull()) return null;
		return parseUuid(element.getAsString());
	}

	private static UUID parseUuid(String value) {
		if (value == null || value.isBlank()) return null;
		try { return UUID.fromString(value); }
		catch (RuntimeException ignored) { return null; }
	}

}

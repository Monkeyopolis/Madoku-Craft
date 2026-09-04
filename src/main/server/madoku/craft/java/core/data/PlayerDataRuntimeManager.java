package madoku.craft.java.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Internal runtime for indexed per-player data backed by vanilla SavedData. */
final class PlayerDataRuntimeManager {
	private static final String FIELD_PLAYERS = "players";
	private static final String FIELD_SYSTEMS = "systems";
	private static final Map<UUID, Map<String, JsonObject>> PLAYER_DATA = new LinkedHashMap<>();
	private static final Set<UUID> DIRTY_PLAYERS = new LinkedHashSet<>();
	private static volatile MinecraftServer currentServer;
	private static volatile boolean initialized;

	private PlayerDataRuntimeManager() { }

	public static void initialize() {
		PLAYER_DATA.clear();
		DIRTY_PLAYERS.clear();
		currentServer = null;
		initialized = true;
	}

	public static void reset() {
		PLAYER_DATA.clear();
		DIRTY_PLAYERS.clear();
		currentServer = null;
		initialized = false;
	}

	public static boolean isInitialized() { return initialized; }

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) return;
		currentServer = server;
		PLAYER_DATA.clear();
		DIRTY_PLAYERS.clear();
		MadokuSavedData savedData = MadokuSavedDataManager.players(server);
		CompoundTag root = savedData.copyData();
		CompoundTag players = root.getCompoundOrEmpty(FIELD_PLAYERS);
		for (Map.Entry<String, Tag> playerEntry : players.entrySet()) {
			UUID playerId;
			try { playerId = UUID.fromString(playerEntry.getKey()); }
			catch (RuntimeException ignored) { continue; }
			if (!(playerEntry.getValue() instanceof CompoundTag playerTag)) continue;
			CompoundTag systems = playerTag.getCompoundOrEmpty(FIELD_SYSTEMS);
			Map<String, JsonObject> target = PLAYER_DATA.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>());
			for (Map.Entry<String, Tag> systemEntry : systems.entrySet()) {
				if (systemEntry.getValue() instanceof CompoundTag compound) {
					target.put(normalizeSystemId(systemEntry.getKey()), MadokuSavedDataManager.toJson(compound));
				}
			}
		}
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server != null) currentServer = server;
	}

	public static void autosavePersistedData(MinecraftServer server) { }

	public static void savePersistedData(MinecraftServer server) {
		if (server != null) syncSavedData(server);
	}

	public static JsonObject getSystemData(String systemId) {
		return getSystemData(systemId, "players", "uuid");
	}

	public static JsonObject getSystemData(String systemId, String entriesKey, String playerIdKey) {
		String normalizedSystemId = normalizeSystemId(systemId);
		String normalizedEntriesKey = normalizeKey(entriesKey, "players");
		String normalizedPlayerIdKey = normalizeKey(playerIdKey, "uuid");
		JsonArray entries = new JsonArray();
		if (!normalizedSystemId.isBlank()) {
			for (Map.Entry<UUID, Map<String, JsonObject>> playerEntry : PLAYER_DATA.entrySet()) {
				JsonObject data = playerEntry.getValue() == null ? null : playerEntry.getValue().get(normalizedSystemId);
				if (data == null) continue;
				JsonObject copy = data.deepCopy();
				copy.addProperty(normalizedPlayerIdKey, playerEntry.getKey().toString());
				entries.add(copy);
			}
		}
		JsonObject result = new JsonObject();
		result.add(normalizedEntriesKey, entries);
		return result;
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
		if (!DIRTY_PLAYERS.isEmpty() && currentServer != null) syncSavedData(currentServer);
	}

	public static long getAutoSaveIntervalTicks() { return WorldChunkDataRuntimeManager.getAutoSaveIntervalTicks(); }

	private static void syncSavedData(MinecraftServer server) {
		MadokuSavedData savedData = MadokuSavedDataManager.players(server);
		CompoundTag players = new CompoundTag();
		for (Map.Entry<UUID, Map<String, JsonObject>> playerEntry : PLAYER_DATA.entrySet()) {
			if (playerEntry.getValue() == null || playerEntry.getValue().isEmpty()) continue;
			CompoundTag systems = new CompoundTag();
			for (Map.Entry<String, JsonObject> systemEntry : playerEntry.getValue().entrySet()) {
				if (systemEntry.getKey() != null && systemEntry.getValue() != null) {
					systems.put(systemEntry.getKey(), MadokuSavedDataManager.toNbt(systemEntry.getValue()));
				}
			}
			CompoundTag player = new CompoundTag();
			player.put(FIELD_SYSTEMS, systems);
			players.put(playerEntry.getKey().toString(), player);
		}
		CompoundTag root = savedData.copyData();
		root.put(FIELD_PLAYERS, players);
		savedData.replaceData(root);
		DIRTY_PLAYERS.clear();
	}

	private static String normalizeSystemId(String systemId) { return systemId == null ? "" : systemId.trim().toLowerCase(java.util.Locale.ROOT); }
	private static String normalizeKey(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
	private static UUID parseUuid(JsonElement element) {
		if (element == null || element.isJsonNull()) return null;
		return parseUuid(element.getAsString());
	}

	private static UUID parseUuid(String value) {
		try { return value == null ? null : UUID.fromString(value); }
		catch (RuntimeException ignored) { return null; }
	}
}

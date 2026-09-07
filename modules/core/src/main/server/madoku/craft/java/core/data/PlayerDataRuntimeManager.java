package madoku.craft.java.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Internal runtime for player-owned data stored in vanilla player NBT through Fabric attachments. */
final class PlayerDataRuntimeManager {
	private static final String FIELD_SYSTEMS = "systems";
	private static final AttachmentType<CompoundTag> PLAYER_DATA_ATTACHMENT = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath("madoku-craft", "player-data"),
		builder -> builder.persistent(CompoundTag.CODEC).copyOnDeath()
	);
	private static final Map<UUID, Map<String, JsonObject>> PLAYER_DATA = new LinkedHashMap<>();
	private static volatile MinecraftServer currentServer;
	private static volatile boolean initialized;

	private PlayerDataRuntimeManager() { }

	public static void initialize() {
		PLAYER_DATA.clear();
		currentServer = null;
		initialized = true;
		ServerPlayerEvents.JOIN.register(PlayerDataRuntimeManager::loadPlayerAttachment);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> loadPlayerAttachment(newPlayer));
	}

	public static void reset() {
		PLAYER_DATA.clear();
		currentServer = null;
		initialized = false;
	}

	public static boolean isInitialized() { return initialized; }

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) return;
		currentServer = server;
		PLAYER_DATA.clear();
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server != null) {
			currentServer = server;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) loadPlayerAttachment(player);
		}
	}

	public static void autosavePersistedData(MinecraftServer server) {
		// Vanilla owns player-file scheduling. The data is synchronized to the player entity
		// before vanilla's saveEverything call.
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) return;
		currentServer = server;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) syncPlayerAttachment(player);
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

	/** Returns one player's data in the same aggregate shape as {@link #getSystemData(String, String, String)}. */
	public static JsonObject getSystemDataForPlayer(ServerPlayer player, String systemId, String entriesKey, String playerIdKey) {
		if (player == null) return emptyEntries(entriesKey, "players");
		loadPlayerAttachment(player);
		String normalizedSystemId = normalizeSystemId(systemId);
		String normalizedEntriesKey = normalizeKey(entriesKey, "players");
		String normalizedPlayerIdKey = normalizeKey(playerIdKey, "uuid");
		JsonArray entries = new JsonArray();
		JsonObject data = PLAYER_DATA.getOrDefault(player.getUUID(), Map.of()).get(normalizedSystemId);
		if (data != null) {
			JsonObject copy = data.deepCopy();
			copy.addProperty(normalizedPlayerIdKey, player.getUUID().toString());
			entries.add(copy);
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
		Map<UUID, JsonObject> updates = new LinkedHashMap<>();
		JsonElement entriesElement = source == null ? null : source.get(normalizedEntriesKey);
		if (entriesElement != null && entriesElement.isJsonArray()) {
			for (JsonElement element : entriesElement.getAsJsonArray()) {
				if (element == null || !element.isJsonObject()) continue;
				JsonObject data = element.getAsJsonObject().deepCopy();
				UUID playerId = parseUuid(data.get(normalizedPlayerIdKey));
				if (playerId == null) continue;
				data.remove(normalizedPlayerIdKey);
				updates.put(playerId, data);
			}
		}

		for (Map.Entry<UUID, Map<String, JsonObject>> entry : PLAYER_DATA.entrySet()) {
			JsonObject updated = updates.remove(entry.getKey());
			if (updated == null) {
				entry.getValue().remove(normalizedSystemId);
			} else {
				entry.getValue().put(normalizedSystemId, updated);
			}
		}
		for (Map.Entry<UUID, JsonObject> update : updates.entrySet()) {
			PLAYER_DATA.computeIfAbsent(update.getKey(), ignored -> new LinkedHashMap<>())
				.put(normalizedSystemId, update.getValue());
		}
		PLAYER_DATA.entrySet().removeIf(entry -> entry.getValue().isEmpty());
		if (currentServer != null) {
			for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
				syncPlayerAttachment(player);
			}
		}
	}

	public static long getAutoSaveIntervalTicks() { return WorldChunkDataRuntimeManager.getAutoSaveIntervalTicks(); }

	private static void loadPlayerAttachment(ServerPlayer player) {
		if (player == null) return;
		currentServer = player.level().getServer();
		CompoundTag root = ((AttachmentTarget) player).getAttached(PLAYER_DATA_ATTACHMENT);
		Map<String, JsonObject> systems = new LinkedHashMap<>();
		CompoundTag systemTag = root == null ? null : root.getCompoundOrEmpty(FIELD_SYSTEMS);
		if (systemTag != null) {
			for (Map.Entry<String, Tag> entry : systemTag.entrySet()) {
				if (entry.getValue() instanceof CompoundTag compound) {
					systems.put(normalizeSystemId(entry.getKey()), MadokuSavedDataManager.toJson(compound));
				}
			}
		}
		PLAYER_DATA.put(player.getUUID(), systems);
	}

	private static void syncPlayerAttachment(ServerPlayer player) {
		if (player == null) return;
		Map<String, JsonObject> systems = PLAYER_DATA.get(player.getUUID());
		CompoundTag root = new CompoundTag();
		CompoundTag systemTag = new CompoundTag();
		if (systems != null) {
			for (Map.Entry<String, JsonObject> entry : systems.entrySet()) {
				if (entry.getKey() != null && entry.getValue() != null) {
					systemTag.put(entry.getKey(), MadokuSavedDataManager.toNbt(entry.getValue()));
				}
			}
		}
		root.put(FIELD_SYSTEMS, systemTag);
		((AttachmentTarget) player).setAttached(PLAYER_DATA_ATTACHMENT, root);
	}

	private static JsonObject emptyEntries(String requestedKey, String fallback) {
		JsonObject result = new JsonObject();
		result.add(normalizeKey(requestedKey, fallback), new JsonArray());
		return result;
	}

	private static String normalizeSystemId(String systemId) { return systemId == null ? "" : systemId.trim().toLowerCase(java.util.Locale.ROOT); }
	private static String normalizeKey(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
	private static UUID parseUuid(JsonElement element) {
		if (element == null || element.isJsonNull()) return null;
		try { return UUID.fromString(element.getAsString()); }
		catch (RuntimeException ignored) { return null; }
	}
}

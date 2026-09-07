package madoku.craft.java.levels;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.java.core.data.PlayerDataAPIManager;
import madoku.craft.java.core.sync.SyncPlayerAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owns runtime player level, experience, stat-level, and persistence state. */
public final class LevelsPlayerManager {
	private static final String DATA_FILE_NAME = "madoku-levels";
	private static final Map<UUID, LevelsPlayerState> PLAYER_STATES = new HashMap<>();
	private static final Set<UUID> DIRTY_PLAYERS = new HashSet<>();
	private static long lastAutosaveBucket = Long.MIN_VALUE;

	private LevelsPlayerManager() { }

	public static void initialize() {
		ServerPlayerEvents.JOIN.register(LevelsPlayerManager::handlePlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register(LevelsPlayerManager::handlePlayerRespawn);
	}

	public static void reset() {
		PLAYER_STATES.clear();
		DIRTY_PLAYERS.clear();
		lastAutosaveBucket = Long.MIN_VALUE;
	}

	public static LevelsPlayerState state(ServerPlayer player) {
		return PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> LevelsPlayerState.defaults(requiredXpForLevel(1)));
	}

	public static void addXp(ServerPlayer player, int xpAmount) {
		if (player == null || xpAmount <= 0 || !MadokuLevelsManager.isEnabled()) return;
		LevelsPlayerState state = state(player);
		int maximum = maxPlayerLevel();
		if (state.level >= maximum) return;
		state.currentXp += xpAmount;
		while (state.currentXp >= state.requiredXp && state.level < maximum) {
			state.currentXp -= state.requiredXp;
			state.level++;
			state.availablePoints++;
			state.requiredXp = requiredXpForLevel(state.level);
		}
		if (state.level >= maximum) state.currentXp = 0;
		LevelsAttributesManager.applyPlayerAttributes(player);
		markDirty(player.getUUID());
	}

	public static void upgradeStat(ServerPlayer player, String statId) {
		if (player == null || !MadokuLevelsManager.isEnabled()) return;
		LevelStat stat = LevelStat.fromId(statId);
		if (stat == null) return;
		LevelsPlayerState state = state(player);
		int current = state.statLevel(stat);
		if (state.availablePoints <= 0 || current >= stat.maxLevel()) return;
		state.statLevels.put(stat, stat.clampLevel(current + 1));
		state.availablePoints--;
		LevelsAttributesManager.applyPlayerAttributes(player);
		LevelsFeatureAPIManager.handleMaximumHungerChanged(player);
		markDirty(player.getUUID());
	}

	public static int getPlayerHungerBonusPoints(ServerPlayer player) {
		return player == null ? 0 : LevelsAttributesManager.hungerBonusPoints(player, state(player).statLevel(LevelStat.HUNGER));
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) return;
		LevelsConfigManager.reload();
		JsonObject data = PlayerDataAPIManager.getSystemData(DATA_FILE_NAME);
		applyPersistedData(data);
		long interval = Math.max(1L, PlayerDataAPIManager.getAutoSaveIntervalTicks());
		lastAutosaveBucket = Math.floorDiv(TimeAPIManager.getGameplayTicks(), Math.max(1L, interval));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			state(player);
			LevelsAttributesManager.applyPlayerAttributes(player);
			LevelsFeatureAPIManager.restoreJoinHealth(player);
			markDirty(player.getUUID());
		}
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) return;
		long interval = Math.max(1L, PlayerDataAPIManager.getAutoSaveIntervalTicks());
		long bucket = Math.floorDiv(TimeAPIManager.getGameplayTicks(), interval);
		if (bucket != lastAutosaveBucket) {
			lastAutosaveBucket = bucket;
			savePersistedData(server);
		}
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server != null) PlayerDataAPIManager.setSystemData(DATA_FILE_NAME, toPersistedData());
	}

	public static void flushDirtySyncs(MinecraftServer server) {
		if (server == null || DIRTY_PLAYERS.isEmpty() || !SyncPlayerAPIManager.shouldFlushDirtySyncs(server)) return;
		Set<UUID> synced = new HashSet<>();
		for (UUID playerId : DIRTY_PLAYERS) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null) continue;
			LevelsPayloadAPIManager.Payload payload = LevelsPayloadAPIManager.createPayload(player);
			if (!SyncPlayerAPIManager.canSend(player, payload)) continue;
			SyncPlayerAPIManager.send(player, payload);
			synced.add(playerId);
		}
		DIRTY_PLAYERS.removeAll(synced);
	}

	public static int maxPlayerLevel() {
		return Math.max(1, LevelsConfigManager.player().maxLevel());
	}

	public static int requiredXpForLevel(int level) {
		int normalized = Math.max(1, level);
		LevelsConfigManager.PlayerSettings player = LevelsConfigManager.player();
		double scaled = player.baseXpRequirement() * normalized
			* (1.0d + (player.baseXpMultiplier() * (normalized - 1)));
		if (!Double.isFinite(scaled)) return Integer.MAX_VALUE;
		return Math.max(1, Math.min(Integer.MAX_VALUE, (int) Math.round(scaled)));
	}

	private static void handlePlayerJoin(ServerPlayer player) {
		if (player == null) return;
		applyPersistedPlayerData(PlayerDataAPIManager.getSystemDataForPlayer(player, DATA_FILE_NAME, "players", "uuid"));
		state(player);
		LevelsAttributesManager.applyPlayerAttributes(player);
		LevelsFeatureAPIManager.restoreJoinHealth(player);
		markDirty(player.getUUID());
	}

	private static void handlePlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (newPlayer == null) return;
		LevelsPlayerState state = state(newPlayer);
		if (!alive) {
			state.currentXp = 0;
			state.requiredXp = requiredXpForLevel(state.level);
		}
		LevelsAttributesManager.applyPlayerAttributes(newPlayer);
		markDirty(newPlayer.getUUID());
	}

	private static void markDirty(UUID playerId) {
		if (playerId != null) DIRTY_PLAYERS.add(playerId);
	}

	private static void applyPersistedData(JsonObject data) {
		PLAYER_STATES.clear();
		if (data == null) return;
		JsonElement playersElement = data.get("players");
		if (playersElement == null || !playersElement.isJsonArray()) return;
		for (JsonElement element : playersElement.getAsJsonArray()) {
			if (element == null || !element.isJsonObject()) continue;
			applyPersistedPlayerState(element.getAsJsonObject());
		}
	}

	private static void applyPersistedPlayerData(JsonObject data) {
		JsonElement playersElement = data == null ? null : data.get("players");
		if (playersElement == null || !playersElement.isJsonArray()) return;
		for (JsonElement element : playersElement.getAsJsonArray()) {
			if (element != null && element.isJsonObject()) applyPersistedPlayerState(element.getAsJsonObject());
		}
	}

	private static void applyPersistedPlayerState(JsonObject playerData) {
		try {
			UUID playerId = UUID.fromString(readString(playerData, "uuid", ""));
			LevelsPlayerState state = LevelsPlayerState.defaults(requiredXpForLevel(1));
			state.level = Math.min(maxPlayerLevel(), Math.max(1, readInt(playerData, "level", 1)));
			state.currentXp = Math.max(0, readInt(playerData, "current-xp", 0));
			state.requiredXp = requiredXpForLevel(state.level);
			state.availablePoints = Math.max(0, readInt(playerData, "available-points", 1));
			JsonObject stats = object(playerData, "stats");
			for (LevelStat stat : LevelStat.values()) state.statLevels.put(stat, stat.clampLevel(readInt(stats, stat.id(), LevelStat.DEFAULT_LEVEL)));
			PLAYER_STATES.put(playerId, state);
		} catch (IllegalArgumentException ignored) { }
	}

	private static JsonObject toPersistedData() {
		JsonArray players = new JsonArray();
		for (Map.Entry<UUID, LevelsPlayerState> entry : PLAYER_STATES.entrySet()) {
			LevelsPlayerState state = entry.getValue();
			JsonObject stats = new JsonObject();
			for (LevelStat stat : LevelStat.values()) stats.addProperty(stat.id(), state.statLevel(stat));
			JsonObject player = new JsonObject();
			player.addProperty("uuid", entry.getKey().toString());
			player.addProperty("level", state.level);
			player.addProperty("current-xp", state.currentXp);
			player.addProperty("required-xp", requiredXpForLevel(state.level));
			player.addProperty("available-points", state.availablePoints);
			player.add("stats", stats);
			players.add(player);
		}
		JsonObject data = new JsonObject();
		data.add("players", players);
		return data;
	}

	private static JsonObject object(JsonObject source, String key) {
		JsonElement element = source == null ? null : source.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static int readInt(JsonObject source, String key, int fallback) {
		try { return source != null && source.has(key) ? source.get(key).getAsInt() : fallback; }
		catch (RuntimeException exception) { return fallback; }
	}

	private static String readString(JsonObject source, String key, String fallback) {
		try { return source != null && source.has(key) ? source.get(key).getAsString() : fallback; }
		catch (RuntimeException exception) { return fallback; }
	}

}

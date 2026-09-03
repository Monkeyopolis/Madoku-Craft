package madoku.craft.java.core.season;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.sync.SyncConfigAPIManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import madoku.craft.java.core.sync.SyncWorldAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;


/** Orchestrator and public entry point for the Madoku Season subsystem. */
public final class SeasonAPIManager {
	private static final long CLIMATE_CACHE_TTL_TICKS = 5L;
	private static final Season[] SEASONS = Season.values();
	private static boolean initialized;
	private static MinecraftServer currentServer;
	private static String lastBroadcastSeason = "";
	private static double lastBroadcastTemperatureOffset;
	private static double lastBroadcastHumidityOffset;
	private static String lastBroadcastWeatherCondition = "";
	private static int lastBroadcastSeasonDay = -1;
	private static int lastBroadcastSeasonLengthDays = -1;
	private static final Map<UUID, ClimateHudState> lastPlayerClimateStates = new HashMap<>();
	private static final Map<UUID, ClimateEvaluation> climateEvaluations = new HashMap<>();
	private static long cachedAbsoluteDay = Long.MIN_VALUE;
	private static int cachedSeasonLengthDays = -1;
	private static SeasonState cachedState;

	private SeasonAPIManager() { }

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		SeasonConfigManager.initialize();
		SeasonBiomeClimateAPIManager.initialize();
		SeasonEnvironmentTransitionAPIManager.initialize();
		SeasonWeatherAPIManager.initialize();
		SyncConfigAPIManager.register(
			"season",
			SeasonAPIManager::createClientSyncSnapshot,
			SeasonAPIManager::applyClientSyncSnapshot,
			SeasonAPIManager::resetClientSyncState
		);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			SeasonPayloadAPIManager payload = currentSyncPayload(server);
			if (payload != null) {
				SyncWorldAPIManager.send(handler.player, payload);
			}
			syncPlayerClimate(handler.player, true);
		});
	}

	public static void reset() {
		currentServer = null;
		SeasonEnvironmentTransitionAPIManager.reset();
		SeasonWeatherAPIManager.reset();
		SeasonBiomeClimateAPIManager.reset();
		lastBroadcastSeason = "";
		lastBroadcastTemperatureOffset = 0.0;
		lastBroadcastHumidityOffset = 0.0;
		lastBroadcastWeatherCondition = "";
		lastBroadcastSeasonDay = -1;
		lastBroadcastSeasonLengthDays = -1;
		lastPlayerClimateStates.clear();
		climateEvaluations.clear();
		cachedAbsoluteDay = Long.MIN_VALUE;
		cachedSeasonLengthDays = -1;
		cachedState = null;
	}

	public static boolean isEnabled() { return SeasonConfigManager.getSettings().enabled(); }

	public static String createClientSyncSnapshot() {
		return JSONFormatAPIManager.object()
			.put("season", SeasonConfigManager.toJson(SeasonConfigManager.getSettings()))
			.put("biome-climate", BiomeClimateConfigAPIManager.toJson(BiomeClimateConfigAPIManager.getSettings()))
			.put("environment-transition", EnvironmentTransitionConfigAPIManager.toJson(EnvironmentTransitionConfigAPIManager.getSettings()))
			.build()
			.toString();
	}

	public static void applyClientSyncSnapshot(String snapshot) {
		JsonElement parsed = JsonParser.parseString(snapshot == null ? "" : snapshot);
		if (!parsed.isJsonObject()) return;
		JsonObject root = parsed.getAsJsonObject();
		SeasonConfigManager.applyClientSynchronizedSettings(
			SeasonConfigManager.fromJson(root.getAsJsonObject("season"))
		);
		BiomeClimateConfigAPIManager.applyClientSynchronizedSettings(
			BiomeClimateConfigAPIManager.fromJson(root.getAsJsonObject("biome-climate"))
		);
		EnvironmentTransitionConfigAPIManager.applyClientSynchronizedSettings(
			EnvironmentTransitionConfigAPIManager.fromJson(root.getAsJsonObject("environment-transition"))
		);
		SeasonEnvironmentTransitionAPIManager.reset();
	}

	public static void resetClientSyncState() {
		SeasonConfigManager.resetClientSynchronizedSettings();
		BiomeClimateConfigAPIManager.resetClientSynchronizedSettings();
		EnvironmentTransitionConfigAPIManager.resetClientSynchronizedSettings();
		SeasonEnvironmentTransitionAPIManager.reset();
	}
	public static SeasonState getCurrentState() { return currentState(null); }
	public static SeasonState getCurrentState(ServerLevel level) { return currentState(level); }
	public static String getCurrentSeasonId() { return getCurrentState().season().id(); }
	public static String getCurrentSeasonId(ServerLevel level) { return getCurrentState(level).season().id(); }
	public static String getCurrentSeasonDisplayName() { return capitalize(getCurrentSeasonId()); }
	public static int getCurrentSeasonDay() { return getCurrentState().seasonDay(); }
	public static int getCurrentSeasonWeek() { return getCurrentState().week(); }

	public static SeasonBiomeClimateAPIManager.Climate resolveBiomeClimate(ServerLevel level, BlockPos pos) {
		return resolveBiomeClimate(level, pos, null);
	}

	public static SeasonBiomeClimateAPIManager.Climate resolveBiomeClimate(
		ServerLevel level,
		BlockPos pos,
		Biome biome
	) {
		SeasonBiomeClimateAPIManager.Climate climate = biome == null
			? SeasonBiomeClimateAPIManager.resolve(level, pos)
			: SeasonBiomeClimateAPIManager.resolve(biome);
		String season = getCurrentSeasonId(level);
		SeasonBiomeClimateAPIManager.Climate adjustedClimate = new SeasonBiomeClimateAPIManager.Climate(
			SeasonEnvironmentTransitionAPIManager.adjustTemperature(
				climate.temperature(),
				season,
				TimeAPIManager.getCurrentAbsoluteDayTime(level)),
			SeasonEnvironmentTransitionAPIManager.adjustHumidity(climate.humidity(), season));
		return SeasonEnvironmentTransitionAPIManager.adjustForShelter(level, pos, adjustedClimate);
	}

	public static Biome.Precipitation resolveSeasonalPrecipitation(Biome biome) {
		return SeasonEnvironmentTransitionAPIManager.resolvePrecipitation(biome, getCurrentSeasonId());
	}

	public static Biome.Precipitation resolveSeasonalPrecipitation(ServerLevel level, BlockPos pos) {
		return resolveSeasonalPrecipitation(level, pos, null);
	}

	public static Biome.Precipitation resolveSeasonalPrecipitation(ServerLevel level, BlockPos pos, Biome biome) {
		if (level == null || pos == null) return Biome.Precipitation.NONE;
		if (!SeasonEnvironmentTransitionAPIManager.isWeatherTransitionEnabled()) {
			Biome resolvedBiome = biome == null ? level.getBiome(pos).value() : biome;
			return SeasonEnvironmentTransitionAPIManager.resolvePrecipitation(resolvedBiome, getCurrentSeasonId(level));
		}
		return SeasonEnvironmentTransitionAPIManager.resolvePrecipitation(
			biome == null ? SeasonBiomeClimateAPIManager.resolve(level, pos) : SeasonBiomeClimateAPIManager.resolve(biome),
			getCurrentSeasonId(level),
			SeasonEnvironmentTransitionAPIManager.getTemperatureOffset(),
			SeasonEnvironmentTransitionAPIManager.getHumidityOffset(),
			TimeAPIManager.getCurrentAbsoluteDayTime(level));
	}

	public static boolean shouldSeasonFreezeAt(ServerLevel level, Biome biome, BlockPos pos) {
		return SeasonEnvironmentTransitionAPIManager.shouldFreezeAt(level, pos, resolveBiomeClimate(level, pos, biome));
	}

	public static boolean shouldSeasonMeltAt(ServerLevel level, BlockPos pos) {
		return SeasonEnvironmentTransitionAPIManager.shouldMeltAt(resolveBiomeClimate(level, pos));
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server == null || currentServer == server) return;
		currentServer = server;
		SeasonBiomeClimateAPIManager.onServerStarted(server);
		SeasonWeatherAPIManager.onServerStarted(server);
	}
	public static void onServerTick(MinecraftServer server) {
		if (server != null) {
			currentState(server.overworld());
		}
	}

	/** Sends each player's resolved local climate when its HUD-visible value changes. */
	public static void syncPlayerClimateIfChanged(MinecraftServer server) {
		if (server == null) return;
		lastPlayerClimateStates.keySet().removeIf(playerId -> {
			boolean disconnected = server.getPlayerList().getPlayer(playerId) == null;
			if (disconnected) climateEvaluations.remove(playerId);
			return disconnected;
		});
		climateEvaluations.keySet().removeIf(playerId -> server.getPlayerList().getPlayer(playerId) == null);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			syncPlayerClimate(player, false);
		}
	}

	/** Runs before Vanilla's level weather tick so the current Madoku state is authoritative for this frame. */
	public static void onServerStartTick(MinecraftServer server) {
		if (server != null) SeasonWeatherAPIManager.onServerTick(server);
	}

	public static void broadcastWorldSeasonNow(MinecraftServer server) {
		broadcastWorldSeason(server, true);
	}

	public static void broadcastWorldSeasonIfChanged(MinecraftServer server) {
		broadcastWorldSeason(server, false);
	}

	private static int broadcastWorldSeason(MinecraftServer server, boolean force) {
		if (server == null) {
			return 0;
		}
		if (!isEnabled()) {
			return broadcastSeasonCleared(server, force);
		}

		SeasonPayloadAPIManager payload = currentSyncPayload(server);
		if (payload == null) {
			return 0;
		}
		if (!force && payload.season().equals(lastBroadcastSeason)
			&& Double.compare(payload.temperatureOffset(), lastBroadcastTemperatureOffset) == 0
			&& Double.compare(payload.humidityOffset(), lastBroadcastHumidityOffset) == 0
			&& payload.weatherCondition().equals(lastBroadcastWeatherCondition)
			&& payload.seasonDay() == lastBroadcastSeasonDay
			&& payload.seasonLengthDays() == lastBroadcastSeasonLengthDays) {
			return 0;
		}

		int sent = SyncWorldAPIManager.broadcast(server, payload);
		lastBroadcastSeason = payload.season();
		lastBroadcastTemperatureOffset = payload.temperatureOffset();
		lastBroadcastHumidityOffset = payload.humidityOffset();
		lastBroadcastWeatherCondition = payload.weatherCondition();
		lastBroadcastSeasonDay = payload.seasonDay();
		lastBroadcastSeasonLengthDays = payload.seasonLengthDays();
		return sent;
	}

	private static int broadcastSeasonCleared(MinecraftServer server, boolean force) {
		if (!force && lastBroadcastSeason.isEmpty()) {
			return 0;
		}
		int sent = SyncWorldAPIManager.broadcast(server, new SeasonPayloadAPIManager("", 0.0, 0.0, "", 0, 1));
		lastBroadcastSeason = "";
		lastBroadcastTemperatureOffset = 0.0;
		lastBroadcastHumidityOffset = 0.0;
		lastBroadcastWeatherCondition = "";
		lastBroadcastSeasonDay = -1;
		lastBroadcastSeasonLengthDays = -1;
		return sent;
	}

	private static SeasonPayloadAPIManager currentSyncPayload(MinecraftServer server) {
		if (!isEnabled()) {
			return null;
		}
		ServerLevel world = server == null ? null : server.overworld();
		String season = getCurrentSeasonId(world);
		if (season == null || season.isBlank()) {
			return null;
		}
		SeasonWeatherAPIManager.WeatherCondition condition = SeasonWeatherAPIManager.getCurrentCondition();
		return new SeasonPayloadAPIManager(
			season,
			SeasonEnvironmentTransitionAPIManager.getTemperatureOffset(),
			SeasonEnvironmentTransitionAPIManager.getHumidityOffset(),
			condition == null ? "" : condition.id(),
			SeasonAPIManager.getCurrentSeasonDay(),
			SeasonConfigManager.getSettings().seasonLengthDays());
	}

	private static void syncPlayerClimate(ServerPlayer player, boolean force) {
		if (player == null) return;
		if (!(player.level() instanceof ServerLevel level)) return;
		BlockPos position = player.blockPosition();
		long gameTime = level.getGameTime();
		ClimateEvaluation evaluation = climateEvaluations.get(player.getUUID());
		boolean cacheValid = !force && evaluation != null
			&& evaluation.level() == level
			&& evaluation.position() == position.asLong()
			&& gameTime >= evaluation.gameTime()
			&& gameTime - evaluation.gameTime() < CLIMATE_CACHE_TTL_TICKS;
		if (!cacheValid) {
			SeasonBiomeClimateAPIManager.Climate climate = resolveBiomeClimate(level, position);
			if (climate == null || !Double.isFinite(climate.temperature()) || !Double.isFinite(climate.humidity())) {
				climate = new SeasonBiomeClimateAPIManager.Climate(50.0D, 50.0D);
			}
			evaluation = new ClimateEvaluation(
				level,
				position.asLong(),
				gameTime,
				climate.temperature(),
				climate.humidity(),
				new ClimateHudState(Math.round(climate.temperature()), Math.round(climate.humidity())));
			climateEvaluations.put(player.getUUID(), evaluation);
		}

		ClimateHudState state = evaluation.state();
		if (!force && state.equals(lastPlayerClimateStates.get(player.getUUID()))) return;

		if (SyncWorldAPIManager.send(player, new PlayerClimatePayloadAPIManager(evaluation.temperature(), evaluation.humidity()))) {
			lastPlayerClimateStates.put(player.getUUID(), state);
		}
	}




	private static SeasonState resolveState(ServerLevel level) {
		int seasonLength = SeasonConfigManager.getSettings().seasonLengthDays();
		long absoluteDay = Math.max(0L, TimeAPIManager.getDay(TimeAPIManager.getCurrentAbsoluteDayTime(level)));
		if (cachedState != null && cachedAbsoluteDay == absoluteDay && cachedSeasonLengthDays == seasonLength) {
			return cachedState;
		}
		long cycleDay = Math.floorMod(absoluteDay, seasonLength * 4L);
		Season season = SEASONS[(int) (cycleDay / seasonLength)];
		int day = (int) (cycleDay % seasonLength);
		cachedAbsoluteDay = absoluteDay;
		cachedSeasonLengthDays = seasonLength;
		return cachedState = new SeasonState(absoluteDay, cycleDay, season, day, day / SeasonConfigManager.DEFAULT_DAYS_PER_WEEK + 1, day % SeasonConfigManager.DEFAULT_DAYS_PER_WEEK + 1);
	}

	private static SeasonState currentState(ServerLevel level) {
		SeasonState state = resolveState(level);
		SeasonEnvironmentTransitionAPIManager.updateSeasonState(state);
		return state;
	}
	private static String capitalize(String value) { return value == null || value.isBlank() ? "Unknown" : Character.toUpperCase(value.charAt(0)) + value.substring(1); }

	public enum Season { SPRING("spring"), SUMMER("summer"), FALL("fall"), WINTER("winter"); private final String id; Season(String id) { this.id = id; } public String id() { return id; } }
	public record SeasonState(long absoluteDay, long cycleDay, Season season, int seasonDay, int week, int dayInWeek) { }
	private record ClimateHudState(long temperature, long humidity) { }
	private record ClimateEvaluation(
		ServerLevel level,
		long position,
		long gameTime,
		double temperature,
		double humidity,
		ClimateHudState state
	) { }
}




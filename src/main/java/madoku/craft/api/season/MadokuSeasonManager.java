package madoku.craft.api.season;

import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.metadata.MadokuMetaDataManager;
import madoku.craft.api.sync.SyncWorldManager;
import madoku.craft.api.time.MadokuTimeManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

import java.util.function.Consumer;

/** Orchestrator and public entry point for the Madoku Season subsystem. */
public final class MadokuSeasonManager {
	private static volatile SeasonState lastDebugState;
	private static String lastBroadcastSeason = "";
	private static double lastBroadcastTemperatureOffset;
	private static double lastBroadcastHumidityOffset;
	private static String lastBroadcastWeatherCondition = "";

	private MadokuSeasonManager() { }

	public static void initialize() {
		lastDebugState = null;
		MadokuMetaDataManager.registerMainSystem(MadokuMetaDataManager.SEASON);
		MadokuDebugManager.bootstrapMainSystem(MadokuMetaDataManager.SEASON);
		SeasonConfigManager.initialize();
		SeasonBiomeClimateManager.initialize();
		SeasonEnvironmentTransitionManager.initialize();
		SeasonWeatherManager.initialize();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			SeasonPayloadManager payload = currentSyncPayload(server);
			if (payload != null) {
				SyncWorldManager.send(handler.player, payload);
			}
		});
		emitDebug("initialize", builder -> builder
			.field("enabled", isEnabled())
			.field("season-length-days", SeasonConfigManager.getSettings().seasonLengthDays()));
	}

	public static void reset() {
		SeasonEnvironmentTransitionManager.reset();
		SeasonWeatherManager.reset();
		SeasonBiomeClimateManager.reset();
		lastDebugState = null;
		lastBroadcastSeason = "";
		lastBroadcastTemperatureOffset = 0.0;
		lastBroadcastHumidityOffset = 0.0;
		lastBroadcastWeatherCondition = "";
		emitDebug("reset", builder -> builder
			.field("enabled", isEnabled())
			.field("season-length-days", SeasonConfigManager.getSettings().seasonLengthDays()));
	}

	public static boolean isEnabled() { return SeasonConfigManager.getSettings().enabled(); }
	public static SeasonState getCurrentState() { return currentState(null); }
	public static SeasonState getCurrentState(ServerLevel level) { return currentState(level); }
	public static String getCurrentSeasonId() { return getCurrentState().season().id(); }
	public static String getCurrentSeasonId(ServerLevel level) { return getCurrentState(level).season().id(); }
	public static String getCurrentSeasonDisplayName() { return capitalize(getCurrentSeasonId()); }
	public static int getCurrentSeasonDay() { return getCurrentState().seasonDay(); }
	public static int getCurrentSeasonWeek() { return getCurrentState().week(); }

	public static SeasonBiomeClimateManager.Climate resolveBiomeClimate(ServerLevel level, BlockPos pos) {
		SeasonBiomeClimateManager.Climate climate = SeasonBiomeClimateManager.resolve(level, pos);
		String season = getCurrentSeasonId(level);
		return new SeasonBiomeClimateManager.Climate(
			SeasonEnvironmentTransitionManager.adjustTemperature(
				climate.temperature(),
				season,
				MadokuTimeManager.getCurrentAbsoluteDayTime(level)),
			SeasonEnvironmentTransitionManager.adjustHumidity(climate.humidity(), season));
	}

	public static Biome.Precipitation resolveSeasonalPrecipitation(Biome biome) {
		return SeasonEnvironmentTransitionManager.resolvePrecipitation(biome, getCurrentSeasonId());
	}

	public static Biome.Precipitation resolveSeasonalPrecipitation(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) return Biome.Precipitation.NONE;
		if (!SeasonEnvironmentTransitionManager.isWeatherTransitionEnabled()) {
			Biome biome = level.getBiome(pos).value();
			return SeasonEnvironmentTransitionManager.resolvePrecipitation(biome, getCurrentSeasonId(level));
		}
		return SeasonEnvironmentTransitionManager.resolvePrecipitation(
			SeasonBiomeClimateManager.resolve(level, pos),
			getCurrentSeasonId(level),
			SeasonEnvironmentTransitionManager.getTemperatureOffset(),
			SeasonEnvironmentTransitionManager.getHumidityOffset(),
			MadokuTimeManager.getCurrentAbsoluteDayTime(level));
	}

	public static boolean shouldSeasonFreezeAt(ServerLevel level, Biome biome, BlockPos pos) {
		return SeasonEnvironmentTransitionManager.shouldFreezeAt(level, pos, resolveBiomeClimate(level, pos));
	}

	public static boolean shouldSeasonMeltAt(ServerLevel level, BlockPos pos) {
		return SeasonEnvironmentTransitionManager.shouldMeltAt(resolveBiomeClimate(level, pos));
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server != null) {
			SeasonBiomeClimateManager.onServerStarted(server);
			SeasonWeatherManager.onServerStarted(server);
			emitDebug("server-started", builder -> builder
			.field("enabled", isEnabled())
			.field("season", getCurrentSeasonId(server.overworld()))
			.field("season-day", getCurrentSeasonDay())
			.field("week", getCurrentSeasonWeek()));
		}
	}
	public static void onServerTick(MinecraftServer server) {
		if (server != null) {
			currentState(server.overworld());
		}
	}

	/** Runs before Vanilla's level weather tick so the current Madoku state is authoritative for this frame. */
	public static void onServerStartTick(MinecraftServer server) {
		if (server != null) SeasonWeatherManager.onServerTick(server);
	}

	public static void broadcastWorldSeasonNow(MinecraftServer server) {
		int sent = broadcastWorldSeason(server, true);
		emitSyncDebug("broadcast-now", sent);
	}

	public static void broadcastWorldSeasonIfChanged(MinecraftServer server) {
		int sent = broadcastWorldSeason(server, false);
		if (sent > 0) {
			emitSyncDebug("broadcast-changed", sent);
		}
	}

	private static int broadcastWorldSeason(MinecraftServer server, boolean force) {
		if (server == null) {
			return 0;
		}
		if (!isEnabled()) {
			return broadcastSeasonCleared(server, force);
		}

		SeasonPayloadManager payload = currentSyncPayload(server);
		if (payload == null) {
			return 0;
		}
		if (!force && payload.season().equals(lastBroadcastSeason)
			&& Double.compare(payload.temperatureOffset(), lastBroadcastTemperatureOffset) == 0
			&& Double.compare(payload.humidityOffset(), lastBroadcastHumidityOffset) == 0
			&& payload.weatherCondition().equals(lastBroadcastWeatherCondition)) {
			return 0;
		}

		int sent = SyncWorldManager.broadcast(server, payload);
		lastBroadcastSeason = payload.season();
		lastBroadcastTemperatureOffset = payload.temperatureOffset();
		lastBroadcastHumidityOffset = payload.humidityOffset();
		lastBroadcastWeatherCondition = payload.weatherCondition();
		return sent;
	}

	private static int broadcastSeasonCleared(MinecraftServer server, boolean force) {
		if (!force && lastBroadcastSeason.isEmpty()) {
			return 0;
		}
		int sent = SyncWorldManager.broadcast(server, new SeasonPayloadManager("", 0.0, 0.0, ""));
		lastBroadcastSeason = "";
		lastBroadcastTemperatureOffset = 0.0;
		lastBroadcastHumidityOffset = 0.0;
		lastBroadcastWeatherCondition = "";
		return sent;
	}

	private static SeasonPayloadManager currentSyncPayload(MinecraftServer server) {
		if (!isEnabled()) {
			return null;
		}
		ServerLevel world = server == null ? null : server.overworld();
		String season = getCurrentSeasonId(world);
		if (season == null || season.isBlank()) {
			return null;
		}
		SeasonWeatherManager.WeatherCondition condition = SeasonWeatherManager.getCurrentCondition();
		return new SeasonPayloadManager(
			season,
			SeasonEnvironmentTransitionManager.getTemperatureOffset(),
			SeasonEnvironmentTransitionManager.getHumidityOffset(),
			condition == null ? "" : condition.id());
	}

	private static void emitSyncDebug(String subject, int playersSent) {
		if (!MadokuDebugManager.shouldEmit(MadokuMetaDataManager.SEASON.mainSystem(), "season-manager", "sync", subject)) {
			return;
		}
		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(
			"season.sync",
			MadokuMetaDataManager.SEASON.mainSystem(),
			"season-manager",
			"sync",
			subject
		).side(MadokuDebugManager.Side.SERVER)
			.tick(MadokuTimeManager.getGameplayTicks())
			.subject(subject)
			.field("season", lastBroadcastSeason)
			.field("players-sent", playersSent);
		builder.log();
	}

	private static void emitDebug(String subject, Consumer<MadokuDebugManager.EventBuilder> customizer) {
		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(
			"season.lifecycle",
			MadokuMetaDataManager.SEASON.mainSystem(),
			"season-manager",
			"lifecycle",
			"state"
		).side(MadokuDebugManager.Side.SERVER).tick(MadokuTimeManager.getGameplayTicks()).subject(subject);
		if (customizer != null) customizer.accept(builder);
		builder.log();
	}

	private static void emitStateDebug(SeasonState state) {
		SeasonState previous = lastDebugState;
		if (state == null || state.equals(previous)) {
			return;
		}
		lastDebugState = state;
		emitDebug("state-changed", builder -> builder
			.field("enabled", isEnabled())
			.field("absolute-day", state.absoluteDay())
			.field("cycle-day", state.cycleDay())
			.field("season", state.season().id())
			.field("season-day", state.seasonDay())
			.field("week", state.week())
			.field("day-in-week", state.dayInWeek())
			.field("season-length-days", SeasonConfigManager.getSettings().seasonLengthDays()));
	}

	private static SeasonState resolveState(ServerLevel level) {
		long absoluteDay = Math.max(0L, MadokuTimeManager.getDay(MadokuTimeManager.getCurrentAbsoluteDayTime(level)));
		int seasonLength = SeasonConfigManager.getSettings().seasonLengthDays();
		long cycleDay = Math.floorMod(absoluteDay, seasonLength * 4L);
		Season season = Season.values()[(int) (cycleDay / seasonLength)];
		int day = (int) (cycleDay % seasonLength);
		return new SeasonState(absoluteDay, cycleDay, season, day, day / SeasonConfigManager.DEFAULT_DAYS_PER_WEEK + 1, day % SeasonConfigManager.DEFAULT_DAYS_PER_WEEK + 1);
	}

	private static SeasonState currentState(ServerLevel level) {
		SeasonState state = resolveState(level);
		SeasonEnvironmentTransitionManager.updateSeasonState(state);
		emitStateDebug(state);
		return state;
	}
	private static String capitalize(String value) { return value == null || value.isBlank() ? "Unknown" : Character.toUpperCase(value.charAt(0)) + value.substring(1); }

	public enum Season { SPRING("spring"), SUMMER("summer"), FALL("fall"), WINTER("winter"); private final String id; Season(String id) { this.id = id; } public String id() { return id; } }
	public record SeasonState(long absoluteDay, long cycleDay, Season season, int seasonDay, int week, int dayInWeek) { }
}

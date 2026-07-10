package madoku.craft.api.season;

import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.metadata.MadokuMetaDataManager;
import madoku.craft.api.time.MadokuTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

/** Orchestrator and public entry point for the Madoku Season subsystem. */
public final class MadokuSeasonManager {
	private MadokuSeasonManager() { }

	public static void initialize() {
		MadokuMetaDataManager.registerMainSystem(MadokuMetaDataManager.SEASON);
		MadokuDebugManager.bootstrapMainSystem(MadokuMetaDataManager.SEASON);
		SeasonConfigManager.initialize();
		SeasonBiomeClimateManager.initialize();
		SeasonEnvironmentTransitionManager.initialize();
		emitDebug("initialize", builder -> builder
			.field("enabled", isEnabled())
			.field("season-length-days", SeasonConfigManager.getSettings().seasonLengthDays()));
	}

	public static void reset() {
		SeasonEnvironmentTransitionManager.reset();
		SeasonBiomeClimateManager.reset();
		emitDebug("reset", builder -> builder.field("enabled", isEnabled()));
	}

	public static boolean isEnabled() { return SeasonConfigManager.getSettings().enabled(); }
	public static SeasonState getCurrentState() { return resolveState(null); }
	public static SeasonState getCurrentState(ServerLevel level) { return resolveState(level); }
	public static String getCurrentSeasonId() { return getCurrentState().season().id(); }
	public static String getCurrentSeasonId(ServerLevel level) { return getCurrentState(level).season().id(); }
	public static String getCurrentSeasonDisplayName() { return capitalize(getCurrentSeasonId()); }
	public static int getCurrentSeasonDay() { return getCurrentState().seasonDay(); }
	public static int getCurrentSeasonWeek() { return getCurrentState().week(); }

	public static SeasonBiomeClimateManager.Climate resolveBiomeClimate(ServerLevel level, BlockPos pos) {
		SeasonBiomeClimateManager.Climate climate = SeasonBiomeClimateManager.resolve(level, pos);
		String season = getCurrentSeasonId(level);
		return new SeasonBiomeClimateManager.Climate(
			SeasonEnvironmentTransitionManager.adjustTemperature(climate.temperature(), season),
			SeasonEnvironmentTransitionManager.adjustHumidity(climate.humidity(), season));
	}

	public static Biome.Precipitation resolveSeasonalPrecipitation(Biome biome) {
		return SeasonEnvironmentTransitionManager.resolvePrecipitation(biome, getCurrentSeasonId());
	}

	public static boolean shouldSeasonFreezeAt(ServerLevel level, Biome biome, BlockPos pos) {
		return SeasonEnvironmentTransitionManager.shouldFreezeAt(level, pos, resolveBiomeClimate(level, pos));
	}

	public static boolean shouldSeasonMeltAt(ServerLevel level, BlockPos pos) {
		return SeasonEnvironmentTransitionManager.shouldMeltAt(resolveBiomeClimate(level, pos));
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server != null) emitDebug("server-started", builder -> builder
			.field("enabled", isEnabled()).field("season", getCurrentSeasonId(server.overworld())));
	}
	public static void onServerTick(MinecraftServer server) { }

	private static void emitDebug(String subject, java.util.function.Consumer<MadokuDebugManager.EventBuilder> customizer) {
		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(
			"season.lifecycle",
			MadokuMetaDataManager.SEASON.mainSystem(),
			"season-manager",
			"lifecycle",
			"state"
		).side(MadokuDebugManager.Side.SERVER).subject(subject);
		if (customizer != null) customizer.accept(builder);
		builder.log();
	}

	private static SeasonState resolveState(ServerLevel level) {
		long absoluteDay = Math.max(0L, MadokuTimeManager.getDay(MadokuTimeManager.getCurrentAbsoluteDayTime(level)));
		int seasonLength = SeasonConfigManager.getSettings().seasonLengthDays();
		long cycleDay = Math.floorMod(absoluteDay, seasonLength * 4L);
		Season season = Season.values()[(int) (cycleDay / seasonLength)];
		int day = (int) (cycleDay % seasonLength);
		return new SeasonState(absoluteDay, cycleDay, season, day, day / SeasonConfigManager.DEFAULT_DAYS_PER_WEEK + 1, day % SeasonConfigManager.DEFAULT_DAYS_PER_WEEK + 1);
	}
	private static String capitalize(String value) { return value == null || value.isBlank() ? "Unknown" : Character.toUpperCase(value.charAt(0)) + value.substring(1); }

	public enum Season { SPRING("spring"), SUMMER("summer"), FALL("fall"), WINTER("winter"); private final String id; Season(String id) { this.id = id; } public String id() { return id; } }
	public record SeasonState(long absoluteDay, long cycleDay, Season season, int seasonDay, int week, int dayInWeek) { }
}

package madoku.craft.api.season;

import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.metadata.MadokuMetaDataManager;
import madoku.craft.api.time.MadokuTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;

import java.util.function.Consumer;

/** Applies the current season's climate adjustment and vanilla environment overrides. */
public final class SeasonEnvironmentTransitionManager {
	private static final int COLD_TEMPERATURE_MAX = 30;
	private static final int HOT_TEMPERATURE_MIN = 70;
	private static final int PRECIPITATION_HUMIDITY_MIN = 31;
	private static final int HOT_BIOME_HUMIDITY_MIN = 70;
	private static final int MINUTES_PER_DAY = 24 * 60;
	private static final int[] DAILY_TEMPERATURE_MINUTES = {
		0, 2 * 60, 4 * 60, 6 * 60, 8 * 60, 10 * 60,
		12 * 60, 14 * 60, 16 * 60, 18 * 60, 20 * 60, 22 * 60
	};
	private static final double[] DAILY_TEMPERATURE_MODIFIERS = {
		-0.25D, -0.125D, 0.0D, 0.10D, 0.15D, 0.20D,
		0.25D, 0.125D, 0.0D, -0.10D, -0.15D, -0.20D
	};
	private static volatile double temperatureOffset;
	private static volatile double humidityOffset;

	private SeasonEnvironmentTransitionManager() { }

	public static void initialize() {
		EnvironmentTransitionConfigManager.initialize();
		debug("initialize");
	}

	public static void reset() {
		temperatureOffset = 0.0;
		humidityOffset = 0.0;
		debug("reset");
	}

	static void updateSeasonState(MadokuSeasonManager.SeasonState state) {
		if (state == null || state.season() == null) return;
		EnvironmentTransitionConfigManager.Settings settings = EnvironmentTransitionConfigManager.getSettings();
		int timeRateDays = Math.max(1, settings.timeRateDays());
		int seasonLengthDays = Math.max(1, SeasonConfigManager.getSettings().seasonLengthDays());
		int elapsedIntervals = Math.max(0, state.seasonDay() / timeRateDays);
		// The first transition is active on season day 0; subsequent transitions
		// begin at each configured time-rate boundary.
		int count = Math.min(settings.adjustmentCount(), elapsedIntervals + 1);
		double previousTemperatureOffset = temperatureOffset;
		double previousHumidityOffset = humidityOffset;
		temperatureOffset = settings.temperatureEnabled() && settings.seasonTransitionsEnabled()
			? resolveSmoothSeasonalOffset(
				settings.temperatureAdjustments(),
				state,
				count,
				settings.adjustmentCount(),
				timeRateDays,
				seasonLengthDays) : 0.0;
		humidityOffset = settings.humidityEnabled() && settings.seasonTransitionsEnabled()
			? resolveSmoothSeasonalOffset(
				settings.humidityAdjustments(),
				state,
				count,
				settings.adjustmentCount(),
				timeRateDays,
				seasonLengthDays) : 0.0;
		if (Double.compare(previousTemperatureOffset, temperatureOffset) != 0
			|| Double.compare(previousHumidityOffset, humidityOffset) != 0) {
			debug("offsets-changed", builder -> builder
				.field("season", state.season().id())
				.field("season-day", state.seasonDay())
				.field("elapsed-intervals", elapsedIntervals)
				.field("adjustment-count", count)
				.field("time-rate-days", timeRateDays)
				.field("temperature-offset", temperatureOffset)
				.field("humidity-offset", humidityOffset)
				.field("temperature-enabled", settings.temperatureEnabled())
				.field("humidity-enabled", settings.humidityEnabled())
				.field("season-transitions-enabled", settings.seasonTransitionsEnabled()));
		}
	}

	public static double adjustTemperature(double base, String season) {
		return adjustTemperature(base, season, MadokuTimeManager.getCurrentAbsoluteDayTime());
	}

	public static double adjustTemperature(double base, String season, long absoluteDayTime) {
		double seasonalTemperature = isTemperatureTransitionEnabled() ? base + temperatureOffset : base;
		return adjustTemperatureByTime(seasonalTemperature, absoluteDayTime);
	}

	public static double adjustTemperatureByTime(double seasonalTemperature, long absoluteDayTime) {
		return seasonalTemperature * (1.0D + resolveDailyTemperatureModifier(absoluteDayTime));
	}

	public static double adjustHumidity(double base, String season) {
		if (!isHumidityTransitionEnabled()) return base;
		return base + humidityOffset;
	}

	public static double getTemperatureOffset() {
		return temperatureOffset;
	}

	public static double getHumidityOffset() {
		return humidityOffset;
	}

	public static boolean isWeatherTransitionEnabled() {
		return MadokuSeasonManager.isEnabled() && SeasonBiomeClimateManager.isTemperatureEnabled() && SeasonBiomeClimateManager.isHumidityEnabled()
			&& EnvironmentTransitionConfigManager.getSettings().weatherEnabled();
	}

	public static boolean isWaterTransitionEnabled() {
		return MadokuSeasonManager.isEnabled() && SeasonBiomeClimateManager.isTemperatureEnabled()
			&& EnvironmentTransitionConfigManager.getSettings().waterEnabled();
	}

	public static boolean isTemperatureTransitionEnabled() {
		return MadokuSeasonManager.isEnabled() && SeasonBiomeClimateManager.isTemperatureEnabled()
			&& EnvironmentTransitionConfigManager.getSettings().seasonTransitionsEnabled()
			&& EnvironmentTransitionConfigManager.getSettings().temperatureEnabled();
	}

	public static boolean isHumidityTransitionEnabled() {
		return MadokuSeasonManager.isEnabled() && SeasonBiomeClimateManager.isHumidityEnabled()
			&& EnvironmentTransitionConfigManager.getSettings().seasonTransitionsEnabled()
			&& EnvironmentTransitionConfigManager.getSettings().humidityEnabled();
	}

	public static Biome.Precipitation resolvePrecipitation(Biome biome, String season) {
		if (!isWeatherTransitionEnabled() || biome == null) return vanillaPrecipitation(biome);
		return resolvePrecipitation(SeasonBiomeClimateManager.resolve(biome), season);
	}

	public static Biome.Precipitation resolvePrecipitation(SeasonBiomeClimateManager.Climate climate, String season) {
		return resolvePrecipitation(
			climate,
			season,
			temperatureOffset,
			humidityOffset,
			MadokuTimeManager.getCurrentAbsoluteDayTime());
	}

	public static Biome.Precipitation resolvePrecipitation(
		SeasonBiomeClimateManager.Climate climate,
		String season,
		double temperatureOffset,
		double humidityOffset
	) {
		return resolvePrecipitation(
			climate,
			season,
			temperatureOffset,
			humidityOffset,
			MadokuTimeManager.getCurrentAbsoluteDayTime());
	}

	public static Biome.Precipitation resolvePrecipitation(
		SeasonBiomeClimateManager.Climate climate,
		String season,
		double temperatureOffset,
		double humidityOffset,
		long absoluteDayTime
	) {
		if (!isWeatherTransitionEnabled() || climate == null) return Biome.Precipitation.NONE;
		double seasonalTemperature = isTemperatureTransitionEnabled()
			? climate.temperature() + temperatureOffset
			: climate.temperature();
		double temperature = adjustTemperatureByTime(seasonalTemperature, absoluteDayTime);
		double humidity = isHumidityTransitionEnabled() ? climate.humidity() + humidityOffset : climate.humidity();
		if (humidity < PRECIPITATION_HUMIDITY_MIN) return Biome.Precipitation.NONE;
		if (temperature >= HOT_TEMPERATURE_MIN && humidity < HOT_BIOME_HUMIDITY_MIN) return Biome.Precipitation.NONE;
		return temperature <= COLD_TEMPERATURE_MAX ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
	}

	private static double resolveDailyTemperatureModifier(long absoluteDayTime) {
		int minutes = MadokuTimeManager.getTotalMinutes(absoluteDayTime);
		for (int index = 0; index < DAILY_TEMPERATURE_MINUTES.length - 1; index++) {
			int startMinute = DAILY_TEMPERATURE_MINUTES[index];
			int endMinute = DAILY_TEMPERATURE_MINUTES[index + 1];
			if (minutes <= endMinute) {
				return interpolateModifier(
					DAILY_TEMPERATURE_MODIFIERS[index],
					DAILY_TEMPERATURE_MODIFIERS[index + 1],
					(minutes - startMinute) / (double) (endMinute - startMinute));
			}
		}

		int startMinute = DAILY_TEMPERATURE_MINUTES[DAILY_TEMPERATURE_MINUTES.length - 1];
		return interpolateModifier(
			DAILY_TEMPERATURE_MODIFIERS[DAILY_TEMPERATURE_MODIFIERS.length - 1],
			DAILY_TEMPERATURE_MODIFIERS[0],
			(minutes - startMinute) / (double) (MINUTES_PER_DAY - startMinute));
	}

	private static double interpolateModifier(double start, double end, double progress) {
		double clampedProgress = Math.max(0.0D, Math.min(1.0D, progress));
		double smoothProgress = clampedProgress * clampedProgress * (3.0D - 2.0D * clampedProgress);
		return start + (end - start) * smoothProgress;
	}

	public static boolean shouldFreezeAt(LevelReader level, BlockPos pos, SeasonBiomeClimateManager.Climate climate) {
		if (!isWaterTransitionEnabled() || level == null || pos == null || climate == null) return false;
		if (climate.temperature() > COLD_TEMPERATURE_MAX) return false;
		var state = level.getBlockState(pos);
		return state != null && state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource()
			&& (!state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
				|| !state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED));
	}

	public static boolean shouldMeltAt(SeasonBiomeClimateManager.Climate climate) {
		return isWaterTransitionEnabled() && climate != null && climate.temperature() > COLD_TEMPERATURE_MAX;
	}

	private static double resolveSmoothSeasonalOffset(
		java.util.Map<String, EnvironmentTransitionConfigManager.Adjustment> adjustments,
		MadokuSeasonManager.SeasonState state,
		int count,
		int adjustmentCount,
		int timeRateDays,
		int seasonLengthDays
	) {
		if (adjustmentCount <= 0) return 0.0D;

		double current = resolveCumulativeOffset(adjustments, state.season(), count, adjustmentCount);
		int availableStages = Math.min(
			adjustmentCount,
			Math.max(1, ((seasonLengthDays - 1) / timeRateDays) + 1));
		int currentStageStartDay = Math.min((availableStages - 1) * timeRateDays, seasonLengthDays - 1);
		if (count >= availableStages && state.seasonDay() >= currentStageStartDay) {
			MadokuSeasonManager.Season nextSeason = nextSeason(state.season());
			double next = resolveCumulativeOffset(adjustments, nextSeason, 1, adjustmentCount);
			double progress = (state.seasonDay() - currentStageStartDay)
				/ (double) Math.max(1, seasonLengthDays - currentStageStartDay - 1);
			return interpolateModifier(current, next, progress);
		}

		int nextCount = Math.min(count + 1, availableStages);
		double next = resolveCumulativeOffset(adjustments, state.season(), nextCount, adjustmentCount);
		int currentStageStart = Math.max(0, (count - 1) * timeRateDays);
		double progress = (state.seasonDay() - currentStageStart) / (double) Math.max(1, timeRateDays - 1);
		return interpolateModifier(current, next, progress);
	}

	private static MadokuSeasonManager.Season nextSeason(MadokuSeasonManager.Season season) {
		MadokuSeasonManager.Season[] seasons = MadokuSeasonManager.Season.values();
		return seasons[(season.ordinal() + 1) % seasons.length];
	}

	private static Biome.Precipitation vanillaPrecipitation(Biome biome) {
		if (biome == null || !biome.hasPrecipitation()) return Biome.Precipitation.NONE;
		return biome.getBaseTemperature() <= 0.15f ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
	}

	private static double resolveCumulativeOffset(
		java.util.Map<String, EnvironmentTransitionConfigManager.Adjustment> adjustments,
		MadokuSeasonManager.Season season,
		int currentCount,
		int adjustmentCount
	) {
		// Spring begins at the value left by the previous Winter. The base climate is
		// the midpoint of the warm and cold seasonal range for the default balanced cycle.
		int fullCount = Math.max(0, adjustmentCount);
		double offset = -0.5 * (signedValue(adjustments, "spring") + signedValue(adjustments, "summer")) * fullCount;
		MadokuSeasonManager.Season[] seasons = MadokuSeasonManager.Season.values();
		for (int index = 0; index < season.ordinal(); index++) {
			offset += signedValue(adjustments, seasons[index].id()) * fullCount;
		}
		return offset + signedValue(adjustments, season.id()) * Math.max(0, currentCount);
	}

	private static double signedValue(
		java.util.Map<String, EnvironmentTransitionConfigManager.Adjustment> adjustments,
		String season
	) {
		EnvironmentTransitionConfigManager.Adjustment adjustment = adjustments.get(season);
		if (adjustment == null) return 0.0;
		return adjustment.type().equals("subtraction") ? -adjustment.value() : adjustment.value();
	}
	private static void debug(String subject) {
		debug(subject, builder -> { });
	}

	private static void debug(String subject, Consumer<MadokuDebugManager.EventBuilder> customizer) {
		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(
			"season.environment-transition.lifecycle",
			MadokuMetaDataManager.SEASON.mainSystem(),
			"season-environment-transition-manager",
			"lifecycle",
			"state"
		).side(MadokuDebugManager.Side.SERVER).tick(madoku.craft.api.time.MadokuTimeManager.getGameplayTicks()).subject(subject);
		if (customizer != null) customizer.accept(builder);
		builder.log();
	}
}

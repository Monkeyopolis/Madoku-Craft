package madoku.craft.api.season;

import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.metadata.MadokuMetaDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;

/** Applies the current season's climate adjustment and vanilla environment overrides. */
public final class SeasonEnvironmentTransitionManager {
	private SeasonEnvironmentTransitionManager() { }

	public static void initialize() {
		EnvironmentTransitionConfigManager.initialize();
		debug("initialize");
	}

	public static void reset() {
		debug("reset");
	}

	public static int adjustTemperature(int base, String season) {
		if (!isTemperatureTransitionEnabled()) return clamp(base);
		return apply(base, EnvironmentTransitionConfigManager.getSettings().temperatureAdjustments().getOrDefault(normalizeSeason(season), new EnvironmentTransitionConfigManager.Adjustment("addition", 0)));
	}

	public static int adjustHumidity(int base, String season) {
		if (!isHumidityTransitionEnabled()) return clamp(base);
		return apply(base, EnvironmentTransitionConfigManager.getSettings().humidityAdjustments().getOrDefault(normalizeSeason(season), new EnvironmentTransitionConfigManager.Adjustment("addition", 0)));
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
		SeasonBiomeClimateManager.Climate climate = SeasonBiomeClimateManager.resolve(biome);
		int temperature = adjustTemperature(climate.temperature(), season);
		int humidity = adjustHumidity(climate.humidity(), season);
		if (humidity < 40) return Biome.Precipitation.NONE;
		return temperature <= 30 ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
	}

	public static boolean shouldFreezeAt(LevelReader level, BlockPos pos, SeasonBiomeClimateManager.Climate climate) {
		if (!isWaterTransitionEnabled() || level == null || pos == null || climate == null) return false;
		if (climate.temperature() > 30) return false;
		var state = level.getBlockState(pos);
		return state != null && state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource()
			&& (!state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
				|| !state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED));
	}

	public static boolean shouldMeltAt(SeasonBiomeClimateManager.Climate climate) {
		return isWaterTransitionEnabled() && climate != null && climate.temperature() >= 30;
	}

	private static Biome.Precipitation vanillaPrecipitation(Biome biome) {
		if (biome == null || !biome.hasPrecipitation()) return Biome.Precipitation.NONE;
		return biome.getBaseTemperature() <= 0.15f ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
	}

	private static int apply(int base, EnvironmentTransitionConfigManager.Adjustment adjustment) {
		int value = adjustment.type().equals("subtraction") ? base - adjustment.value() : base + adjustment.value();
		return clamp(value);
	}
	private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
	private static String normalizeSeason(String season) { return season == null ? "spring" : season.toLowerCase(java.util.Locale.ROOT); }
	private static void debug(String subject) {
		MadokuDebugManager.event("season.environment-transition.lifecycle", MadokuMetaDataManager.SEASON.mainSystem(), "season-environment-transition-manager", "lifecycle", "state").subject(subject).log();
	}
}

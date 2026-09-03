package madoku.craft.java.season;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import madoku.craft.java.core.season.BiomeClimateConfigAPIManager;
import madoku.craft.java.core.season.SeasonBiomeClimateAPIManager;
import madoku.craft.java.core.season.SeasonEnvironmentTransitionAPIManager;

public final class ClientSeasonalPrecipitationState {
	private static volatile String season = "";
	private static volatile int seasonDay;
	private static volatile int seasonLengthDays = 28;
	private static volatile double temperatureOffset;
	private static volatile double humidityOffset;
	private static volatile String weatherCondition = "";
	private static volatile ClientLevel level;
	private static volatile Map<Biome, SeasonBiomeClimateAPIManager.Climate> climates = Map.of();

	private ClientSeasonalPrecipitationState() {
	}

	public static void update(String season, double temperatureOffset, double humidityOffset, String weatherCondition, int seasonDay, int seasonLengthDays) {
		ClientSeasonalPrecipitationState.season = season == null ? "" : season;
		ClientSeasonalPrecipitationState.seasonDay = Math.max(0, seasonDay);
		ClientSeasonalPrecipitationState.seasonLengthDays = Math.max(1, seasonLengthDays);
		ClientSeasonalPrecipitationState.temperatureOffset = temperatureOffset;
		ClientSeasonalPrecipitationState.humidityOffset = humidityOffset;
		ClientSeasonalPrecipitationState.weatherCondition = weatherCondition == null ? "" : weatherCondition;
	}

	public static String getSeason() {
		return season;
	}

	public static double getSeasonProgress() {
		return Math.max(0.0D, Math.min(1.0D, seasonDay / (double) Math.max(1, seasonLengthDays - 1)));
	}

	public static int getSeasonDay() {
		return seasonDay;
	}

	public static int getSeasonLengthDays() {
		return seasonLengthDays;
	}

	public static double getSeasonDayValue() {
		return seasonDay;
	}

	public static void refresh(ClientLevel clientLevel) {
		if (clientLevel == null || clientLevel == level) {
			return;
		}
		Registry<Biome> registry = clientLevel.registryAccess().lookupOrThrow(Registries.BIOME);
		IdentityHashMap<Biome, SeasonBiomeClimateAPIManager.Climate> configured = new IdentityHashMap<>();
		for (Map.Entry<ResourceKey<Biome>, Biome> entry : registry.entrySet()) {
			BiomeClimateConfigAPIManager.Climate climate = BiomeClimateConfigAPIManager.getBiomeClimate(entry.getKey().identifier().toString());
			if (climate != null) {
				configured.put(entry.getValue(), new SeasonBiomeClimateAPIManager.Climate(climate.temperature(), climate.humidity()));
			}
		}
		climates = Collections.unmodifiableMap(configured);
		level = clientLevel;
	}

	public static Biome.Precipitation resolve(Biome biome) {
		if (biome == null || season.isBlank()) {
			return Biome.Precipitation.NONE;
		}
		if (!SeasonEnvironmentTransitionAPIManager.isWeatherTransitionEnabled()) {
			return SeasonEnvironmentTransitionAPIManager.resolvePrecipitation(biome, season);
		}
		SeasonBiomeClimateAPIManager.Climate climate = climates.get(biome);
		if (climate == null) {
			climate = SeasonBiomeClimateAPIManager.nativeClimate(biome);
		}
		return SeasonEnvironmentTransitionAPIManager.resolvePrecipitation(
			climate,
			season,
			temperatureOffset,
			humidityOffset,
			currentAbsoluteDayTime());
	}

	public static SeasonBiomeClimateAPIManager.Climate resolveClimate(Biome biome) {
		if (biome == null) {
			return new SeasonBiomeClimateAPIManager.Climate(0.0D, 0.0D);
		}
		SeasonBiomeClimateAPIManager.Climate climate = climates.get(biome);
		if (climate == null) {
			climate = SeasonBiomeClimateAPIManager.nativeClimate(biome);
		}
		return new SeasonBiomeClimateAPIManager.Climate(
			SeasonEnvironmentTransitionAPIManager.adjustTemperatureByTime(
				climate.temperature() + temperatureOffset,
				currentAbsoluteDayTime()),
			climate.humidity() + humidityOffset);
	}

	public static double resolveSeasonalTemperature(Biome biome) {
		if (biome == null) {
			return 0.0D;
		}
		SeasonBiomeClimateAPIManager.Climate climate = climates.get(biome);
		if (climate == null) {
			climate = SeasonBiomeClimateAPIManager.nativeClimate(biome);
		}
		return climate.temperature() + temperatureOffset;
	}

	private static long currentAbsoluteDayTime() {
		ClientLevel clientLevel = level;
		return clientLevel == null ? 0L : clientLevel.getOverworldClockTime();
	}

	public static boolean isSynchronized() {
		return !season.isBlank();
	}

	public static boolean isPrecipitating() {
		ClientLevel clientLevel = level;
		if (clientLevel == null) return false;
		if (!isWeatherAuthoritative()) return clientLevel.isRaining();
		return isPrecipitatingCondition();
	}

	public static float resolveRainLevel(float vanillaLevel) {
		if (!isWeatherAuthoritative()) return vanillaLevel;
		return isPrecipitatingCondition() ? 1.0F : 0.0F;
	}

	public static float resolveThunderLevel(float vanillaLevel) {
		if (!isWeatherAuthoritative()) return vanillaLevel;
		return "thunderstorm-weather".equals(weatherCondition) ? 1.0F : 0.0F;
	}

	private static boolean isWeatherAuthoritative() {
		return !season.isBlank() && !weatherCondition.isBlank();
	}

	private static boolean isPrecipitatingCondition() {
		return "rain-weather".equals(weatherCondition) || "thunderstorm-weather".equals(weatherCondition);
	}

	public static void clear() {
		season = "";
		seasonDay = 0;
		seasonLengthDays = 28;
		temperatureOffset = 0.0;
		humidityOffset = 0.0;
		weatherCondition = "";
		level = null;
		climates = Map.of();
	}
}


package madoku.craft.season;

import madoku.craft.api.season.BiomeClimateConfigManager;
import madoku.craft.api.season.SeasonBiomeClimateManager;
import madoku.craft.api.season.SeasonEnvironmentTransitionManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ClientSeasonalPrecipitationState {
	private static volatile String season = "";
	private static volatile double temperatureOffset;
	private static volatile double humidityOffset;
	private static volatile String weatherCondition = "";
	private static volatile ClientLevel level;
	private static volatile Map<Biome, SeasonBiomeClimateManager.Climate> climates = Map.of();

	private ClientSeasonalPrecipitationState() {
	}

	public static void update(String season, double temperatureOffset, double humidityOffset, String weatherCondition) {
		ClientSeasonalPrecipitationState.season = season == null ? "" : season;
		ClientSeasonalPrecipitationState.temperatureOffset = temperatureOffset;
		ClientSeasonalPrecipitationState.humidityOffset = humidityOffset;
		ClientSeasonalPrecipitationState.weatherCondition = weatherCondition == null ? "" : weatherCondition;
	}

	public static void refresh(ClientLevel clientLevel) {
		if (clientLevel == null || clientLevel == level) {
			return;
		}
		Registry<Biome> registry = clientLevel.registryAccess().lookupOrThrow(Registries.BIOME);
		IdentityHashMap<Biome, SeasonBiomeClimateManager.Climate> configured = new IdentityHashMap<>();
		for (Map.Entry<ResourceKey<Biome>, Biome> entry : registry.entrySet()) {
			BiomeClimateConfigManager.Climate climate = BiomeClimateConfigManager.getBiomeClimate(entry.getKey().identifier().toString());
			if (climate != null) {
				configured.put(entry.getValue(), new SeasonBiomeClimateManager.Climate(climate.temperature(), climate.humidity()));
			}
		}
		climates = Collections.unmodifiableMap(configured);
		level = clientLevel;
	}

	public static Biome.Precipitation resolve(Biome biome) {
		if (biome == null || season.isBlank()) {
			return Biome.Precipitation.NONE;
		}
		if (!SeasonEnvironmentTransitionManager.isWeatherTransitionEnabled()) {
			return SeasonEnvironmentTransitionManager.resolvePrecipitation(biome, season);
		}
		SeasonBiomeClimateManager.Climate climate = climates.get(biome);
		if (climate == null) {
			climate = SeasonBiomeClimateManager.nativeClimate(biome);
		}
		return SeasonEnvironmentTransitionManager.resolvePrecipitation(
			climate,
			season,
			temperatureOffset,
			humidityOffset,
			currentAbsoluteDayTime());
	}

	public static SeasonBiomeClimateManager.Climate resolveClimate(Biome biome) {
		if (biome == null) {
			return new SeasonBiomeClimateManager.Climate(0.0D, 0.0D);
		}
		SeasonBiomeClimateManager.Climate climate = climates.get(biome);
		if (climate == null) {
			climate = SeasonBiomeClimateManager.nativeClimate(biome);
		}
		return new SeasonBiomeClimateManager.Climate(
			SeasonEnvironmentTransitionManager.adjustTemperatureByTime(
				climate.temperature() + temperatureOffset,
				currentAbsoluteDayTime()),
			climate.humidity() + humidityOffset);
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
		temperatureOffset = 0.0;
		humidityOffset = 0.0;
		weatherCondition = "";
		level = null;
		climates = Map.of();
	}
}

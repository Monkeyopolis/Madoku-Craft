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
	private static volatile ClientLevel level;
	private static volatile Map<Biome, SeasonBiomeClimateManager.Climate> climates = Map.of();

	private ClientSeasonalPrecipitationState() {
	}

	public static void update(String season, double temperatureOffset, double humidityOffset) {
		ClientSeasonalPrecipitationState.season = season == null ? "" : season;
		ClientSeasonalPrecipitationState.temperatureOffset = temperatureOffset;
		ClientSeasonalPrecipitationState.humidityOffset = humidityOffset;
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
		SeasonBiomeClimateManager.Climate climate = climates.get(biome);
		if (climate == null) {
			climate = SeasonBiomeClimateManager.nativeClimate(biome);
		}
		return SeasonEnvironmentTransitionManager.resolvePrecipitation(
			climate,
			season,
			temperatureOffset,
			humidityOffset);
	}

	public static boolean isSynchronized() {
		return !season.isBlank();
	}

	public static void clear() {
		season = "";
		temperatureOffset = 0.0;
		humidityOffset = 0.0;
		level = null;
		climates = Map.of();
	}
}

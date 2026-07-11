package madoku.craft.api.season;

import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.metadata.MadokuMetaDataManager;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

/** Runtime biome climate resolver. Temperature and humidity may be below 0 or above 100. */
public final class SeasonBiomeClimateManager {
	public record Climate(double temperature, double humidity) { }

	private SeasonBiomeClimateManager() { }

	public static void initialize() {
		MadokuMetaDataManager.registerMainSystem(MadokuMetaDataManager.SEASON);
		BiomeClimateConfigManager.initialize();
		debug("initialize", BiomeClimateConfigManager.getSettings().biomes().size());
	}

	public static void reset() {
		debug("reset", 0);
	}

	public static boolean isTemperatureEnabled() {
		return BiomeClimateConfigManager.getSettings().temperatureEnabled();
	}

	public static boolean isHumidityEnabled() {
		return BiomeClimateConfigManager.getSettings().humidityEnabled();
	}

	public static Climate resolve(ServerLevel level, net.minecraft.core.BlockPos pos) {
		if (level == null || pos == null) return new Climate(50, 50);
		Biome biome = level.getBiome(pos).value();
		if (!MadokuSeasonManager.isEnabled()) return nativeClimate(biome);
		String id = resolveBiomeId(level, pos);
		BiomeClimateConfigManager.Climate configured = BiomeClimateConfigManager.getBiomeClimate(id);
		if (configured != null) {
			double temperature = isTemperatureEnabled() ? configured.temperature() : nativeClimate(biome).temperature();
			double humidity = isHumidityEnabled() ? configured.humidity() : nativeClimate(biome).humidity();
			return new Climate(temperature, humidity);
		}
		return nativeClimate(biome);
	}

	public static Climate resolve(Biome biome) {
		BiomeClimateConfigManager.Climate configured = BiomeClimateConfigManager.getBiomeClimate("");
		return configured == null ? nativeClimate(biome) : new Climate(configured.temperature(), configured.humidity());
	}

	public static Climate nativeClimate(Biome biome) {
		if (biome == null) return new Climate(50, 50);
		int temperature = Math.round((biome.getBaseTemperature() + 0.5f) * 40.0f);
		int humidity = biome.hasPrecipitation() ? 70 : 0;
		return new Climate(temperature, humidity);
	}

	public static String resolveBiomeId(ServerLevel level, net.minecraft.core.BlockPos pos) {
		try {
			Holder<Biome> holder = level.getBiome(pos);
			return holder.unwrapKey().map(ResourceKey::identifier).map(Identifier::toString).orElseGet(() -> {
				Registry<Biome> registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
				Identifier id = registry.getKey(holder.value());
				return id == null ? "" : id.toString();
			});
		} catch (RuntimeException ignored) {
			return "";
		}
	}

	private static void debug(String subject, int biomeCount) {
		MadokuDebugManager.event("season.biome-climate.lifecycle", MadokuMetaDataManager.SEASON.mainSystem(), "season-biome-climate-manager", "lifecycle", "state")
			.subject(subject).field("biomes", biomeCount).log();
	}
}

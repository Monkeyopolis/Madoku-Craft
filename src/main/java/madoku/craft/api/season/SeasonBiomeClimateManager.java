package madoku.craft.api.season;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/** Runtime biome climate resolver. Temperature and humidity may be below 0 or above 100. */
public final class SeasonBiomeClimateManager {
	public record Climate(double temperature, double humidity) { }
	private static volatile Map<Biome, BiomeClimateConfigManager.Climate> runtimeConfiguredClimates = Map.of();
	private static volatile Map<Biome, Climate> runtimeNativeClimates = Map.of();
	private static volatile MinecraftServer currentServer;

	private SeasonBiomeClimateManager() { }

	public static void initialize() {
		BiomeClimateConfigManager.initialize();
	}

	public static void reset() {
		currentServer = null;
		runtimeConfiguredClimates = Map.of();
		runtimeNativeClimates = Map.of();
	}

	static void onServerStarted(MinecraftServer server) {
		currentServer = server;
		rebuildConfiguredClimates(server);
	}

	static void onClimateConfigReloaded() {
		MinecraftServer server = currentServer;
		if (server == null) {
			runtimeConfiguredClimates = Map.of();
			return;
		}
		rebuildConfiguredClimates(server);
	}

	private static void rebuildConfiguredClimates(MinecraftServer server) {
		if (server == null || server.overworld() == null) {
			runtimeConfiguredClimates = Map.of();
			return;
		}
		Registry<Biome> registry = server.overworld().registryAccess().lookupOrThrow(Registries.BIOME);
		IdentityHashMap<Biome, BiomeClimateConfigManager.Climate> configured = new IdentityHashMap<>();
		IdentityHashMap<Biome, Climate> nativeClimates = new IdentityHashMap<>();
		for (Map.Entry<ResourceKey<Biome>, Biome> entry : registry.entrySet()) {
			Identifier biomeId = entry.getKey().identifier();
			Biome biome = entry.getValue();
			nativeClimates.put(biome, resolveNativeClimate(biome));
			BiomeClimateConfigManager.Climate climate = BiomeClimateConfigManager.getBiomeClimate(biomeId.toString());
			if (climate != null) {
				configured.put(biome, climate);
			}
		}
		runtimeConfiguredClimates = Collections.unmodifiableMap(configured);
		runtimeNativeClimates = Collections.unmodifiableMap(nativeClimates);
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
		return resolve(biome);
	}

	public static Climate resolve(Biome biome) {
		Climate nativeClimate = nativeClimate(biome);
		BiomeClimateConfigManager.Climate configured = runtimeConfiguredClimates.get(biome);
		if (configured == null) {
			return nativeClimate;
		}
		return new Climate(
			isTemperatureEnabled() ? configured.temperature() : nativeClimate.temperature(),
			isHumidityEnabled() ? configured.humidity() : nativeClimate.humidity());
	}

	public static Climate nativeClimate(Biome biome) {
		if (biome == null) return new Climate(50, 50);
		Climate cached = runtimeNativeClimates.get(biome);
		return cached == null ? resolveNativeClimate(biome) : cached;
	}

	private static Climate resolveNativeClimate(Biome biome) {
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

}

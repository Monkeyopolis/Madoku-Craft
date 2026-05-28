package madoku.craft.season;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.data.DataManagerSystem;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.time.MadokuTime;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MadokuSeason {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuSeason.class);

	private static final String SEASON_CONFIG_FOLDER_NAME = "madoku-craft-season";
	private static final String SEASON_CONFIG_FILE_NAME = "madoku-season";
	private static final String DATA_FOLDER_NAME = "madoku-craft-season";
	private static final String DATA_FILE_NAME = "madoku-season";
	private static final String BIOME_FOLDER_NAME = "biomes";
	private static final long SEASON_YEAR_DAYS = MadokuSeasonConfig.DEFAULT_SEASON_LENGTH_DAYS * 4L;
	private static final int TEMPERATE_TRANSITION_START_DAY = MadokuSeasonConfig.DEFAULT_DAYS_PER_WEEK * 2;

	private static final Map<String, BiomeClimateRecord> BIOME_CLIMATE_CACHE = new ConcurrentHashMap<>();

	private static volatile Settings settings = Settings.defaults();
	private static volatile SeasonState lastProcessedState = SeasonState.empty();
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;

	private MadokuSeason() {
	}

	public static void initialize() {
		loadStaticConfig();
	}

	public static void reset() {
		BIOME_CLIMATE_CACHE.clear();
		lastProcessedState = SeasonState.empty();
		lastAutosaveBucket = Long.MIN_VALUE;
	}

	public static void onServerStarted(MinecraftServer server) {
		ServerLevel world = resolveSeasonWorld(server);
		lastProcessedState = resolveCurrentState(world);
	}

	public static void onServerTick(MinecraftServer server) {
		ServerLevel world = resolveSeasonWorld(server);
		if (world == null) {
			return;
		}
		refreshSeasonState(world);
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		loadStaticConfig();
		JsonObject data = DataManagerSystem.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		SeasonState persistedState = parsePersistedState(data);
		if (persistedState != null) {
			lastProcessedState = persistedState;
		} else {
			lastProcessedState = resolveCurrentState(server.overworld());
		}
		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		long bucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
		if (bucket != lastAutosaveBucket) {
			lastAutosaveBucket = bucket;
			savePersistedData(server);
		}
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		DataManagerSystem.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, toPersistedData());
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static SeasonState getCurrentState() {
		return resolveDisplayState();
	}

	public static SeasonState getCurrentState(ServerLevel world) {
		return resolveCurrentState(world);
	}

	public static Season getCurrentSeason() {
		return getCurrentState().season();
	}

	public static Season getCurrentSeason(ServerLevel world) {
		return getCurrentState(world).season();
	}

	public static String getCurrentSeasonId() {
		return getCurrentSeason().id;
	}

	public static String getCurrentSeasonId(ServerLevel world) {
		return getCurrentSeason(world).id;
	}

	public static String getCurrentSeasonDisplayName() {
		return capitalizeSeasonId(getCurrentSeasonId());
	}

	public static String getCurrentSeasonDisplayName(ServerLevel world) {
		return capitalizeSeasonId(getCurrentSeasonId(world));
	}

	public static int getCurrentSeasonDay() {
		return getCurrentState().seasonDay();
	}

	public static int getCurrentSeasonDay(ServerLevel world) {
		return getCurrentState(world).seasonDay();
	}

	public static int getCurrentSeasonWeek() {
		return getCurrentState().week();
	}

	public static int getCurrentSeasonWeek(ServerLevel world) {
		return getCurrentState(world).week();
	}

	public static BiomeClimate resolveBiomeClimate(ServerLevel world, net.minecraft.core.BlockPos pos) {
		if (world == null || pos == null) {
			return BiomeClimate.TEMPERATE;
		}

		if (!settings.enabled || !settings.biomeOverridesEnabled) {
			return resolveNativeBiomeClimate(world, pos);
		}

		Identifier biomeId = resolveBiomeId(world, pos);
		if (biomeId == null) {
			return resolveNativeBiomeClimate(world, pos);
		}

		BiomeClimateRecord cached = BIOME_CLIMATE_CACHE.get(biomeId.toString());
		if (cached != null) {
			return cached.classification();
		}

		BiomeClimateRecord loaded = loadBiomeClimateRecord(world, pos, biomeId);
		BIOME_CLIMATE_CACHE.put(biomeId.toString(), loaded);
		return loaded.classification();
	}

	public static BiomeClimate resolveNativeBiomeClimate(ServerLevel world, net.minecraft.core.BlockPos pos) {
		if (world == null || pos == null) {
			return BiomeClimate.TEMPERATE;
		}

		Holder<Biome> biomeEntry = world.getBiome(pos);
		Biome biome = biomeEntry.value();
		Biome.Precipitation precipitation = resolveNativeBiomePrecipitation(biome);
		float temperature = biome.getBaseTemperature();
		return classifyBiome(precipitation, temperature, settings);
	}

	public static BiomeMoisture resolveBiomeMoisture(Biome biome) {
		if (biome == null) {
			return BiomeMoisture.WET;
		}

		return resolveBiomeMoisture(resolveNativeBiomePrecipitation(biome));
	}

	public static BiomeClimate resolveBiomeClimate(Biome biome) {
		if (biome == null) {
			return BiomeClimate.TEMPERATE;
		}

		return classifyBiome(null, biome.getBaseTemperature(), settings);
	}

	public static BiomeMoisture resolveBiomeMoisture(Biome.Precipitation precipitation) {
		if (precipitation == null) {
			return BiomeMoisture.WET;
		}

		return switch (precipitation) {
			case NONE -> BiomeMoisture.DRY;
			case RAIN -> BiomeMoisture.WET;
			case SNOW -> BiomeMoisture.SNOWY;
		};
	}

	public static double resolveWaterFreezeProgress(Season season, BiomeClimate climate, int seasonDay) {
		Season safeSeason = season == null ? Season.SPRING : season;
		BiomeClimate safeClimate = climate == null ? BiomeClimate.TEMPERATE : climate;
		int safeSeasonDay = clampSeasonDay(seasonDay);
		return switch (safeClimate) {
			case COLD -> resolveColdWaterProgress(safeSeason);
			case TEMPERATE -> resolveTemperateWaterProgress(safeSeason, safeSeasonDay);
			case HOT -> resolveHotWaterProgress(safeSeason);
		};
	}

	public static boolean shouldSeasonFreezeAt(ServerLevel world, Biome biome, net.minecraft.core.BlockPos pos) {
		if (world == null || biome == null || pos == null || !settings.enabled) {
			return false;
		}
		if (resolveSeasonalPrecipitation(biome) == SeasonalPrecipitation.RAIN) {
			return false;
		}

		SeasonState state = resolveCurrentState(world);
		BiomeClimate climate = resolveBiomeClimate(world, pos);
		double freezeProgress = resolveWaterFreezeProgress(state.season(), climate, state.seasonDay());
		if (freezeProgress <= 0.0d) {
			return false;
		}
		if (freezeProgress >= 1.0d) {
			return true;
		}
		return freezeProgress >= seasonalColumnBias(pos.getX(), pos.getZ());
	}

	public static SeasonalPrecipitation resolveSeasonalPrecipitation(Biome biome) {
		if (biome == null) {
			return SeasonalPrecipitation.DRY;
		}

		SeasonState state = getCurrentState();
		BiomeClimate climate = resolveBiomeClimate(biome);
		BiomeMoisture moisture = resolveBiomeMoisture(biome);
		return resolveSeasonalPrecipitation(state.season(), climate, moisture);
	}

	public static SeasonalPrecipitation resolveSeasonalPrecipitation(Season season, BiomeClimate climate, BiomeMoisture moisture) {
		Season safeSeason = season == null ? Season.SPRING : season;
		BiomeClimate safeClimate = climate == null ? BiomeClimate.TEMPERATE : climate;
		BiomeMoisture safeMoisture = moisture == null ? BiomeMoisture.WET : moisture;

		return switch (safeMoisture) {
			case DRY -> resolveDrySeasonalPrecipitation(safeSeason, safeClimate);
			case WET -> resolveWetSeasonalPrecipitation(safeSeason, safeClimate);
			case SNOWY -> resolveSnowySeasonalPrecipitation(safeSeason, safeClimate);
		};
	}

	private static double resolveTemperateWaterProgress(Season season, int seasonDay) {
		return switch (season) {
			case SPRING -> seasonDay < TEMPERATE_TRANSITION_START_DAY ? 1.0d : 0.0d;
			case SUMMER -> 0.0d;
			case FALL -> seasonDay < TEMPERATE_TRANSITION_START_DAY ? 0.0d : 1.0d;
			case WINTER -> 1.0d;
		};
	}

	private static double resolveColdWaterProgress(Season season) {
		return switch (season) {
			case SPRING -> 1.0d;
			case SUMMER -> 0.0d;
			case FALL -> 1.0d;
			case WINTER -> 1.0d;
		};
	}

	private static double resolveHotWaterProgress(Season season) {
		return switch (season) {
			case SPRING -> 0.0d;
			case SUMMER -> 0.0d;
			case FALL -> 0.0d;
			case WINTER -> 1.0d;
		};
	}

	private static double seasonalColumnBias(int worldX, int worldZ) {
		long mixed = 0x9E3779B97F4A7C15L;
		mixed ^= (long) worldX * 0x632BE59BD9B4E019L;
		mixed ^= (long) worldZ * 0x8CB92BA72F3D8DD7L;
		mixed ^= mixed >>> 33;
		mixed *= 0xFF51AFD7ED558CCDL;
		mixed ^= mixed >>> 33;
		mixed *= 0xC4CEB9FE1A85EC53L;
		mixed ^= mixed >>> 33;
		long positive = mixed & Long.MAX_VALUE;
		return positive / (double) Long.MAX_VALUE;
	}

	private static SeasonalPrecipitation resolveDrySeasonalPrecipitation(Season season, BiomeClimate climate) {
		return switch (climate) {
			case COLD -> switch (season) {
				case SPRING, SUMMER -> SeasonalPrecipitation.DRY;
				case FALL, WINTER -> SeasonalPrecipitation.RAIN;
			};
			case TEMPERATE -> switch (season) {
				case SPRING, SUMMER, FALL -> SeasonalPrecipitation.DRY;
				case WINTER -> SeasonalPrecipitation.RAIN;
			};
			case HOT -> SeasonalPrecipitation.DRY;
		};
	}

	private static SeasonalPrecipitation resolveWetSeasonalPrecipitation(Season season, BiomeClimate climate) {
		return switch (climate) {
			case COLD -> switch (season) {
				case SPRING -> SeasonalPrecipitation.RAIN;
				case SUMMER -> SeasonalPrecipitation.RAIN;
				case FALL, WINTER -> SeasonalPrecipitation.SNOW;
			};
			case TEMPERATE -> switch (season) {
				case SPRING, FALL -> SeasonalPrecipitation.RAIN;
				case SUMMER -> SeasonalPrecipitation.DRY;
				case WINTER -> SeasonalPrecipitation.SNOW;
			};
			case HOT -> switch (season) {
				case SPRING, FALL, WINTER -> SeasonalPrecipitation.RAIN;
				case SUMMER -> SeasonalPrecipitation.DRY;
			};
		};
	}

	private static SeasonalPrecipitation resolveSnowySeasonalPrecipitation(Season season, BiomeClimate climate) {
		return switch (climate) {
			case COLD -> SeasonalPrecipitation.SNOW;
			case TEMPERATE -> switch (season) {
				case SPRING, FALL, WINTER -> SeasonalPrecipitation.SNOW;
				case SUMMER -> SeasonalPrecipitation.RAIN;
			};
			case HOT -> switch (season) {
				case SPRING, FALL -> SeasonalPrecipitation.RAIN;
				case SUMMER -> SeasonalPrecipitation.DRY;
				case WINTER -> SeasonalPrecipitation.SNOW;
			};
		};
	}

	private static SeasonState refreshSeasonState(ServerLevel world) {
		SeasonState previousState = lastProcessedState;
		SeasonState currentState = resolveCurrentState(world);
		boolean seasonTransition = previousState.absoluteDay() >= 0L && previousState.season() != currentState.season();
		if (seasonTransition) {
			emitSeasonTransitionDebug(previousState, currentState);
		}
		if (!currentState.equals(previousState)) {
			lastProcessedState = currentState;
		}
		return currentState;
	}

	private static void emitSeasonTransitionDebug(SeasonState previousState, SeasonState currentState) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.SEASON, "season.transition")) {
			return;
		}

		MadokuDebug.event("season.transition", MadokuDebug.Domain.SEASON)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.subject("season")
			.field("from", previousState.season().id)
			.field("to", currentState.season().id)
			.field("absolute_day", currentState.absoluteDay())
			.field("season_day", currentState.seasonDay())
			.field("week", currentState.week())
			.field("day_in_week", currentState.dayInWeek())
			.field("cycle_day", currentState.cycleDay())
			.field("progress", formatProgress(currentState.progress()))
			.log();
	}

	private static ServerLevel resolveSeasonWorld(MinecraftServer server) {
		return server == null ? null : server.overworld();
	}

	private static SeasonState resolveDisplayState() {
		SeasonState snapshot = lastProcessedState;
		if (!MadokuTime.isEnabled() && snapshot.absoluteDay() >= 0L) {
			return snapshot;
		}
		return resolveCurrentState();
	}

	private static SeasonState resolveCurrentState() {
		return resolveCurrentState(null);
	}

	private static SeasonState resolveCurrentState(ServerLevel world) {
		long absoluteDayTime = MadokuTime.getCurrentAbsoluteDayTime(world);
		long absoluteDay = Math.max(0L, MadokuTime.getDay(absoluteDayTime));
		return resolveStateForAbsoluteDay(absoluteDay);
	}

	private static SeasonState resolveStateForAbsoluteDay(long absoluteDay) {
		long normalizedDay = Math.max(0L, absoluteDay);
		long cycleDay = Math.floorMod(normalizedDay, SEASON_YEAR_DAYS);
		Season season = Season.fromIndex((int) (cycleDay / MadokuSeasonConfig.DEFAULT_SEASON_LENGTH_DAYS));
		int seasonDay = (int) (cycleDay % MadokuSeasonConfig.DEFAULT_SEASON_LENGTH_DAYS);
		int week = (seasonDay / MadokuSeasonConfig.DEFAULT_DAYS_PER_WEEK) + 1;
		int dayInWeek = (seasonDay % MadokuSeasonConfig.DEFAULT_DAYS_PER_WEEK) + 1;
		double progress = normalizeSeasonProgress(seasonDay, MadokuSeasonConfig.DEFAULT_SEASON_LENGTH_DAYS);
		return new SeasonState(absoluteDay, cycleDay, season, seasonDay, week, dayInWeek, progress);
	}

	private static double normalizeSeasonProgress(int seasonDay, int seasonLengthDays) {
		if (seasonLengthDays <= 1) {
			return 1.0d;
		}
		int clamped = Math.max(0, Math.min(seasonLengthDays - 1, seasonDay));
		return (double) clamped / (double) (seasonLengthDays - 1);
	}

	private static String formatProgress(double value) {
		return String.format(java.util.Locale.ROOT, "%.3f", Math.max(0.0d, Math.min(1.0d, value)));
	}

	private static int clampSeasonDay(int seasonDay) {
		return Math.max(0, Math.min(MadokuSeasonConfig.DEFAULT_SEASON_LENGTH_DAYS - 1, seasonDay));
	}

	private static BiomeClimateRecord loadBiomeClimateRecord(ServerLevel world, net.minecraft.core.BlockPos pos, Identifier biomeId) {
		Holder<Biome> biomeEntry = world.getBiome(pos);
		Biome biome = biomeEntry.value();
		Biome.Precipitation precipitation = resolveNativeBiomePrecipitation(biome);
		float temperature = biome.getBaseTemperature();
		BiomeClimate nativeClimate = classifyBiome(precipitation, temperature, settings);
		Path file = resolveBiomeFile(biomeId);
		JsonObject defaults = MadokuSeasonConfig.buildBiomeDefaults(
			biomeId.toString(),
			nativeClimate.id,
			nativeClimate.id,
			temperature,
			precipitation.name()
		);

		try {
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(file, defaults);
			BiomeClimateRecord parsed = parseBiomeClimateRecord(normalized, biomeId, nativeClimate, temperature, precipitation);
			if (parsed != null) {
				return parsed;
			}
			JsonStaticSystem.writeManagedFile(file, defaults, defaults);
		} catch (IOException exception) {
			LOGGER.error("Failed to load MadokuSeason biome climate file {}", file, exception);
		}

		return new BiomeClimateRecord(biomeId, nativeClimate, nativeClimate, temperature, precipitation);
	}

	private static BiomeClimateRecord parseBiomeClimateRecord(
		JsonObject source,
		Identifier biomeId,
		BiomeClimate defaultClimate,
		float temperature,
		Biome.Precipitation precipitation
	) throws IOException {
		if (source == null) {
			return null;
		}

		String configuredBiomeId = getString(source, MadokuSeasonConfig.FIELD_BIOME_ID, biomeId.toString());
		String defaultClassification = normalizeClimateId(getString(source, MadokuSeasonConfig.FIELD_DEFAULT_CLASSIFICATION, defaultClimate.id));
		String classificationText = normalizeClimateId(getString(source, MadokuSeasonConfig.FIELD_CLASSIFICATION, defaultClassification));
		BiomeClimate classification = BiomeClimate.fromId(classificationText);
		BiomeClimate normalizedDefault = BiomeClimate.fromId(defaultClassification);
		boolean dirty = false;

		if (!biomeId.toString().equals(configuredBiomeId)) {
			dirty = true;
		}
		if (normalizedDefault == null) {
			normalizedDefault = defaultClimate;
			dirty = true;
		}
		if (classification == null) {
			classification = normalizedDefault;
			dirty = true;
		}

		String precipitationText = normalizeKey(getString(source, MadokuSeasonConfig.FIELD_BIOME_PRECIPITATION, precipitation.name()));
		if (precipitationText.isEmpty()) {
			precipitationText = precipitation.name().toLowerCase();
			dirty = true;
		}

		double recordedTemperature = getDouble(source, MadokuSeasonConfig.FIELD_BIOME_TEMPERATURE, temperature);
		if (Double.isNaN(recordedTemperature) || Double.isInfinite(recordedTemperature)) {
			recordedTemperature = temperature;
			dirty = true;
		}

		if (dirty) {
			JsonObject cleaned = MadokuSeasonConfig.buildBiomeDefaults(
				biomeId.toString(),
				normalizedDefault.id,
				classification.id,
				recordedTemperature,
				precipitationText
			);
			Path file = resolveBiomeFile(biomeId);
			JsonStaticSystem.writeManagedFile(file, cleaned, cleaned);
		}

		return new BiomeClimateRecord(biomeId, normalizedDefault, classification, (float) recordedTemperature, precipitation);
	}

	private static BiomeClimate classifyBiome(Biome.Precipitation precipitation, float temperature, Settings currentSettings) {
		Settings safeSettings = currentSettings == null ? Settings.defaults() : currentSettings;
		if (temperature <= safeSettings.coldTemperatureThreshold || precipitation == Biome.Precipitation.SNOW) {
			return BiomeClimate.COLD;
		}
		if (temperature >= safeSettings.hotTemperatureThreshold) {
			return BiomeClimate.HOT;
		}
		if (precipitation == Biome.Precipitation.NONE) {
			return BiomeClimate.HOT;
		}
		return BiomeClimate.TEMPERATE;
	}

	private static Biome.Precipitation resolveNativeBiomePrecipitation(Biome biome) {
		if (biome == null) {
			return Biome.Precipitation.RAIN;
		}

		if (!biome.hasPrecipitation()) {
			return Biome.Precipitation.NONE;
		}
		if (biome.getBaseTemperature() <= settings.coldTemperatureThreshold) {
			return Biome.Precipitation.SNOW;
		}
		return Biome.Precipitation.RAIN;
	}

	private static Identifier resolveBiomeId(ServerLevel world, net.minecraft.core.BlockPos pos) {
		try {
			Holder<Biome> biomeEntry = world.getBiome(pos);
			return biomeEntry.unwrapKey()
				.map(ResourceKey::identifier)
				.orElseGet(() -> {
					Registry<Biome> biomeRegistry = world.registryAccess().lookupOrThrow(Registries.BIOME);
					return biomeRegistry.getKey(biomeEntry.value());
				});
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static Path resolveBiomeFile(Identifier biomeId) {
		Path directory = resolveBiomeDirectory();
		String fileKey = MadokuSeasonConfig.normalizeKey(biomeId == null ? "" : biomeId.toString());
		return resolveJsonFile(directory, fileKey);
	}

	private static Path resolveBiomeDirectory() {
		Path directory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(SEASON_CONFIG_FOLDER_NAME).resolve(BIOME_FOLDER_NAME);
		try {
			Files.createDirectories(directory);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to create season biome directory: " + directory, exception);
		}
		return directory;
	}

	private static JsonObject createDefaultData() {
		return new JsonObject();
	}

	private static SeasonState parsePersistedState(JsonObject source) {
		return null;
	}

	private static JsonObject toPersistedData() {
		return new JsonObject();
	}

	private static void loadStaticConfig() {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path directory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(SEASON_CONFIG_FOLDER_NAME);
			Path configFile = resolveJsonFile(directory, SEASON_CONFIG_FILE_NAME);
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(configFile, defaults);
			Settings loaded = Settings.fromJson(normalized);
			JsonObject cleaned = loaded.toConfigJson();
			JsonStaticSystem.writeManagedFile(configFile, cleaned, defaults);
			settings = loaded;
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load MadokuSeason static config; using defaults.", exception);
		}
	}

	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Config file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private static double getDouble(JsonObject object, String key, double fallback) {
		if (object == null) {
			return fallback;
		}

		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}

		try {
			return element.getAsDouble();
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static String getString(JsonObject object, String key, String fallback) {
		if (object == null) {
			return fallback;
		}

		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}

		String value = element.getAsString();
		return value == null ? fallback : value;
	}

	private static String normalizeKey(String value) {
		return MadokuSeasonConfig.normalizeKey(value);
	}

	private static String normalizeClimateId(String value) {
		return MadokuSeasonConfig.normalizeClassification(value);
	}

	private static String capitalizeSeasonId(String value) {
		if (value == null || value.isBlank()) {
			return "Unknown";
		}
		String normalized = value.trim().toLowerCase();
		return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
	}

	private enum Season {
		SPRING("spring"),
		SUMMER("summer"),
		FALL("fall"),
		WINTER("winter");

		private final String id;

		Season(String id) {
			this.id = id;
		}

		private static Season fromIndex(int index) {
			Season[] values = values();
			if (index < 0 || index >= values.length) {
				return SPRING;
			}
			return values[index];
		}
	}

	public enum BiomeClimate {
		COLD("cold"),
		TEMPERATE("temperate"),
		HOT("hot");

		private final String id;

		BiomeClimate(String id) {
			this.id = id;
		}

		private static BiomeClimate fromId(String value) {
			if (value == null) {
				return null;
			}

			String normalized = value.trim().toLowerCase();
			for (BiomeClimate climate : values()) {
				if (climate.id.equals(normalized)) {
					return climate;
				}
			}
			return null;
		}
	}

	public enum BiomeMoisture {
		DRY,
		WET,
		SNOWY;
	}

	public enum SeasonalPrecipitation {
		DRY(Biome.Precipitation.NONE),
		RAIN(Biome.Precipitation.RAIN),
		SNOW(Biome.Precipitation.SNOW);

		private final Biome.Precipitation vanilla;

		SeasonalPrecipitation(Biome.Precipitation vanilla) {
			this.vanilla = vanilla;
		}

		public Biome.Precipitation vanilla() {
			return vanilla;
		}
	}

	public record SeasonState(
		long absoluteDay,
		long cycleDay,
		Season season,
		int seasonDay,
		int week,
		int dayInWeek,
		double progress
	) {
		private static SeasonState empty() {
			return new SeasonState(-1L, 0L, Season.SPRING, 0, 1, 1, 0.0d);
		}
	}

	private record BiomeClimateRecord(
		Identifier biomeId,
		BiomeClimate defaultClimate,
		BiomeClimate classification,
		float temperature,
		Biome.Precipitation precipitation
	) {
	}

	private record Settings(
		boolean enabled,
		boolean biomeOverridesEnabled,
		double coldTemperatureThreshold,
		double hotTemperatureThreshold
	) {
		private static Settings defaults() {
			return new Settings(
				true,
				true,
				MadokuSeasonConfig.DEFAULT_COLD_TEMPERATURE_THRESHOLD,
				MadokuSeasonConfig.DEFAULT_HOT_TEMPERATURE_THRESHOLD
			);
		}

		private static Settings fromJson(JsonObject source) {
			boolean enabled = getBoolean(source, MadokuSeasonConfig.FIELD_ENABLED, true);
			boolean biomeOverridesEnabled = getBoolean(source, MadokuSeasonConfig.FIELD_BIOME_OVERRIDES_ENABLED, true);
			double coldThreshold = getDouble(source, MadokuSeasonConfig.FIELD_COLD_TEMPERATURE_THRESHOLD, MadokuSeasonConfig.DEFAULT_COLD_TEMPERATURE_THRESHOLD);
			double hotThreshold = getDouble(source, MadokuSeasonConfig.FIELD_HOT_TEMPERATURE_THRESHOLD, MadokuSeasonConfig.DEFAULT_HOT_TEMPERATURE_THRESHOLD);

			if (!Double.isFinite(coldThreshold) || !Double.isFinite(hotThreshold) || coldThreshold >= hotThreshold) {
				coldThreshold = MadokuSeasonConfig.DEFAULT_COLD_TEMPERATURE_THRESHOLD;
				hotThreshold = MadokuSeasonConfig.DEFAULT_HOT_TEMPERATURE_THRESHOLD;
			}

			return new Settings(enabled, biomeOverridesEnabled, coldThreshold, hotThreshold);
		}

		private JsonObject toConfigJson() {
			JsonObject root = new JsonObject();
			root.addProperty(MadokuSeasonConfig.FIELD_ENABLED, enabled);
			root.addProperty(MadokuSeasonConfig.FIELD_BIOME_OVERRIDES_ENABLED, biomeOverridesEnabled);
			root.addProperty(MadokuSeasonConfig.FIELD_COLD_TEMPERATURE_THRESHOLD, coldTemperatureThreshold);
			root.addProperty(MadokuSeasonConfig.FIELD_HOT_TEMPERATURE_THRESHOLD, hotTemperatureThreshold);
			return root;
		}
	}

	private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
		if (object == null) {
			return fallback;
		}

		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}

		return element.getAsBoolean();
	}
}

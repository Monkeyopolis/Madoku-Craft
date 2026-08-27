package madoku.craft.smelting.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.json.JSONTypeManager;
import madoku.craft.core.json.MadokuJSONManager;
import madoku.craft.core.scheduler.MadokuSchedulerManager;
import madoku.craft.core.time.MadokuTimeManager;
import madoku.craft.mixin.smelting.AbstractFurnaceServerTickInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class MadokuSmeltingManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuSmeltingManager.class);

	private static final String SMELTING_CONFIG_FOLDER_NAME = "madoku-craft-smelting";
	private static final String SMELTING_CONFIG_FILE_NAME = "madoku-smelting";
	private static final String FURNACES_DIRECTORY_NAME = "madoku-furnaces";
	private static final String TASK_TYPE_SMELTING_TICK = "smelting_furnace_tick";
	private static final String FIELD_ENABLED = "enabled";

	private static final int MINIMUM_COOK_TICKS = 20;
	private static final int BASE_FURNACE_COOK_TICKS = 200;
	private static final int BASE_SMOKER_COOK_TICKS = 100;
	private static final int BASE_BLAST_COOK_TICKS = 100;

	private static final String FIELD_BLOCK_ID = "block-id";
	private static final String FIELD_BLOCK_ENTITY_ID = "block-entity-id";
	private static final String FIELD_RECIPE_TYPE_ID = "recipe-type-id";
	private static final String FIELD_SMELTING_SPEED = "smelting-speed";
	private static final String FIELD_FUEL_EFFICIENCY = "fuel-efficiency";

	private static final double SPEED_INCREMENT = 0.125;
	private static final double MIN_SMELTING_SPEED = 20.0;
	private static final double MAX_SMELTING_SPEED = 1200.0;
	private static final double MIN_FUEL_EFFICIENCY = 0.1;
	private static final double MAX_FUEL_EFFICIENCY = 10.0;

	private static final MadokuSmeltingConfig configuration = new MadokuSmeltingConfig();
	private static Map<BlockEntityType<?>, FurnaceBehavior> furnaceBehaviorByBlockEntityType = Map.of();
	private static final Map<FurnaceKey, String> furnaceSchedulerIds = new HashMap<>();
	private static final Set<FurnaceKey> scheduledFurnaces = new HashSet<>();
	private static final Map<FurnaceKey, Long> lastProcessedMadokuTickByFurnace = new HashMap<>();
	private static final Map<FurnaceKey, Long> lastProcessedGameTimeByFurnace = new HashMap<>();
	private static Map<RecipeType<?>, FurnaceBehavior> furnaceBehaviorByRecipeType = Map.of();

	private MadokuSmeltingManager() {
	}

	public static void initialize() {
		MadokuSchedulerManager.registerTaskHandler(TASK_TYPE_SMELTING_TICK, MadokuSmeltingManager::runScheduledFurnaceTask);
		resetRuntimeState();

		try {
			Path directory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(SMELTING_CONFIG_FOLDER_NAME);
			Path smeltingFile = resolveJsonFile(directory, SMELTING_CONFIG_FILE_NAME);

			JSONFormatManager.ManagedDocument smeltingDocument = JSONFormatManager.readManagedDocument(smeltingFile);
			boolean smeltingEnabled = readBoolean(smeltingDocument.settings(), FIELD_ENABLED, true);
			configuration.enableFeature = smeltingEnabled;
			JsonObject smeltingSettings = smeltingDocument.settings();
			smeltingSettings.addProperty(FIELD_ENABLED, smeltingEnabled);
			JSONFormatManager.writeManagedDocument(smeltingFile, new JsonObject(), smeltingSettings, JSONTypeManager.STATIC_CONFIG);

			FurnaceLoadResult furnaceLoadResult = loadFurnaceRules(directory);
			rebuildRules(furnaceLoadResult.behaviorByBlockEntityId);
		} catch (IOException | RuntimeException exception) {
			configuration.resetToDefaults();
			furnaceBehaviorByBlockEntityType = Map.of();
			LOGGER.error("Failed to load MadokuSmelting config; using defaults.", exception);
		}
	}

	public static boolean isEnabled() {
		return configuration.enableFeature;
	}

	public static int getCookTimeTicks(AbstractFurnaceBlockEntity furnace, int originalTicks) {
		if (!isEnabled() || furnace == null) {
			return originalTicks;
		}
		FurnaceBehavior behavior = furnaceBehaviorByBlockEntityType.get(furnace.getType());
		if (behavior == null) {
			return originalTicks;
		}
		return Math.max(MINIMUM_COOK_TICKS, toTicks(behavior.smeltingSpeed));
	}

	public static int getCookTimeTicks(RecipeType<?> recipeType, int originalTicks) {
		if (!isEnabled() || recipeType == null) {
			return originalTicks;
		}
		FurnaceBehavior behavior = furnaceBehaviorByRecipeType.get(recipeType);
		if (behavior == null) {
			return originalTicks;
		}
		return Math.max(MINIMUM_COOK_TICKS, toTicks(behavior.smeltingSpeed));
	}

	public static int getAdjustedFuelTicks(AbstractFurnaceBlockEntity furnace, ItemStack stack, int originalTicks) {
		if (!isEnabled() || furnace == null || stack == null || stack.isEmpty() || originalTicks <= 0) {
			return originalTicks;
		}

		FurnaceBehavior behavior = furnaceBehaviorByBlockEntityType.get(furnace.getType());
		if (behavior == null) {
			return originalTicks;
		}

		int baseCookTicks = getBaseCookTicks(furnace);
		double speedFactor = behavior.smeltingSpeed / (double) baseCookTicks;
		double adjusted = originalTicks * speedFactor * behavior.fuelEfficiency;
		return Math.max(1, toTicks(adjusted));
	}

	public static void onServerStarted() {
		resetRuntimeState();
	}

	public static void onServerStopped() {
		resetRuntimeState();
	}

	public static void onFurnaceServerTick(
		ServerLevel level,
		BlockPos blockPos,
		BlockState blockState,
		AbstractFurnaceBlockEntity furnace
	) {
		if (!isEnabled() || level == null || blockPos == null || furnace == null) {
			return;
		}

		MinecraftServer server = level.getServer();
		if (server == null) {
			return;
		}

		FurnaceKey key = FurnaceKey.from(level, blockPos);
		if (key == null) {
			return;
		}

		if (!shouldTrackFurnace(furnace, blockState)) {
			lastProcessedMadokuTickByFurnace.remove(key);
			return;
		}

		requestFurnaceProcessing(server, key, 0L);
	}

	private static void runScheduledFurnaceTask(MinecraftServer server, MadokuSchedulerManager.TaskContext context, JsonObject payload) {
		if (server == null || context == null || !isEnabled()) {
			return;
		}

		FurnaceKey key = FurnaceKey.from(context.getBinding());
		if (key == null) {
			return;
		}

		furnaceSchedulerIds.put(key, context.getSchedulerId());
		scheduledFurnaces.remove(key);
		ServerLevel level = resolveLevel(server, key.levelId());
		if (level == null) {
			clearFurnaceRuntimeState(key);
			return;
		}

		BlockPos blockPos = BlockPos.of(key.blockPosLong());
		BlockEntity blockEntity = level.getBlockEntity(blockPos);
		if (!(blockEntity instanceof AbstractFurnaceBlockEntity furnace)) {
			clearFurnaceRuntimeState(key);
			return;
		}

		long nowMadokuTick = context.getNowTick();
		long lastTick = lastProcessedMadokuTickByFurnace.getOrDefault(key, nowMadokuTick);
		long tickDelta = nowMadokuTick - lastTick;
		lastProcessedMadokuTickByFurnace.put(key, nowMadokuTick);

			long gameTime = level.getGameTime();
			long lastProcessedGameTime = lastProcessedGameTimeByFurnace.getOrDefault(key, Long.MIN_VALUE);
			if (lastProcessedGameTime != gameTime) {
				lastProcessedGameTimeByFurnace.put(key, gameTime);
				long extraTicksFromScheduling = Math.max(0L, tickDelta - 1L);
				// Ignore the normal 1-tick world-time drift; only jump-sized changes
				// should fast-forward furnaces.
				long extraTicksFromWorldTimeJump = Math.max(0L, MadokuTimeManager.getWorldTimeDelta() - 1L);
				long extraTicks = extraTicksFromScheduling + extraTicksFromWorldTimeJump;
				if (extraTicks > 0L) {
					advanceSingleFurnaceTicks(level, blockPos, extraTicks);
				}
			}

		BlockState currentState = level.getBlockState(blockPos);
		if (!shouldTrackFurnace(furnace, currentState)) {
			lastProcessedMadokuTickByFurnace.remove(key);
			lastProcessedGameTimeByFurnace.remove(key);
		}
	}

	private static void requestFurnaceProcessing(MinecraftServer server, FurnaceKey key, long delay) {
		if (server == null || key == null || scheduledFurnaces.contains(key)) {
			return;
		}

		String schedulerId = ensureSchedulerExists(key);
		if (enqueueFurnaceTask(schedulerId, delay)) {
			scheduledFurnaces.add(key);
			return;
		}

		String created = MadokuSchedulerManager.createOrGetScheduler(key.toBinding());
		furnaceSchedulerIds.put(key, created);
		if (enqueueFurnaceTask(created, delay)) {
			scheduledFurnaces.add(key);
			return;
		}

		LOGGER.error("Failed to enqueue smelting scheduler task for furnace={} @ {}", key.levelId(), key.blockPosLong());
	}

	private static String ensureSchedulerExists(FurnaceKey key) {
		String schedulerId = furnaceSchedulerIds.get(key);
		if (schedulerId == null || schedulerId.isBlank()) {
			schedulerId = MadokuSchedulerManager.createOrGetScheduler(key.toBinding());
			furnaceSchedulerIds.put(key, schedulerId);
		}
		return schedulerId;
	}

	private static boolean enqueueFurnaceTask(String schedulerId, long delay) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return false;
		}

		MadokuSchedulerManager.EnqueueStatus status = MadokuSchedulerManager.enqueue(
			schedulerId,
			Math.max(0L, delay),
			TASK_TYPE_SMELTING_TICK,
			new JsonObject(),
			MadokuSchedulerManager.TickDomain.GAMEPLAY
		);
		return status == MadokuSchedulerManager.EnqueueStatus.ACCEPTED
			|| status == MadokuSchedulerManager.EnqueueStatus.QUEUE_FULL;
	}

	private static void advanceSingleFurnaceTicks(ServerLevel level, BlockPos blockPos, long extraTicks) {
		if (level == null || blockPos == null || extraTicks <= 0L) {
			return;
		}

		for (long step = 0L; step < extraTicks; step++) {
			BlockEntity blockEntity = level.getBlockEntity(blockPos);
			if (!(blockEntity instanceof AbstractFurnaceBlockEntity furnace)) {
				break;
			}
			BlockState state = level.getBlockState(blockPos);
			AbstractFurnaceServerTickInvoker.madokuCraft$invokeServerTick(level, blockPos, state, furnace);
		}
	}

	private static boolean shouldTrackFurnace(AbstractFurnaceBlockEntity furnace, BlockState blockState) {
		if (furnace == null || blockState == null) {
			return false;
		}
		boolean litByState = blockState.hasProperty(BlockStateProperties.LIT) && Boolean.TRUE.equals(blockState.getValue(BlockStateProperties.LIT));
		boolean hasInput = !furnace.getItem(0).isEmpty();
		boolean hasFuel = !furnace.getItem(1).isEmpty();
		return litByState || (hasInput && hasFuel);
	}

	private static ServerLevel resolveLevel(MinecraftServer server, String levelId) {
		if (server == null || levelId == null || levelId.isBlank()) {
			return null;
		}
		Identifier location = Identifier.tryParse(levelId);
		if (location == null) {
			location = Identifier.tryParse(MadokuSchedulerManager.normalizeLevelIdentifier(levelId));
		}
		if (location == null) {
			return null;
		}
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, location);
		return server.getLevel(key);
	}

	private static void clearFurnaceRuntimeState(FurnaceKey key) {
		if (key == null) {
			return;
		}
		scheduledFurnaces.remove(key);
		furnaceSchedulerIds.remove(key);
		lastProcessedMadokuTickByFurnace.remove(key);
		lastProcessedGameTimeByFurnace.remove(key);
	}

	private static void resetRuntimeState() {
		furnaceSchedulerIds.clear();
		scheduledFurnaces.clear();
		lastProcessedMadokuTickByFurnace.clear();
		lastProcessedGameTimeByFurnace.clear();
	}

	private static FurnaceLoadResult loadFurnaceRules(Path smeltingRootDirectory) throws IOException {
		Path furnacesFolder = smeltingRootDirectory.resolve(FURNACES_DIRECTORY_NAME);

		Map<String, JsonObject> defaultFiles = buildDefaultFurnaceFiles();
		Map<String, JsonObject> loadedFiles = JSONFormatManager.ensureManagedFolder(
			furnacesFolder,
			defaultFiles,
			ignored -> buildGenericFurnaceDefaults(),
			(fileKey, sourceRoot) -> defaultFiles.containsKey(fileKey) || isSupportedFurnaceDefinition(sourceRoot),
			null
		);

		Map<String, FurnaceBehaviorDefinition> byBlockEntityId = new LinkedHashMap<>();

		for (Map.Entry<String, JsonObject> fileEntry : loadedFiles.entrySet()) {
			String fileKey = fileEntry.getKey();
			JsonObject root = fileEntry.getValue();
			JsonObject defaults = defaultFiles.getOrDefault(fileKey, buildGenericFurnaceDefaults());
			boolean changed = normalizeFurnaceDefinition(root, defaults);

			if (!isSupportedFurnaceDefinition(root)) {
				if (defaultFiles.containsKey(fileKey)) {
					root = defaults.deepCopy();
					changed = true;
				} else {
					Files.deleteIfExists(furnacesFolder.resolve(fileKey + ".json"));
					continue;
				}
			}

			String blockEntityTypeId = root.getAsJsonPrimitive(FIELD_BLOCK_ENTITY_ID).getAsString();
			double smeltingSpeed = normalizeSmeltingSpeed(readDouble(root, FIELD_SMELTING_SPEED, 200.0), 200.0);
			double fuelEfficiency = normalizeFuelEfficiency(readDouble(root, FIELD_FUEL_EFFICIENCY, 1.0), 1.0);

			changed |= setDouble(root, FIELD_SMELTING_SPEED, smeltingSpeed);
			changed |= setDouble(root, FIELD_FUEL_EFFICIENCY, fuelEfficiency);

			if (changed) {
				JsonObject fileDefaults = defaultFiles.getOrDefault(fileKey, buildGenericFurnaceDefaults());
				JSONFormatManager.writeManagedFile(
					furnacesFolder.resolve(fileKey + ".json"),
					root,
					fileDefaults,
					null
				);
			}

			byBlockEntityId.put(blockEntityTypeId, new FurnaceBehaviorDefinition(smeltingSpeed, fuelEfficiency));
		}

		return new FurnaceLoadResult(byBlockEntityId);
	}

	private static boolean normalizeFurnaceDefinition(JsonObject root, JsonObject defaults) {
		boolean changed = false;

		String defaultBlock = readString(defaults, FIELD_BLOCK_ID, "");
		String defaultBlockEntity = readString(defaults, FIELD_BLOCK_ENTITY_ID, "");
		String defaultRecipeType = readString(defaults, FIELD_RECIPE_TYPE_ID, "");
		double defaultSmeltingSpeed = readDouble(defaults, FIELD_SMELTING_SPEED, 200.0);
		double defaultFuelEfficiency = readDouble(defaults, FIELD_FUEL_EFFICIENCY, 1.0);

		String blockId = normalizeBlockId(readString(root, FIELD_BLOCK_ID, defaultBlock), defaultBlock);
		String blockEntityId = normalizeBlockEntityId(readString(root, FIELD_BLOCK_ENTITY_ID, defaultBlockEntity), defaultBlockEntity);
		String recipeTypeId = normalizeRecipeTypeId(readString(root, FIELD_RECIPE_TYPE_ID, defaultRecipeType), defaultRecipeType);
		double smeltingSpeed = normalizeSmeltingSpeed(readDouble(root, FIELD_SMELTING_SPEED, defaultSmeltingSpeed), defaultSmeltingSpeed);
		double fuelEfficiency = normalizeFuelEfficiency(readDouble(root, FIELD_FUEL_EFFICIENCY, defaultFuelEfficiency), defaultFuelEfficiency);

		changed |= setString(root, FIELD_BLOCK_ID, blockId);
		changed |= setString(root, FIELD_BLOCK_ENTITY_ID, blockEntityId);
		changed |= setString(root, FIELD_RECIPE_TYPE_ID, recipeTypeId);
		changed |= setDouble(root, FIELD_SMELTING_SPEED, smeltingSpeed);
		changed |= setDouble(root, FIELD_FUEL_EFFICIENCY, fuelEfficiency);
		return changed;
	}

	private static boolean isSupportedFurnaceDefinition(JsonObject root) {
		if (root == null) {
			return false;
		}
		String blockId = readString(root, FIELD_BLOCK_ID, "");
		String blockEntityId = readString(root, FIELD_BLOCK_ENTITY_ID, "");
		String recipeTypeId = readString(root, FIELD_RECIPE_TYPE_ID, "");
		return resolveBlockId(blockId) != null
			&& resolveBlockEntityTypeId(blockEntityId) != null
			&& resolveRecipeTypeId(recipeTypeId) != null;
	}

	private static Map<String, JsonObject> buildDefaultFurnaceFiles() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		defaults.put("furnace", buildFurnaceDefaultsObject(
			"minecraft:furnace",
			"minecraft:furnace",
			"minecraft:smelting",
			120.0,
			1.0
		));
		defaults.put("smoker", buildFurnaceDefaultsObject(
			"minecraft:smoker",
			"minecraft:smoker",
			"minecraft:smoking",
			80.0,
			1.5
		));
		defaults.put("blast_furnace", buildFurnaceDefaultsObject(
			"minecraft:blast_furnace",
			"minecraft:blast_furnace",
			"minecraft:blasting",
			80.0,
			1.5
		));
		return defaults;
	}

	private static JsonObject buildGenericFurnaceDefaults() {
		return buildFurnaceDefaultsObject("", "", "", 200.0, 1.0);
	}

	private static JsonObject buildFurnaceDefaultsObject(
		String blockId,
		String blockEntityId,
		String recipeTypeId,
		double smeltingSpeed,
		double fuelEfficiency
	) {
		return JSONFormatManager.object()
			.put(FIELD_BLOCK_ID, blockId)
			.put(FIELD_BLOCK_ENTITY_ID, blockEntityId)
			.put(FIELD_RECIPE_TYPE_ID, recipeTypeId)
			.put(FIELD_SMELTING_SPEED, smeltingSpeed)
			.put(FIELD_FUEL_EFFICIENCY, fuelEfficiency)
			.build();
	}

	private static String normalizeBlockId(String value, String fallback) {
		String resolved = resolveBlockId(value);
		if (resolved != null) {
			return resolved;
		}
		String fallbackResolved = resolveBlockId(fallback);
		return fallbackResolved == null ? "" : fallbackResolved;
	}

	private static String normalizeBlockEntityId(String value, String fallback) {
		String resolved = resolveBlockEntityTypeId(value);
		if (resolved != null) {
			return resolved;
		}
		String fallbackResolved = resolveBlockEntityTypeId(fallback);
		return fallbackResolved == null ? "" : fallbackResolved;
	}

	private static String normalizeRecipeTypeId(String value, String fallback) {
		String resolved = resolveRecipeTypeId(value);
		if (resolved != null) {
			return resolved;
		}
		String fallbackResolved = resolveRecipeTypeId(fallback);
		return fallbackResolved == null ? "" : fallbackResolved;
	}

	private static double normalizeSmeltingSpeed(double value, double fallback) {
		double resolved = normalizeRange(value, MIN_SMELTING_SPEED, MAX_SMELTING_SPEED, SPEED_INCREMENT);
		if (!Double.isFinite(resolved)) {
			return normalizeRange(fallback, MIN_SMELTING_SPEED, MAX_SMELTING_SPEED, SPEED_INCREMENT);
		}
		return resolved;
	}

	private static double normalizeFuelEfficiency(double value, double fallback) {
		double resolved = normalizeRange(value, MIN_FUEL_EFFICIENCY, MAX_FUEL_EFFICIENCY, SPEED_INCREMENT);
		if (!Double.isFinite(resolved)) {
			return normalizeRange(fallback, MIN_FUEL_EFFICIENCY, MAX_FUEL_EFFICIENCY, SPEED_INCREMENT);
		}
		return resolved;
	}

	private static double normalizeRange(double value, double min, double max, double increment) {
		if (!Double.isFinite(value)) {
			return Double.NaN;
		}
		double clamped = Math.min(max, Math.max(min, value));
		double rounded = Math.round(clamped / increment) * increment;
		return Math.min(max, Math.max(min, rounded));
	}

	private static String resolveBlockId(String value) {
		Identifier id = Identifier.tryParse(MadokuJSONManager.normalizeRegistryIdentifierForLookup(value));
		if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
			return null;
		}
		return id.toString();
	}

	private static String resolveBlockEntityTypeId(String value) {
		Identifier id = Identifier.tryParse(MadokuJSONManager.normalizeRegistryIdentifierForLookup(value));
		if (id == null || !BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)) {
			return null;
		}
		return id.toString();
	}

	private static String resolveRecipeTypeId(String value) {
		Identifier id = Identifier.tryParse(MadokuJSONManager.normalizeRegistryIdentifierForLookup(value));
		if (id == null || !BuiltInRegistries.RECIPE_TYPE.containsKey(id)) {
			return null;
		}
		return id.toString();
	}

	private static void rebuildRules(Map<String, FurnaceBehaviorDefinition> behaviorByBlockEntityId) {
		Map<BlockEntityType<?>, FurnaceBehavior> byBlockEntityType = new LinkedHashMap<>();
		Map<RecipeType<?>, FurnaceBehavior> byRecipeType = new LinkedHashMap<>();
		for (Map.Entry<String, FurnaceBehaviorDefinition> entry : behaviorByBlockEntityId.entrySet()) {
			BlockEntityType<?> blockEntityType = resolveBlockEntityType(entry.getKey());
			RecipeType<?> recipeType = resolveRecipeTypeForBlockEntity(entry.getKey());
			if (blockEntityType == null && recipeType == null) {
				continue;
			}
			FurnaceBehaviorDefinition definition = entry.getValue();
			FurnaceBehavior behavior = new FurnaceBehavior(definition.smeltingSpeed, definition.fuelEfficiency);
			if (blockEntityType != null) {
				byBlockEntityType.put(blockEntityType, behavior);
			}
			if (recipeType != null) {
				byRecipeType.put(recipeType, behavior);
			}
		}
		furnaceBehaviorByBlockEntityType = Map.copyOf(byBlockEntityType);
		furnaceBehaviorByRecipeType = Map.copyOf(byRecipeType);
	}

	private static RecipeType<?> resolveRecipeTypeForBlockEntity(String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		return switch (key) {
			case "minecraft:furnace" -> RecipeType.SMELTING;
			case "minecraft:smoker" -> RecipeType.SMOKING;
			case "minecraft:blast_furnace" -> RecipeType.BLASTING;
			default -> null;
		};
	}

	private static BlockEntityType<?> resolveBlockEntityType(String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		Identifier id = Identifier.tryParse(MadokuJSONManager.normalizeRegistryIdentifierForLookup(key));
		if (id == null || !BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)) {
			return null;
		}
		return BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);
	}

	private static boolean setString(JsonObject root, String key, String value) {
		String safeValue = value == null ? "" : value;
		JsonElement element = root.get(key);
		if (element instanceof JsonPrimitive primitive && primitive.isString()) {
			if (safeValue.equals(primitive.getAsString())) {
				return false;
			}
		}
		root.addProperty(key, safeValue);
		return true;
	}

	private static boolean setDouble(JsonObject root, String key, double value) {
		JsonElement element = root.get(key);
		if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
			if (Double.compare(primitive.getAsDouble(), value) == 0) {
				return false;
			}
		}
		root.addProperty(key, value);
		return true;
	}

	private static String readString(JsonObject root, String key, String fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element instanceof JsonPrimitive primitive && primitive.isString()) {
			return primitive.getAsString();
		}
		return fallback;
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
			return primitive.getAsDouble();
		}
		return fallback;
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
			return primitive.getAsBoolean();
		}
		return fallback;
	}

	private static int toTicks(double value) {
		if (!Double.isFinite(value) || value <= 0.0) {
			return 0;
		}
		return Math.max(1, (int) Math.round(value));
	}

	public static String describeRecipeType(RecipeType<?> recipeType) {
		if (recipeType == null) {
			return "unknown";
		}
		Identifier key = BuiltInRegistries.RECIPE_TYPE.getKey(recipeType);
		return key == null ? "unknown" : key.toString();
	}

	private static int getBaseCookTicks(AbstractFurnaceBlockEntity furnace) {
		if (furnace instanceof SmokerBlockEntity) {
			return BASE_SMOKER_COOK_TICKS;
		}
		if (furnace instanceof BlastFurnaceBlockEntity) {
			return BASE_BLAST_COOK_TICKS;
		}
		return BASE_FURNACE_COOK_TICKS;
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

	private record FurnaceKey(String levelId, long blockPosLong) {
		private static FurnaceKey from(ServerLevel level, BlockPos blockPos) {
			if (level == null || blockPos == null) {
				return null;
			}
			String normalizedLevelId = MadokuSchedulerManager.normalizeLevelIdentifier(level.dimension().toString());
			if (normalizedLevelId == null || normalizedLevelId.isBlank()) {
				return null;
			}
			return new FurnaceKey(normalizedLevelId, blockPos.asLong());
		}

		private static FurnaceKey from(MadokuSchedulerManager.SchedulerBinding binding) {
			if (binding == null || binding.getEventType() != MadokuSchedulerManager.EventType.BLOCK_ENTITY) {
				return null;
			}
			String levelId = binding.getLevelId();
			if (levelId == null || levelId.isBlank()) {
				return null;
			}
			Long blockPosLong = binding.getBlockPosLong();
			if (blockPosLong == null) {
				return null;
			}
			return new FurnaceKey(levelId, blockPosLong);
		}

		private MadokuSchedulerManager.SchedulerBinding toBinding() {
			return MadokuSchedulerManager.SchedulerBinding.blockEntity(TASK_TYPE_SMELTING_TICK, levelId, blockPosLong);
		}
	}

	private record FurnaceLoadResult(Map<String, FurnaceBehaviorDefinition> behaviorByBlockEntityId) {
	}

	private record FurnaceBehaviorDefinition(
		double smeltingSpeed,
		double fuelEfficiency
	) {
	}

	private record FurnaceBehavior(double smeltingSpeed, double fuelEfficiency) {
	}
}


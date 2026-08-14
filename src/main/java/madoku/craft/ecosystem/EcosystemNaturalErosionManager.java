package madoku.craft.ecosystem;

import com.google.gson.JsonObject;
import madoku.craft.api.chunk.MadokuChunkManager;
import madoku.craft.api.json.MadokuJSONManager;
import madoku.craft.api.json.JSONFormatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class EcosystemNaturalErosionManager {
	public static final String CHUNK_PROCESSOR_ID = "ecosystem_natural_erosion";

	private static final Logger LOGGER = LoggerFactory.getLogger(EcosystemNaturalErosionManager.class);
	private static final String CONFIG_FOLDER_NAME = "madoku-craft-ecosystem";
	private static final String CONFIG_FILE_NAME = "natural-erosion";

	private static volatile NaturalErosionConfigManager.Settings settings = NaturalErosionConfigManager.defaults();
	private static final Map<String, Identifier> BIOME_IDENTIFIERS = new ConcurrentHashMap<>();
	private static final Map<String, ResourceKey<Biome>> BIOME_KEYS = new ConcurrentHashMap<>();
	private static final Map<String, TagKey<Biome>> BIOME_TAG_KEYS = new ConcurrentHashMap<>();

	private static final MadokuChunkManager.ChunkProcessor CHUNK_PROCESSOR = new MadokuChunkManager.ChunkProcessor() {
		@Override
		public void handleRandomPosition(ServerLevel level, BlockPos position, RandomSource random) {
			EcosystemNaturalErosionManager.handleRandomPosition(level, position);
		}
	};

	private EcosystemNaturalErosionManager() {
	}

	public static void initialize() {
		loadConfig();
		MadokuChunkManager.registerChunkProcessor(CHUNK_PROCESSOR_ID, CHUNK_PROCESSOR);
	}

	public static void reset() {
		BIOME_IDENTIFIERS.clear();
		BIOME_KEYS.clear();
		BIOME_TAG_KEYS.clear();
	}

	public static NaturalErosionConfigManager.Settings getSettings() {
		return settings;
	}

	public static boolean isEnabled() {
		return MadokuEcosystemManager.isEnabled() && settings.isEnabled();
	}

	private static boolean isNaturalErosionEnabled() {
		return isEnabled();
	}

	public static void syncChunkProcessorActivation() {
		MadokuChunkManager.setChunkProcessorActive(CHUNK_PROCESSOR_ID, isEnabled());
	}

	static void handleRandomPosition(ServerLevel world, BlockPos position) {
		if (world == null || position == null || !isEnabled()) {
			return;
		}
		long currentAbsoluteDayTime = MadokuEcosystemManager.resolveCachedAbsoluteDayTime(world);
		int chunkX = position.getX() >> 4;
		int chunkZ = position.getZ() >> 4;
		EcosystemNaturalGrowthManager.processDirtInColumn(
			world,
			chunkX,
			chunkZ,
			currentAbsoluteDayTime,
			"wet",
			position.getX(),
			position.getZ()
		);

		BlockPos groundPosition = MadokuEcosystemManager.resolveCachedGroundPosition(world, position);
		if (groundPosition != null) {
			BlockState groundState = world.getBlockState(groundPosition);
			String seedKey = MadokuEcosystemManager.levelId(world) + "|" + groundPosition.asLong();
			MadokuEcosystemManager.DirtState trackedSeed = MadokuEcosystemManager.dirtBlocksByKey.get(seedKey);
			if (isWetSeedCandidate(world, groundPosition, groundState)
				&& (trackedSeed == null || !"wet".equals(trackedSeed.mode))) {
				spreadWetTrackingFromSeed(world, groundPosition);
			}
		}

		BlockState state = world.getBlockState(position);
		if (isLavaMagmaSeedCandidate(world, position, state)) {
			MadokuEcosystemManager.trackDirtCandidateForMode(world, position, state, "wet");
		}
	}

	static void spreadWetTrackingFromSeed(ServerLevel world, BlockPos seedPosition) {
		if (world == null || seedPosition == null || !isWaterErosionEnabled()) {
			return;
		}

		int radius = currentSettings().waterErosionRadius();
		for (int offsetX = -radius; offsetX <= radius; offsetX++) {
			for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
				if (Math.abs(offsetX) + Math.abs(offsetZ) > radius) {
					continue;
				}

				BlockPos groundPosition = seedPosition.offset(offsetX, 0, offsetZ);
				trackWetCandidate(world, groundPosition);
				trackWetCandidate(world, groundPosition.above());
			}
		}
	}

	private static void trackWetCandidate(ServerLevel world, BlockPos position) {
		if (world == null || position == null) {
			return;
		}
		BlockState state = world.getBlockState(position);
		if (isWetTrackedCandidate(world, position, state)) {
			MadokuEcosystemManager.trackDirtCandidateForMode(world, position, state, "wet");
		}
	}

	static boolean isWetSeedCandidate(ServerLevel world, BlockPos blockPos, BlockState state) {
		if (world == null || blockPos == null || state == null || !isWaterErosionEnabled() || !isTrackableGroundBlock(state)) {
			return false;
		}
		if (isSubmerged(world, blockPos)) {
			return false;
		}
		return isAdjacentToSurfaceWater(world, blockPos);
	}

	static boolean isLavaMagmaSeedCandidate(ServerLevel world, BlockPos blockPos, BlockState state) {
		if (world == null || blockPos == null || state == null || !isLavaErosionEnabled()) {
			return false;
		}
		String sourceBlockId = EcosystemConfigManager.blockId(state.getBlock());
		if (!MadokuEcosystemManager.isLavaMagmaSourceBlockId(sourceBlockId)) {
			return false;
		}
		return isAdjacentToLava(world, blockPos, currentSettings().lavaErosionRadius());
	}

	static boolean isWetTrackedCandidate(ServerLevel world, BlockPos blockPos, BlockState state) {
		if (world == null || blockPos == null || state == null || !isWaterErosionEnabled() || !isTrackableGroundBlock(state)) {
			return false;
		}
		if (MadokuEcosystemManager.resolveErosionRule(world, blockPos, state, "") == null) {
			return false;
		}
		return !isSubmerged(world, blockPos);
	}

	static void syncChunkProcessorTracking(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		// Candidate maps are queried directly by random-position dispatch.
	}

	static boolean isLavaMagmaSourceBlockId(String blockId) {
		if (blockId == null || blockId.isBlank()) {
			return false;
		}
		NaturalErosionConfigManager.NamedErosionRule magmaRule = findErosionRuleById(NaturalErosionConfigManager.FIELD_MAGMA_BLOCK);
		return magmaRule != null
			&& magmaRule.rule() != null
			&& magmaRule.rule().enabled()
			&& magmaRule.rule().sourceBlocks().contains(blockId);
	}

	static NaturalErosionConfigManager.NamedErosionRule resolveErosionRule(
		ServerLevel world,
		BlockPos pos,
		BlockState state,
		String preferredRuleId
	) {
		if (world == null || pos == null || state == null || !isNaturalErosionEnabled()) {
			return null;
		}
		String blockId = EcosystemConfigManager.blockId(state.getBlock());
		if (blockId.isBlank()) {
			return null;
		}

		NaturalErosionConfigManager.NamedErosionRule magmaRule = findErosionRuleById(NaturalErosionConfigManager.FIELD_MAGMA_BLOCK);
		if (magmaRule != null && isLavaErosionEnabled() && matchesLavaMagmaRule(world, pos, blockId, magmaRule.ruleId(), magmaRule.rule())) {
			return magmaRule;
		}

		if (preferredRuleId != null && !preferredRuleId.isBlank()) {
			for (NaturalErosionConfigManager.NamedErosionRule candidate : MadokuEcosystemManager.cachedErosionRules) {
				if (!preferredRuleId.equals(candidate.ruleId())) {
					continue;
				}
				if (!isErosionRuleEnabled(candidate.ruleId())) {
					break;
				}
				if (NaturalErosionConfigManager.FIELD_MAGMA_BLOCK.equals(candidate.ruleId())) {
					break;
				}
				if (matchesErosionRule(world, pos, blockId, candidate.ruleId(), candidate.rule())) {
					return candidate;
				}
				break;
			}
		}

		for (NaturalErosionConfigManager.NamedErosionRule candidate : MadokuEcosystemManager.cachedErosionRules) {
			if (!isErosionRuleEnabled(candidate.ruleId())) {
				continue;
			}
			if (NaturalErosionConfigManager.FIELD_MAGMA_BLOCK.equals(candidate.ruleId())) {
				continue;
			}
			if (matchesErosionRule(world, pos, blockId, candidate.ruleId(), candidate.rule())) {
				return candidate;
			}
		}
		return null;
	}

	private static NaturalErosionConfigManager.NamedErosionRule findErosionRuleById(String ruleId) {
		if (ruleId == null || ruleId.isBlank()) {
			return null;
		}
		for (NaturalErosionConfigManager.NamedErosionRule candidate : MadokuEcosystemManager.cachedErosionRules) {
			if (candidate == null || candidate.rule() == null) {
				continue;
			}
			if (ruleId.equals(candidate.ruleId())) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean matchesErosionRule(
		ServerLevel world,
		BlockPos pos,
		String sourceBlockId,
		String ruleId,
		NaturalErosionConfigManager.ErosionRuleSettings rule
	) {
		if (world == null || pos == null || sourceBlockId == null || sourceBlockId.isBlank() || rule == null || !rule.enabled()) {
			return false;
		}
		if (!rule.sourceBlocks().contains(sourceBlockId)) {
			return false;
		}
		Block targetBlock = resolveErosionTargetBlock(ruleId);
		if (targetBlock == null) {
			return false;
		}

		List<String> eligibleBiomes = rule.eligibleBiomes();
		if (eligibleBiomes == null || eligibleBiomes.isEmpty()) {
			return true;
		}

		Holder<Biome> biomeHolder = world.getBiome(pos);
		for (String biomeEntry : eligibleBiomes) {
			String normalized = biomeEntry == null ? "" : biomeEntry.trim();
			if (normalized.isEmpty()) {
				continue;
			}
			if (normalized.startsWith("#")) {
				normalized = normalized.substring(1);
			}
			String normalizedBiomeId = MadokuJSONManager.normalizeRegistryIdentifierForLookup(normalized);
			Identifier id = BIOME_IDENTIFIERS.computeIfAbsent(normalizedBiomeId, Identifier::tryParse);
			if (id == null) {
				continue;
			}
			ResourceKey<Biome> biomeKey = BIOME_KEYS.computeIfAbsent(
				normalizedBiomeId,
				key -> ResourceKey.create(Registries.BIOME, id)
			);
			TagKey<Biome> biomeTagKey = BIOME_TAG_KEYS.computeIfAbsent(
				normalizedBiomeId,
				key -> TagKey.create(Registries.BIOME, id)
			);
			if (biomeHolder.is(biomeKey)) {
				return true;
			}
			if (biomeHolder.is(biomeTagKey)) {
				return true;
			}
		}
		return false;
	}

	private static boolean matchesLavaMagmaRule(
		ServerLevel world,
		BlockPos pos,
		String sourceBlockId,
		String ruleId,
		NaturalErosionConfigManager.ErosionRuleSettings rule
	) {
		return isLavaErosionEnabled()
			&& matchesErosionRule(world, pos, sourceBlockId, ruleId, rule)
			&& isAdjacentToLava(world, pos, currentSettings().lavaErosionRadius());
	}

	private static Block resolveErosionTargetBlock(String ruleId) {
		String normalizedRuleId = EcosystemConfigManager.normalize(ruleId);
		String targetBlockId = switch (normalizedRuleId) {
			case NaturalErosionConfigManager.FIELD_MUD -> "minecraft:mud";
			case NaturalErosionConfigManager.FIELD_RED_SAND -> "minecraft:red_sand";
			case NaturalErosionConfigManager.FIELD_SAND -> "minecraft:sand";
			case NaturalErosionConfigManager.FIELD_MAGMA_BLOCK -> "minecraft:magma_block";
			default -> "";
		};
		return EcosystemConfigManager.resolveBlock(targetBlockId);
	}

	static boolean isTrackableGroundBlock(BlockState state) {
		if (state == null) {
			return false;
		}
		Block block = state.getBlock();
		if (block == Blocks.DIRT) {
			return true;
		}
		if (MadokuEcosystemManager.TRACKABLE_WET_GROUND_BLOCKS.contains(block) && isWaterErosionEnabled()) {
			return true;
		}
		String blockId = EcosystemConfigManager.blockId(block);
		if (isLavaMagmaSourceBlockId(blockId) && isLavaErosionEnabled()) {
			return true;
		}
		if (!isWaterErosionEnabled()) {
			return false;
		}
		for (NaturalErosionConfigManager.NamedErosionRule rule : MadokuEcosystemManager.cachedErosionRules) {
			if (rule == null || rule.rule() == null || !rule.rule().enabled()) {
				continue;
			}
			if (NaturalErosionConfigManager.FIELD_MAGMA_BLOCK.equals(rule.ruleId())) {
				continue;
			}
			if (rule.rule().sourceBlocks().contains(blockId)) {
				return true;
			}
		}
		return false;
	}

	static Block resolveWetGroundReplacementBlock(ServerLevel world, BlockPos pos, BlockState state, String preferredRuleId) {
		NaturalErosionConfigManager.NamedErosionRule rule = resolveErosionRule(world, pos, state, preferredRuleId);
		if (rule == null || rule.rule() == null) {
			return null;
		}
		return resolveErosionTargetBlock(rule.ruleId());
	}

	static boolean isSubmerged(ServerLevel world, BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}
		return world.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)
			|| world.getFluidState(pos.above()).is(net.minecraft.tags.FluidTags.WATER);
	}

	static boolean isAdjacentToSurfaceWater(ServerLevel world, BlockPos blockPos) {
		if (world == null || blockPos == null) {
			return false;
		}
		for (Direction direction : Direction.values()) {
			if (direction == null) {
				continue;
			}
			if (isSurfaceLevelWater(world, blockPos.relative(direction))) {
				return true;
			}
		}
		return false;
	}

	static boolean isAdjacentToLava(ServerLevel world, BlockPos blockPos, int radius) {
		if (world == null || blockPos == null || radius < 0) {
			return false;
		}
		if (world.getFluidState(blockPos).is(net.minecraft.tags.FluidTags.LAVA)) {
			return true;
		}
		if (radius == 0) {
			return false;
		}
		for (Direction direction : Direction.values()) {
			if (direction == null) {
				continue;
			}
			BlockPos neighborPos = blockPos.relative(direction);
			if (world.getFluidState(neighborPos).is(net.minecraft.tags.FluidTags.LAVA)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isSurfaceLevelWater(ServerLevel world, BlockPos waterPos) {
		if (world == null || waterPos == null) {
			return false;
		}
		var fluidState = world.getFluidState(waterPos);
		if (!fluidState.is(net.minecraft.tags.FluidTags.WATER) || !fluidState.isSource()) {
			return false;
		}
		var aboveFluidState = world.getFluidState(waterPos.above());
		if (aboveFluidState.is(net.minecraft.tags.FluidTags.WATER)) {
			return false;
		}
		int topY = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, waterPos.getX(), waterPos.getZ()) - 1;
		return waterPos.getY() >= topY;
	}

	private static NaturalErosionConfigManager.Settings currentSettings() {
		return settings == null ? NaturalErosionConfigManager.defaults() : settings;
	}

	static boolean isWaterErosionEnabled() {
		NaturalErosionConfigManager.Settings current = currentSettings();
		return isEnabled() && current.waterErosion() != null && current.waterErosion().enabled();
	}

	static boolean isLavaErosionEnabled() {
		NaturalErosionConfigManager.Settings current = currentSettings();
		return isEnabled() && current.lavaErosion() != null && current.lavaErosion().enabled();
	}

	private static boolean isErosionRuleEnabled(String ruleId) {
		if (NaturalErosionConfigManager.FIELD_MAGMA_BLOCK.equals(EcosystemConfigManager.normalize(ruleId))) {
			return isLavaErosionEnabled();
		}
		return isWaterErosionEnabled();
	}

	private static void loadConfig() {
		NaturalErosionConfigManager.Settings fallback = NaturalErosionConfigManager.defaults();
		JsonObject defaults = NaturalErosionConfigManager.buildDefaultsJson();
		try {
			Path rootDirectory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path file = rootDirectory.resolve(CONFIG_FILE_NAME + ".json");
			JsonObject normalized = JSONFormatManager.ensureManagedFile(file, defaults);
			settings = NaturalErosionConfigManager.fromJson(normalized);
			JSONFormatManager.writeManagedFile(file, NaturalErosionConfigManager.toJson(settings), defaults);
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load EcosystemNaturalErosionManager config; using defaults.", exception);
		}
	}

}

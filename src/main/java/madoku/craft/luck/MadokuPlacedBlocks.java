package madoku.craft.luck;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import madoku.craft.MadokuCraft;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.scheduler.SchedulerManagerSystem;
import madoku.craft.time.MadokuTime;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class MadokuPlacedBlocks {
	private static final String DATA_ID = "madoku_placed_blocks";
	private static final String FIELD_LEVELS = "levels";
	private static final String FIELD_TRACKED_SINCE_GAMEPLAY_TICK = "tracked-since-gameplay-tick";
	private static final long PLACED_BLOCK_RETENTION_DAYS = 336L;

	private static final SavedDataType<PlacedBlockData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, DATA_ID),
		PlacedBlockData::new,
		PlacedBlockData.CODEC,
		null
	);

	private MadokuPlacedBlocks() {
	}

	public static void initialize() {
	}

	public static void reset() {
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		getData(server).clearExpiredPlacedBlocks(null);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		getData(server).clearExpiredPlacedBlocks(null);
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		getData(server).clearExpiredPlacedBlocks(null);
	}

	public static void recordPlacedBlock(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return;
		}

		PlacedBlockData data = getData(level.getServer());
		if (data != null) {
			data.recordPlacedBlock(level, pos);
		}
	}

	public static boolean isPlayerPlacedBlock(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return false;
		}

		PlacedBlockData data = getData(level.getServer());
		return data != null && data.isPlayerPlacedBlock(level, pos);
	}

	public static boolean consumePlacedBlock(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return false;
		}

		PlacedBlockData data = getData(level.getServer());
		return data != null && data.consumePlacedBlock(level, pos);
	}

	private static PlacedBlockData getData(MinecraftServer server) {
		if (server == null) {
			return null;
		}

		ServerLevel level = server.getLevel(ServerLevel.OVERWORLD);
		if (level == null) {
			return new PlacedBlockData();
		}

		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	private static String levelId(ServerLevel world) {
		if (world == null) {
			return "";
		}
		return SchedulerManagerSystem.normalizeLevelIdentifier(world.dimension().toString());
	}

	private static long resolvePlacedBlockRetentionTicks() {
		long dayTicks = Math.max(1L, MadokuTime.getGameplayTicksPerDay());
		try {
			return Math.max(1L, Math.multiplyExact(PLACED_BLOCK_RETENTION_DAYS, dayTicks));
		} catch (ArithmeticException exception) {
			return Long.MAX_VALUE;
		}
	}

	private static long packChunk(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
	}

	private static int packLocalBlockPos(BlockPos pos) {
		int localX = pos.getX() & 15;
		int localZ = pos.getZ() & 15;
		int localY = pos.getY() & 0xFFFF;
		return (localY << 8) | (localX << 4) | localZ;
	}

	private static final class PlacedBlockData extends SavedData {
		private static final Codec<PlacedBlockData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.fieldOf(FIELD_TRACKED_SINCE_GAMEPLAY_TICK).forGetter(data -> data.trackedSinceGameplayTick),
			Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.LONG, Codec.INT.listOf()))
				.fieldOf(FIELD_LEVELS)
				.forGetter(PlacedBlockData::encodedLevels)
		).apply(instance, PlacedBlockData::new));

		private final Map<String, Map<Long, Set<Integer>>> placedBlocksByLevel = new HashMap<>();
		private long trackedSinceGameplayTick = -1L;

		private PlacedBlockData() {
		}

		private PlacedBlockData(long trackedSinceGameplayTick, Map<String, Map<Long, List<Integer>>> encodedLevels) {
			this.trackedSinceGameplayTick = trackedSinceGameplayTick;
			importEncodedLevels(encodedLevels);
			normalizeLoadedState();
		}

		private void recordPlacedBlock(ServerLevel level, BlockPos pos) {
			clearExpiredPlacedBlocks(level);

			String levelId = levelId(level);
			if (levelId.isBlank()) {
				return;
			}

			if (!hasTrackedBlocks()) {
				trackedSinceGameplayTick = MadokuTicks.getGameplayTicks();
			}

			long packedChunk = packChunk(pos.getX() >> 4, pos.getZ() >> 4);
			int packedLocalPos = packLocalBlockPos(pos);
			Map<Long, Set<Integer>> chunks = placedBlocksByLevel.computeIfAbsent(levelId, ignored -> new HashMap<>());
			Set<Integer> positions = chunks.computeIfAbsent(packedChunk, ignored -> new HashSet<>());
			if (!positions.add(packedLocalPos)) {
				return;
			}

			setDirty();
			MadokuLuck.emitLuckDebug(
				"luck.place_recorded",
				level,
				pos,
				"block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ(),
				Map.of(
					"tracked_count", Integer.toString(countTrackedBlocksInLevel(levelId)),
					"tracked_since_gameplay_tick", Long.toString(Math.max(0L, trackedSinceGameplayTick))
				)
			);
		}

		private boolean isPlayerPlacedBlock(ServerLevel level, BlockPos pos) {
			clearExpiredPlacedBlocks(level);

			String levelId = levelId(level);
			Map<Long, Set<Integer>> chunks = placedBlocksByLevel.get(levelId);
			long packedChunk = packChunk(pos.getX() >> 4, pos.getZ() >> 4);
			Set<Integer> positions = chunks == null ? null : chunks.get(packedChunk);
			boolean tracked = positions != null && positions.contains(packLocalBlockPos(pos));
			MadokuLuck.emitLuckDebug(
				"luck.place_lookup",
				level,
				pos,
				"block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ(),
				Map.of(
					"tracked", Boolean.toString(tracked),
					"tracked_count", Integer.toString(countTrackedBlocksInLevel(levelId))
				)
			);
			return tracked;
		}

		private boolean consumePlacedBlock(ServerLevel level, BlockPos pos) {
			clearExpiredPlacedBlocks(level);

			String levelId = levelId(level);
			Map<Long, Set<Integer>> chunks = placedBlocksByLevel.get(levelId);
			if (chunks == null) {
				return false;
			}

			long packedChunk = packChunk(pos.getX() >> 4, pos.getZ() >> 4);
			Set<Integer> positions = chunks.get(packedChunk);
			if (positions == null || !positions.remove(packLocalBlockPos(pos))) {
				return false;
			}

			if (positions.isEmpty()) {
				chunks.remove(packedChunk);
			}
			if (chunks.isEmpty()) {
				placedBlocksByLevel.remove(levelId);
			}
			if (!hasTrackedBlocks()) {
				trackedSinceGameplayTick = -1L;
			}

			setDirty();
			MadokuLuck.emitLuckDebug(
				"luck.place_consumed",
				level,
				pos,
				"block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ(),
				Map.of("remaining_count", Integer.toString(positions.size()))
			);
			return true;
		}

		private void clearExpiredPlacedBlocks(ServerLevel level) {
			if (!hasTrackedBlocks() || trackedSinceGameplayTick < 0L) {
				if (!hasTrackedBlocks()) {
					trackedSinceGameplayTick = -1L;
				}
				return;
			}

			long expiresAfterTicks = resolvePlacedBlockRetentionTicks();
			long nowGameplayTick = MadokuTicks.getGameplayTicks();
			if (nowGameplayTick - trackedSinceGameplayTick < expiresAfterTicks) {
				return;
			}

			int expiredLevels = placedBlocksByLevel.size();
			int expiredBlocks = totalTrackedBlocks();
			placedBlocksByLevel.clear();
			trackedSinceGameplayTick = -1L;
			setDirty();
			MadokuLuck.emitLuckDebug(
				"luck.place_list_expired",
				level,
				null,
				"global",
				Map.of(
					"expired_levels", Integer.toString(expiredLevels),
					"expired_blocks", Integer.toString(expiredBlocks),
					"expiry_ticks", Long.toString(expiresAfterTicks)
				)
			);
		}

		private void normalizeLoadedState() {
			if (!hasTrackedBlocks()) {
				trackedSinceGameplayTick = -1L;
				return;
			}

			if (trackedSinceGameplayTick < 0L) {
				trackedSinceGameplayTick = MadokuTicks.getGameplayTicks();
				setDirty();
			}
		}

		private void importEncodedLevels(Map<String, Map<Long, List<Integer>>> encodedLevels) {
			placedBlocksByLevel.clear();
			if (encodedLevels == null || encodedLevels.isEmpty()) {
				return;
			}

			for (Map.Entry<String, Map<Long, List<Integer>>> levelEntry : encodedLevels.entrySet()) {
				if (levelEntry == null) {
					continue;
				}

				String levelId = levelEntry.getKey();
				Map<Long, List<Integer>> encodedChunks = levelEntry.getValue();
				if (levelId == null || levelId.isBlank() || encodedChunks == null || encodedChunks.isEmpty()) {
					continue;
				}

				Map<Long, Set<Integer>> chunks = new HashMap<>();
				for (Map.Entry<Long, List<Integer>> chunkEntry : encodedChunks.entrySet()) {
					if (chunkEntry == null) {
						continue;
					}

					Long packedChunk = chunkEntry.getKey();
					List<Integer> encodedPositions = chunkEntry.getValue();
					if (packedChunk == null || encodedPositions == null || encodedPositions.isEmpty()) {
						continue;
					}

					Set<Integer> positions = new HashSet<>();
					for (Integer packedLocalPos : encodedPositions) {
						if (packedLocalPos != null) {
							positions.add(packedLocalPos);
						}
					}

					if (!positions.isEmpty()) {
						chunks.put(packedChunk, positions);
					}
				}

				if (!chunks.isEmpty()) {
					placedBlocksByLevel.put(levelId, chunks);
				}
			}
		}

		private Map<String, Map<Long, List<Integer>>> encodedLevels() {
			Map<String, Map<Long, List<Integer>>> encoded = new LinkedHashMap<>();
			List<String> levelIds = new ArrayList<>(placedBlocksByLevel.keySet());
			levelIds.sort(String::compareTo);

			for (String levelId : levelIds) {
				if (levelId == null || levelId.isBlank()) {
					continue;
				}

				Map<Long, Set<Integer>> chunks = placedBlocksByLevel.get(levelId);
				if (chunks == null || chunks.isEmpty()) {
					continue;
				}

				Map<Long, List<Integer>> encodedChunks = new LinkedHashMap<>();
				List<Long> packedChunks = new ArrayList<>(chunks.keySet());
				packedChunks.sort(Comparator.naturalOrder());

				for (Long packedChunk : packedChunks) {
					if (packedChunk == null) {
						continue;
					}

					Set<Integer> positions = chunks.get(packedChunk);
					if (positions == null || positions.isEmpty()) {
						continue;
					}

					List<Integer> encodedPositions = new ArrayList<>(positions);
					encodedPositions.sort(Comparator.naturalOrder());
					encodedChunks.put(packedChunk, encodedPositions);
				}

				if (!encodedChunks.isEmpty()) {
					encoded.put(levelId, encodedChunks);
				}
			}
			return encoded;
		}

		private boolean hasTrackedBlocks() {
			return totalTrackedBlocks() > 0;
		}

		private int totalTrackedBlocks() {
			int count = 0;
			for (Map<Long, Set<Integer>> chunks : placedBlocksByLevel.values()) {
				if (chunks == null) {
					continue;
				}
				for (Set<Integer> positions : chunks.values()) {
					if (positions != null) {
						count += positions.size();
					}
				}
			}
			return count;
		}

		private int countTrackedBlocksInLevel(String levelId) {
			Map<Long, Set<Integer>> chunks = placedBlocksByLevel.get(levelId);
			if (chunks == null || chunks.isEmpty()) {
				return 0;
			}

			int count = 0;
			for (Set<Integer> positions : chunks.values()) {
				if (positions != null) {
					count += positions.size();
				}
			}
			return count;
		}
	}
}

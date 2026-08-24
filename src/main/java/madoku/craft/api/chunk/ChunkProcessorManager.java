package madoku.craft.api.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ChunkProcessorManager {
	private static final Map<String, MadokuChunkManager.ChunkProcessor> PROCESSORS = new LinkedHashMap<>();
	private static final Set<String> ACTIVE_PROCESSOR_IDS = new LinkedHashSet<>();
	/**
	 * Published only when registration or activation changes. Random-position dispatches are much
	 * more frequent than either of those operations, so the hot path must not copy or resolve IDs.
	 */
	private static volatile ProcessorSnapshot ACTIVE_PROCESSOR_SNAPSHOT = new ProcessorSnapshot(List.of());

	private ChunkProcessorManager() {
	}

	static void reset() {
		// Processor registration survives server resets; activation is synchronized by each consumer.
		rebuildActiveProcessorSnapshot();
	}

	static void registerChunkProcessor(String processorId, MadokuChunkManager.ChunkProcessor processor) {
		String normalizedId = normalize(processorId);
		if (normalizedId.isBlank() || processor == null) {
			return;
		}
		PROCESSORS.put(normalizedId, processor);
		ACTIVE_PROCESSOR_IDS.add(normalizedId);
		rebuildActiveProcessorSnapshot();
	}

	static void setChunkProcessorActive(String processorId, boolean active) {
		String normalizedId = normalize(processorId);
		if (normalizedId.isBlank() || !PROCESSORS.containsKey(normalizedId)) {
			return;
		}
		if (active) {
			ACTIVE_PROCESSOR_IDS.add(normalizedId);
		} else {
			ACTIVE_PROCESSOR_IDS.remove(normalizedId);
		}
		rebuildActiveProcessorSnapshot();
	}

	static void dispatchRandomPosition(ServerLevel level, BlockPos position, RandomSource random) {
		if (level == null || position == null || !ChunkConfigManager.isChunkSystemEnabled()) {
			return;
		}
		for (MadokuChunkManager.ChunkProcessor processor : ACTIVE_PROCESSOR_SNAPSHOT.forWorld(level)) {
			if (!processor.acceptsRandomPosition(level, position)) {
				continue;
			}
			processor.handleRandomPosition(level, position, random);
		}
	}

	private static void rebuildActiveProcessorSnapshot() {
		List<MadokuChunkManager.ChunkProcessor> snapshot = new ArrayList<>(ACTIVE_PROCESSOR_IDS.size());
		for (String processorId : ACTIVE_PROCESSOR_IDS) {
			MadokuChunkManager.ChunkProcessor processor = PROCESSORS.get(processorId);
			if (processor != null) {
				snapshot.add(processor);
			}
		}
		ACTIVE_PROCESSOR_SNAPSHOT = new ProcessorSnapshot(List.copyOf(snapshot));
	}

	private static final class ProcessorSnapshot {
		private final List<MadokuChunkManager.ChunkProcessor> processors;
		private final Map<String, List<MadokuChunkManager.ChunkProcessor>> processorsByDimension = new HashMap<>();

		private ProcessorSnapshot(List<MadokuChunkManager.ChunkProcessor> processors) {
			this.processors = processors;
		}

		private List<MadokuChunkManager.ChunkProcessor> forWorld(ServerLevel level) {
			String dimensionId = MadokuChunkManager.levelId(level);
			List<MadokuChunkManager.ChunkProcessor> applicable = processorsByDimension.get(dimensionId);
			if (applicable == null) {
				applicable = filterForWorld(level);
				processorsByDimension.put(dimensionId, applicable);
			}
			return applicable;
		}

		private List<MadokuChunkManager.ChunkProcessor> filterForWorld(ServerLevel level) {
			List<MadokuChunkManager.ChunkProcessor> applicable = new ArrayList<>(processors.size());
			for (MadokuChunkManager.ChunkProcessor processor : processors) {
				if (processor.acceptsWorld(level)) {
					applicable.add(processor);
				}
			}
			return List.copyOf(applicable);
		}
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}
}

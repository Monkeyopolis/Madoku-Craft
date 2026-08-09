package madoku.craft.api.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class ChunkProcessorManager {
	private static final Map<String, MadokuChunkManager.ChunkProcessor> PROCESSORS = new LinkedHashMap<>();
	private static final Set<String> ACTIVE_PROCESSOR_IDS = new LinkedHashSet<>();

	private ChunkProcessorManager() {
	}

	static void reset() {
		// Processor registration survives server resets; activation is synchronized by each consumer.
	}

	static void registerChunkProcessor(String processorId, MadokuChunkManager.ChunkProcessor processor) {
		String normalizedId = normalize(processorId);
		if (normalizedId.isBlank() || processor == null) {
			return;
		}
		PROCESSORS.put(normalizedId, processor);
		ACTIVE_PROCESSOR_IDS.add(normalizedId);
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
	}

	static void dispatchRandomPosition(ServerLevel level, BlockPos position, RandomSource random) {
		if (level == null || position == null || !ChunkConfigManager.isChunkSystemEnabled()) {
			return;
		}
		for (String processorId : new ArrayList<>(ACTIVE_PROCESSOR_IDS)) {
			MadokuChunkManager.ChunkProcessor processor = PROCESSORS.get(processorId);
			if (processor != null && processor.acceptsWorld(level)) {
				processor.handleRandomPosition(level, position, random);
			}
		}
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase();
	}
}

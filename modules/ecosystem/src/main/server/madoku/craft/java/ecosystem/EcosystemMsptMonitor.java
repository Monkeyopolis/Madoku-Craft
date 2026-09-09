package madoku.craft.java.ecosystem;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Low-frequency performance diagnostics for the ecosystem and the vanilla
 * chunk-tick path that drives it.
 *
 * The monitor aggregates on the server thread and emits one report per
 * second. It deliberately does not log individual chunks or random samples.
 */
public final class EcosystemMsptMonitor {
	private static final Logger LOGGER = LoggerFactory.getLogger(EcosystemMsptMonitor.class);
	private static final int REPORT_INTERVAL_TICKS = 20;
	private static final double NANOS_PER_MILLISECOND = 1_000_000.0d;

	private static final WindowStats WINDOW = new WindowStats();
	private static final ThreadLocal<ChunkTickSample> ACTIVE_CHUNK_TICK = new ThreadLocal<>();

	private EcosystemMsptMonitor() {
	}

	public static void initialize() {
		reset();
	}

	public static void reset() {
		WINDOW.clear();
		ACTIVE_CHUNK_TICK.remove();
	}

	public static void beginChunkTick(ServerLevel level, LevelChunk chunk, int randomTickSpeed) {
		if (level == null || chunk == null) {
			return;
		}
		ACTIVE_CHUNK_TICK.set(new ChunkTickSample(
			level,
			randomTickSpeed,
			System.nanoTime()
		));
	}

	public static void beginRandomTickPass() {
		ChunkTickSample sample = ACTIVE_CHUNK_TICK.get();
		if (sample == null || sample.randomTickSpeed <= 0 || sample.randomTickStartedNanos != 0L) {
			return;
		}
		sample.randomTickStartedNanos = System.nanoTime();
		sample.randomTickPasses++;
	}

	public static void recordBlockRandomTick() {
		ChunkTickSample sample = ACTIVE_CHUNK_TICK.get();
		if (sample != null) {
			sample.blockRandomTickCalls++;
		}
	}

	public static void recordFluidRandomTick() {
		ChunkTickSample sample = ACTIVE_CHUNK_TICK.get();
		if (sample != null) {
			sample.fluidRandomTickCalls++;
		}
	}

	public static void finishRandomTickPass() {
		ChunkTickSample sample = ACTIVE_CHUNK_TICK.get();
		if (sample == null || sample.randomTickStartedNanos == 0L || sample.randomTickFinished) {
			return;
		}
		sample.randomTickNanos = Math.max(0L, System.nanoTime() - sample.randomTickStartedNanos);
		sample.randomTickFinished = true;
	}

	public static void finishChunkTick() {
		ChunkTickSample sample = ACTIVE_CHUNK_TICK.get();
		if (sample == null) {
			return;
		}

		long finishedNanos = System.nanoTime();
		long chunkNanos = Math.max(0L, finishedNanos - sample.startedNanos);
		long randomNanos = sample.randomTickNanos;
		WINDOW.recordChunk(sample, chunkNanos, randomNanos);
		ACTIVE_CHUNK_TICK.remove();
	}

	public static void recordEcosystemDispatch(ServerLevel level, long elapsedNanos) {
		if (level == null) {
			return;
		}
		WINDOW.recordEcosystem(level, Math.max(0L, elapsedNanos));
	}

	public static void recordEcosystemStage(ServerLevel level, String stage, long elapsedNanos) {
		if (level == null || stage == null || stage.isBlank()) {
			return;
		}
		WINDOW.recordEcosystemStage(level, stage, Math.max(0L, elapsedNanos));
	}

	public static void recordEcosystemOutcome(ServerLevel level, String outcome, long count) {
		if (level == null || outcome == null || outcome.isBlank() || count <= 0L) {
			return;
		}
		WINDOW.recordEcosystemOutcome(outcome, count);
	}

	public static void onServerTick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		WINDOW.completedTicks++;
		if (WINDOW.completedTicks < REPORT_INTERVAL_TICKS) {
			return;
		}

		logReport();
		WINDOW.clear();
	}

	private static void logReport() {
		long ticks = Math.max(1L, WINDOW.completedTicks);
		LOGGER.info(
			"MSPT ticks={} ecosystem={}ms (calls={}, avg={}ms, max={}ms) "
				+ "chunk-ticking={}ms (chunks={}, avg={}ms, max={}ms) "
				+ "random-ticking={}ms (passes={}, block-calls={}, fluid-calls={}, avg-pass={}ms, max-pass={}ms, random-tick-speed={})",
			ticks,
			formatMs(toMs(WINDOW.ecosystemNanos / (double) ticks)),
			WINDOW.ecosystemCalls,
			formatMs(average(WINDOW.ecosystemNanos, WINDOW.ecosystemCalls)),
			formatMs(toMs(WINDOW.maxEcosystemNanos)),
			formatMs(toMs(WINDOW.chunkNanos / (double) ticks)),
			WINDOW.chunkTicks,
			formatMs(average(WINDOW.chunkNanos, WINDOW.chunkTicks)),
			formatMs(toMs(WINDOW.maxChunkNanos)),
			formatMs(toMs(WINDOW.randomNanos / (double) ticks)),
			WINDOW.randomTickPasses,
			WINDOW.blockRandomTickCalls,
			WINDOW.fluidRandomTickCalls,
			formatMs(average(WINDOW.randomNanos, WINDOW.randomTickPasses)),
			formatMs(toMs(WINDOW.maxRandomNanos)),
			WINDOW.randomTickSpeed
		);

		for (Map.Entry<String, DimensionStats> entry : WINDOW.byDimension.entrySet()) {
			DimensionStats stats = entry.getValue();
			LOGGER.info(
				"MSPT dimension={} ecosystem={}ms (calls={}) chunk-ticking={}ms (chunks={}) "
					+ "random-ticking={}ms (passes={}, block-calls={}, fluid-calls={})",
				entry.getKey(),
				formatMs(toMs(stats.ecosystemNanos / (double) ticks)),
				stats.ecosystemCalls,
				formatMs(toMs(stats.chunkNanos / (double) ticks)),
				stats.chunkTicks,
				formatMs(toMs(stats.randomNanos / (double) ticks)),
				stats.randomTickPasses,
				stats.blockRandomTickCalls,
				stats.fluidRandomTickCalls
			);
		}

		for (Map.Entry<String, StageStats> entry : WINDOW.stages.entrySet()) {
			StageStats stats = entry.getValue();
			LOGGER.info(
				"MSPT ecosystem-stage={}={}ms (calls={}, avg={}ms, max={}ms)",
				entry.getKey(),
				formatMs(toMs(stats.nanos / (double) ticks)),
				stats.calls,
				formatMs(average(stats.nanos, stats.calls)),
				formatMs(toMs(stats.maxNanos))
			);
		}

		for (Map.Entry<String, Long> entry : WINDOW.outcomes.entrySet()) {
			LOGGER.info("MSPT ecosystem-outcome={} count={}", entry.getKey(), entry.getValue());
		}
	}

	private static double average(long nanos, long calls) {
		return calls <= 0L ? 0.0d : toMs(nanos / (double) calls);
	}

	private static double toMs(double nanos) {
		return nanos / NANOS_PER_MILLISECOND;
	}

	private static String formatMs(double milliseconds) {
		return String.format(java.util.Locale.ROOT, "%.3f", Math.max(0.0d, milliseconds));
	}

	private static String dimensionId(ServerLevel level) {
		return level == null || level.dimension() == null ? "unknown" : level.dimension().toString();
	}

	private static final class ChunkTickSample {
		private final ServerLevel level;
		private final int randomTickSpeed;
		private final long startedNanos;
		private long randomTickStartedNanos;
		private long randomTickNanos;
		private boolean randomTickFinished;
		private long randomTickPasses;
		private long blockRandomTickCalls;
		private long fluidRandomTickCalls;

		private ChunkTickSample(ServerLevel level, int randomTickSpeed, long startedNanos) {
			this.level = level;
			this.randomTickSpeed = randomTickSpeed;
			this.startedNanos = startedNanos;
		}
	}

	private static final class DimensionStats {
		private long ecosystemNanos;
		private long ecosystemCalls;
		private long chunkNanos;
		private long chunkTicks;
		private long randomNanos;
		private long randomTickPasses;
		private long blockRandomTickCalls;
		private long fluidRandomTickCalls;
	}

	private static final class StageStats {
		private long nanos;
		private long calls;
		private long maxNanos;
	}

	private static final class WindowStats {
		private long completedTicks;
		private long ecosystemNanos;
		private long ecosystemCalls;
		private long maxEcosystemNanos;
		private long chunkNanos;
		private long chunkTicks;
		private long maxChunkNanos;
		private long randomNanos;
		private long randomTickPasses;
		private long maxRandomNanos;
		private long randomTickSpeed;
		private long blockRandomTickCalls;
		private long fluidRandomTickCalls;
		private final Map<String, DimensionStats> byDimension = new LinkedHashMap<>();
		private final Map<String, StageStats> stages = new LinkedHashMap<>();
		private final Map<String, Long> outcomes = new LinkedHashMap<>();

		private void recordChunk(ChunkTickSample sample, long chunkNanos, long randomNanos) {
			String dimension = dimensionId(sample.level);
			DimensionStats stats = byDimension.computeIfAbsent(dimension, ignored -> new DimensionStats());
			chunkTicks++;
			this.chunkNanos += chunkNanos;
			maxChunkNanos = Math.max(maxChunkNanos, chunkNanos);
			stats.chunkTicks++;
			stats.chunkNanos += chunkNanos;

			if (sample.randomTickStartedNanos != 0L) {
				randomTickPasses += sample.randomTickPasses;
				this.randomNanos += randomNanos;
				maxRandomNanos = Math.max(maxRandomNanos, randomNanos);
				randomTickSpeed += Math.max(0, sample.randomTickSpeed);
				blockRandomTickCalls += sample.blockRandomTickCalls;
				fluidRandomTickCalls += sample.fluidRandomTickCalls;
				stats.randomTickPasses += sample.randomTickPasses;
				stats.randomNanos += randomNanos;
				stats.blockRandomTickCalls += sample.blockRandomTickCalls;
				stats.fluidRandomTickCalls += sample.fluidRandomTickCalls;
			}
		}

		private void recordEcosystem(ServerLevel level, long elapsedNanos) {
			String dimension = dimensionId(level);
			DimensionStats stats = byDimension.computeIfAbsent(dimension, ignored -> new DimensionStats());
			ecosystemCalls++;
			ecosystemNanos += elapsedNanos;
			maxEcosystemNanos = Math.max(maxEcosystemNanos, elapsedNanos);
			stats.ecosystemCalls++;
			stats.ecosystemNanos += elapsedNanos;
		}

		private void recordEcosystemStage(ServerLevel level, String stage, long elapsedNanos) {
			StageStats stats = stages.computeIfAbsent(stage, ignored -> new StageStats());
			stats.nanos += elapsedNanos;
			stats.calls++;
			stats.maxNanos = Math.max(stats.maxNanos, elapsedNanos);
		}

		private void recordEcosystemOutcome(String outcome, long count) {
			outcomes.merge(outcome, count, Long::sum);
		}

		private void clear() {
			completedTicks = 0L;
			ecosystemNanos = 0L;
			ecosystemCalls = 0L;
			maxEcosystemNanos = 0L;
			chunkNanos = 0L;
			chunkTicks = 0L;
			maxChunkNanos = 0L;
			randomNanos = 0L;
			randomTickPasses = 0L;
			maxRandomNanos = 0L;
			randomTickSpeed = 0L;
			blockRandomTickCalls = 0L;
			fluidRandomTickCalls = 0L;
			byDimension.clear();
			stages.clear();
			outcomes.clear();
		}
	}
}

package madoku.craft.java.debug;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Temporary INFO-level server tick profiler.
 *
 * <p>This deliberately stays enabled while the class is present. It is intended to be removed
 * after the source of the MSPT spike has been found. The normal report is emitted once per second;
 * ticks at or above 50 ms also receive a detailed per-phase report.</p>
 */
public final class MadokuMsptDebug {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuMsptDebug.class);
	private static final long SLOW_TICK_NANOS = 50_000_000L;
	private static final long SLOW_OPERATION_NANOS = 500_000L;
	private static final int REPORT_INTERVAL_TICKS = 20;
	private static final int MAX_REPORTED_PHASES = 40;
	private static final int MAX_REPORTED_DETAILS = 24;

	private static final ThreadLocal<Deque<Section>> SECTION_STACK = ThreadLocal.withInitial(ArrayDeque::new);
	private static Frame currentFrame;
	private static Window window = new Window();

	private MadokuMsptDebug() {
	}

	public static void initialize() {
		LOGGER.info("[MSPT DEBUG] Enabled. Reports will be emitted every {} server ticks; slow tick threshold={} ms.",
			REPORT_INTERVAL_TICKS, formatMs(SLOW_TICK_NANOS));
	}

	public static void onServerStopped() {
		currentFrame = null;
		window = new Window();
		SECTION_STACK.remove();
	}

	/** Starts timing the complete processPacketsAndTick call, including packet processing. */
	public static void beginFrame(MinecraftServer server) {
		if (currentFrame != null) {
			LOGGER.info("[MSPT DEBUG] Replacing an unfinished frame before tick {}.", server.getTickCount() + 1);
			SECTION_STACK.get().clear();
		}

		int levelCount = server.levelKeys().size();
		currentFrame = new Frame(server.getTickCount() + 1L, server.getPlayerCount(), levelCount);
		beginSection("server.frame", null);
	}

	/** Finishes the complete processPacketsAndTick measurement and emits INFO diagnostics. */
	public static void endFrame(MinecraftServer server) {
		Frame frame = currentFrame;
		if (frame == null) {
			return;
		}

		Deque<Section> stack = SECTION_STACK.get();
		while (!stack.isEmpty() && stack.peek().frame == frame) {
			Section section = stack.peek();
			endSection();
			if ("server.frame".equals(section.label)) {
				break;
			}
		}

		frame.elapsedNanos = Math.max(0L, System.nanoTime() - frame.startedNanos);
		currentFrame = null;
		window.add(frame);

		if (frame.elapsedNanos >= SLOW_TICK_NANOS) {
			logSlowFrame(frame);
		}
		if (window.ticks >= REPORT_INTERVAL_TICKS) {
			logWindow(server, window);
			window = new Window();
		}
	}

	public static void beginServerTick() {
		beginSection("server.tick", null);
	}

	public static void endServerTick() {
		endSection();
	}

	public static void beginSection(String label) {
		beginSection(label, null);
	}

	private static void beginSection(String label, Object detailSource) {
		Frame frame = currentFrame;
		if (frame == null) {
			return;
		}
		SECTION_STACK.get().push(new Section(frame, label, detailSource, System.nanoTime()));
	}

	public static void endSection() {
		Deque<Section> stack = SECTION_STACK.get();
		if (stack.isEmpty()) {
			return;
		}

		Section section = stack.pop();
		long elapsedNanos = Math.max(0L, System.nanoTime() - section.startedNanos);
		section.frame.record(section.label, elapsedNanos);
		if (section.detailSource != null && elapsedNanos >= SLOW_OPERATION_NANOS) {
			section.frame.slowDetails.add(new SlowDetail(
				section.label,
				elapsedNanos,
				describeDetail(section.detailSource)
			));
		}
	}

	public static void measure(String label, MinecraftServer server, Consumer<MinecraftServer> action) {
		beginSection(label);
		try {
			action.accept(server);
		} finally {
			endSection();
		}
	}

	public static void beginLevelSection(ServerLevel level, String category) {
		beginSection("vanilla.level[" + dimensionId(level) + "]." + category);
	}

	public static void beginLevelSection(ServerLevel level, String category, Object detailSource) {
		beginSection("vanilla.level[" + dimensionId(level) + "]." + category, detailSource);
	}

	public static void beginEntity(ServerLevel level, Entity entity) {
		String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
		beginLevelSection(level, "entities." + type, entity);
	}

	public static void beginScheduledBlock(ServerLevel level, Block block, net.minecraft.core.BlockPos pos) {
		String type = BuiltInRegistries.BLOCK.getKey(block).toString();
		beginLevelSection(level, "scheduled_blocks." + type, pos);
	}

	public static void beginScheduledFluid(ServerLevel level, Fluid fluid, net.minecraft.core.BlockPos pos) {
		String type = BuiltInRegistries.FLUID.getKey(fluid).toString();
		beginLevelSection(level, "scheduled_fluids." + type, pos);
	}

	public static void beginBlockEntity(ServerLevel level, TickingBlockEntity blockEntity) {
		String type = blockEntity.getType();
		if (type == null || type.isBlank()) {
			type = "unknown";
		}
		beginLevelSection(level, "block_entities." + type, blockEntity);
	}

	public static void beginRandomChunk(ServerLevel level, LevelChunk chunk) {
		beginLevelSection(level, "random_chunk", chunk);
	}

	private static String dimensionId(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	private static String describeDetail(Object detailSource) {
		if (detailSource instanceof Entity entity) {
			return "entityId=" + entity.getId() + " uuid=" + entity.getUUID() + " pos=" + entity.blockPosition();
		}
		if (detailSource instanceof TickingBlockEntity blockEntity) {
			return "pos=" + blockEntity.getPos();
		}
		if (detailSource instanceof LevelChunk chunk) {
			return "chunk=" + chunk.getPos();
		}
		return "pos=" + detailSource;
	}

	private static void logSlowFrame(Frame frame) {
		LOGGER.info("[MSPT DEBUG] SLOW TICK tick={} frame={} ms players={} levels={}",
			frame.tick, formatMs(frame.elapsedNanos), frame.players, frame.levels);
		logStats("[MSPT DEBUG] tick=" + frame.tick + " phase", frame.stats, MAX_REPORTED_PHASES);

		frame.slowDetails.sort(Comparator.comparingLong((SlowDetail detail) -> detail.elapsedNanos).reversed());
		int limit = Math.min(MAX_REPORTED_DETAILS, frame.slowDetails.size());
		for (int index = 0; index < limit; index++) {
			SlowDetail detail = frame.slowDetails.get(index);
			LOGGER.info("[MSPT DEBUG] tick={} slow-operation {}={} ms ({})",
				frame.tick, detail.label, formatMs(detail.elapsedNanos), detail.description);
		}
	}

	private static void logWindow(MinecraftServer server, Window report) {
		LOGGER.info("[MSPT DEBUG] {}-tick window avg={} ms max={} ms serverAverage={} ms players={} levels={}",
			report.ticks,
			formatMs(report.totalFrameNanos / report.ticks),
			formatMs(report.maxFrameNanos),
			formatMs(server.getAverageTickTimeNanos()),
			report.lastPlayers,
			report.lastLevels);
		logStats("[MSPT DEBUG] window phase", report.stats, MAX_REPORTED_PHASES);
	}

	private static void logStats(String prefix, Map<String, Stat> stats, int limit) {
		List<Map.Entry<String, Stat>> sorted = new ArrayList<>(stats.entrySet());
		sorted.sort(Map.Entry.<String, Stat>comparingByValue(
			Comparator.comparingLong((Stat stat) -> stat.totalNanos).reversed()
		));
		int logged = 0;
		for (Map.Entry<String, Stat> entry : sorted) {
			if (logged++ >= limit) {
				break;
			}
			Stat stat = entry.getValue();
			LOGGER.info("{} {} total={} ms avg={} ms max={} ms calls={}",
				prefix,
				entry.getKey(),
				formatMs(stat.totalNanos),
				formatMs(stat.totalNanos / Math.max(1L, stat.calls)),
				formatMs(stat.maxNanos),
				stat.calls);
		}
	}

	private static String formatMs(long nanos) {
		return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
	}

	private static final class Section {
		private final Frame frame;
		private final String label;
		private final Object detailSource;
		private final long startedNanos;

		private Section(Frame frame, String label, Object detailSource, long startedNanos) {
			this.frame = frame;
			this.label = label;
			this.detailSource = detailSource;
			this.startedNanos = startedNanos;
		}
	}

	private static final class Frame {
		private final long tick;
		private final int players;
		private final int levels;
		private final long startedNanos;
		private final Map<String, Stat> stats = new HashMap<>();
		private final List<SlowDetail> slowDetails = new ArrayList<>();
		private long elapsedNanos;

		private Frame(long tick, int players, int levels) {
			this.tick = tick;
			this.players = players;
			this.levels = levels;
			this.startedNanos = System.nanoTime();
		}

		private void record(String label, long elapsedNanos) {
			stats.computeIfAbsent(label, ignored -> new Stat()).add(elapsedNanos);
		}
	}

	private static final class Window {
		private int ticks;
		private long totalFrameNanos;
		private long maxFrameNanos;
		private int lastPlayers;
		private int lastLevels;
		private final Map<String, Stat> stats = new HashMap<>();

		private void add(Frame frame) {
			ticks++;
			totalFrameNanos += frame.elapsedNanos;
			maxFrameNanos = Math.max(maxFrameNanos, frame.elapsedNanos);
			lastPlayers = frame.players;
			lastLevels = frame.levels;
			for (Map.Entry<String, Stat> entry : frame.stats.entrySet()) {
				stats.computeIfAbsent(entry.getKey(), ignored -> new Stat()).add(entry.getValue());
			}
		}
	}

	private static final class Stat {
		private long calls;
		private long totalNanos;
		private long maxNanos;

		private void add(long elapsedNanos) {
			calls++;
			totalNanos += elapsedNanos;
			maxNanos = Math.max(maxNanos, elapsedNanos);
		}

		private void add(Stat other) {
			calls += other.calls;
			totalNanos += other.totalNanos;
			maxNanos = Math.max(maxNanos, other.maxNanos);
		}
	}

	private record SlowDetail(String label, long elapsedNanos, String description) {
	}
}

package madoku.craft.debug;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.config.JsonFormatBuilder;
import madoku.craft.config.JsonManagerSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MadokuDebug {
	private static final Logger LOGGER = LoggerFactory.getLogger("Debug");
	private static final String DEBUG_CONFIG_FOLDER_NAME = "madoku-craft-debug";
	private static final String GROUP_ENABLED_KEY = "enabled";
	private static final int MAX_RECENT_EVENTS = 512;
	private static final Object BUFFER_LOCK = new Object();

	private static final Deque<String> RECENT_EVENTS = new ArrayDeque<>(MAX_RECENT_EVENTS);
	private static final Map<String, GroupConfig> GROUP_CONFIG_CACHE = new ConcurrentHashMap<>();

	private MadokuDebug() {
	}

	public static void initialize() {
		GROUP_CONFIG_CACHE.clear();
		resetSession();
		try {
			Files.createDirectories(resolveRootDirectory());
		} catch (IOException exception) {
			LOGGER.error("Failed to initialize MadokuDebug config root.", exception);
		}
	}

	public static void resetSession() {
		synchronized (BUFFER_LOCK) {
			RECENT_EVENTS.clear();
		}
	}

	public static boolean shouldEmit(String mainSystem, String subSystem, String group) {
		return getGroupConfig(mainSystem, subSystem, group).enabled();
	}

	public static EventBuilder event(String metricId, String mainSystem, String subSystem, String group) {
		GroupConfig config = getGroupConfig(mainSystem, subSystem, group);
		return new EventBuilder(metricId, config);
	}

	public static List<String> dumpRecent(int maxEntries) {
		synchronized (BUFFER_LOCK) {
			int limit = Math.max(0, maxEntries);
			int skip = Math.max(0, RECENT_EVENTS.size() - limit);
			List<String> lines = new ArrayList<>(Math.max(0, RECENT_EVENTS.size() - skip));
			int index = 0;
			for (String event : RECENT_EVENTS) {
				if (index++ < skip) {
					continue;
				}
				lines.add(event);
			}
			return lines;
		}
	}

	private static void emit(DebugEvent debugEvent) {
		if (debugEvent == null || !debugEvent.groupConfig.enabled()) {
			return;
		}

		String formatted = format(debugEvent);
		synchronized (BUFFER_LOCK) {
			if (RECENT_EVENTS.size() >= MAX_RECENT_EVENTS) {
				RECENT_EVENTS.pollFirst();
			}
			RECENT_EVENTS.addLast(formatted);
		}
		LOGGER.info("{}", formatted);
	}

	private static String format(DebugEvent debugEvent) {
		String tickText = debugEvent.tick >= 0L ? Long.toString(debugEvent.tick) : "?";
		String worldSuffix = debugEvent.world.isBlank() ? "" : " @" + debugEvent.world;
		StringBuilder builder = new StringBuilder(256);
		builder.append("[")
			.append(tickText)
			.append("][")
			.append(debugEvent.side.label)
			.append("][")
			.append(debugEvent.groupConfig.pathLabel())
			.append("][")
			.append(debugEvent.metricId)
			.append("] ")
			.append(debugEvent.subject)
			.append(worldSuffix)
			.append('\n')
			.append("  ");

		boolean firstField = true;
		for (Map.Entry<String, String> entry : debugEvent.fields.entrySet()) {
			if (!firstField) {
				builder.append("  | ");
			}
			builder.append(entry.getKey()).append(": ").append(entry.getValue());
			firstField = false;
		}

		if (!debugEvent.details.isBlank()) {
			if (!firstField) {
				builder.append("  | ");
			}
			builder.append("details: ").append(debugEvent.details);
			firstField = false;
		}

		if (firstField) {
			builder.append("details: (none)");
		}

		return builder.toString();
	}

	private static GroupConfig getGroupConfig(String mainSystem, String subSystem, String group) {
		String normalizedMain = normalizePathPart(mainSystem, "main system");
		String normalizedSub = normalizePathPart(subSystem, "sub system");
		String normalizedGroup = normalizePathPart(group, "group");
		String cacheKey = normalizedMain + "/" + normalizedSub + "/" + normalizedGroup;
		return GROUP_CONFIG_CACHE.computeIfAbsent(
			cacheKey,
			ignored -> loadGroupConfig(normalizedMain, normalizedSub, normalizedGroup)
		);
	}

	private static GroupConfig loadGroupConfig(String mainSystem, String subSystem, String group) {
		Path file = resolveGroupFile(mainSystem, subSystem, group);
		JsonObject source = readOrCreateGroupFile(file);
		boolean enabled = getBoolean(source, GROUP_ENABLED_KEY, false);
		return new GroupConfig(mainSystem, subSystem, group, enabled);
	}

	private static JsonObject readOrCreateGroupFile(Path file) {
		if (file == null) {
			return defaultGroupConfig();
		}

		if (!Files.isRegularFile(file)) {
			JsonObject defaults = defaultGroupConfig();
			try {
				JsonManagerSystem.writeJsonFile(file, defaults);
			} catch (IOException exception) {
				LOGGER.error("Failed to create debug config file at {}.", file, exception);
			}
			return defaults;
		}

		try {
			return JsonManagerSystem.readJsonFile(file);
		} catch (IOException exception) {
			LOGGER.error("Failed to read debug config file at {}.", file, exception);
			return defaultGroupConfig();
		}
	}

	private static JsonObject defaultGroupConfig() {
		return JsonFormatBuilder.object()
			.put(GROUP_ENABLED_KEY, false)
			.build();
	}

	private static Path resolveRootDirectory() {
		return JsonManagerSystem.getOrCreateGlobalSystemDirectory(DEBUG_CONFIG_FOLDER_NAME);
	}

	private static Path resolveGroupFile(String mainSystem, String subSystem, String group) {
		Path file = resolveRootDirectory()
			.resolve(mainSystem)
			.resolve(subSystem)
			.resolve(group + ".json");
		try {
			Files.createDirectories(file.getParent());
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to create debug config directory: " + file.getParent(), exception);
		}
		return file;
	}

	private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		try {
			return element.getAsBoolean();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static String normalizePathPart(String value, String label) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		normalized = normalized.replace(' ', '-').replace('_', '-').replace('\\', '-').replace('/', '-');
		while (normalized.contains("--")) {
			normalized = normalized.replace("--", "-");
		}
		while (normalized.startsWith("-")) {
			normalized = normalized.substring(1);
		}
		while (normalized.endsWith("-")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		if (normalized.isBlank()) {
			throw new IllegalArgumentException(label + " must not be blank.");
		}
		return normalized;
	}

	public enum Side {
		SERVER("SERVER"),
		CLIENT("CLIENT"),
		UNKNOWN("UNKNOWN");

		private final String label;

		Side(String label) {
			this.label = label;
		}
	}

	public static final class EventBuilder {
		private final String metricId;
		private final GroupConfig groupConfig;
		private final LinkedHashMap<String, String> fields = new LinkedHashMap<>();
		private Side side = Side.UNKNOWN;
		private long tick = -1L;
		private String world = "";
		private String subject = "global";
		private String details = "";

		private EventBuilder(String metricId, GroupConfig groupConfig) {
			this.metricId = normalizeMetric(metricId);
			this.groupConfig = groupConfig == null ? GroupConfig.disabled("unknown", "unknown", "unknown") : groupConfig;
		}

		public EventBuilder tick(long value) {
			this.tick = value;
			return this;
		}

		public EventBuilder side(Side value) {
			this.side = value == null ? Side.UNKNOWN : value;
			return this;
		}

		public EventBuilder world(String value) {
			this.world = normalizeText(value);
			return this;
		}

		public EventBuilder subject(String value) {
			this.subject = normalizeText(value);
			if (this.subject.isBlank()) {
				this.subject = "global";
			}
			return this;
		}

		public EventBuilder field(String name, Object value) {
			String normalizedName = normalizeText(name);
			if (normalizedName.isBlank() || value == null) {
				return this;
			}
			fields.put(normalizedName, normalizeText(String.valueOf(value)));
			return this;
		}

		public EventBuilder details(String value) {
			this.details = normalizeText(value);
			return this;
		}

		public void log() {
			if (!groupConfig.enabled()) {
				return;
			}
			emit(
				new DebugEvent(
					metricId,
					groupConfig,
					side,
					tick,
					world,
					subject,
					Map.copyOf(fields),
					details
				)
			);
		}
	}

	private record GroupConfig(String mainSystem, String subSystem, String group, boolean enabled) {
		private static GroupConfig disabled(String mainSystem, String subSystem, String group) {
			return new GroupConfig(mainSystem, subSystem, group, false);
		}

		private String pathLabel() {
			return mainSystem + "/" + subSystem + "/" + group;
		}
	}

	private record DebugEvent(
		String metricId,
		GroupConfig groupConfig,
		Side side,
		long tick,
		String world,
		String subject,
		Map<String, String> fields,
		String details
	) {
	}

	private static String normalizeMetric(String metricId) {
		String normalized = metricId == null ? "" : metricId.trim().toLowerCase(Locale.ROOT);
		return normalized.isEmpty() ? "other.unknown" : normalized;
	}

	private static String normalizeText(String value) {
		return value == null ? "" : value.trim();
	}
}

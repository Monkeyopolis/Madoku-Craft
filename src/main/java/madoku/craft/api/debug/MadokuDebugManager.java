package madoku.craft.api.debug;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.MadokuAPIManager;
import madoku.craft.api.metadata.MadokuMetaDataManager;
import madoku.craft.api.json.MadokuJSONManager;
import madoku.craft.api.json.JSONFormatManager;
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
import java.lang.StackWalker;

public final class MadokuDebugManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("Debug");
	private static final String DEBUG_CONFIG_FOLDER_NAME = MadokuAPIManager.API_FOLDER_NAME + "/madoku-debug";
	private static final String ENTRY_ENABLED_KEY = "enabled";
	private static final int MAX_RECENT_EVENTS = 512;
	private static final Object BUFFER_LOCK = new Object();
	private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

	private static final Deque<String> RECENT_EVENTS = new ArrayDeque<>(MAX_RECENT_EVENTS);
	private static final Map<String, JsonObject> MAIN_CONFIG_CACHE = new ConcurrentHashMap<>();

	private MadokuDebugManager() {
	}

	public static void initialize() {
		MAIN_CONFIG_CACHE.clear();
		resetSession();
		try {
			Files.createDirectories(resolveRootDirectory());
		} catch (IOException exception) {
			LOGGER.error("Failed to initialize MadokuDebugManager config root.", exception);
		}
	}

	public static void resetSession() {
		synchronized (BUFFER_LOCK) {
			RECENT_EVENTS.clear();
		}
	}

	public static boolean shouldEmit(String mainSystem, String subSystem, String group, String entry) {
		return resolveGroupConfig(mainSystem, subSystem, group, entry).enabled();
	}

	public static boolean shouldEmit(String mainSystem, String subSystem, String entry) {
		return resolveDirectEntryConfig(mainSystem, subSystem, entry).enabled();
	}

	public static String mainSystemFileName(String mainSystem) {
		String normalized = normalizePathPart(mainSystem, "main system");
		return normalized.startsWith("madoku-") ? normalized + ".json" : "madoku-" + normalized + ".json";
	}

	public static EventBuilder event(String metricId, String mainSystem, String subSystem, String group, String entry) {
		GroupConfig config = resolveGroupConfig(mainSystem, subSystem, group, entry);
		return new EventBuilder(metricId, config);
	}

	public static EventBuilder event(String metricId, String mainSystem, String subSystem, String entry) {
		GroupConfig config = resolveDirectEntryConfig(mainSystem, subSystem, entry);
		return new EventBuilder(metricId, config);
	}

	public static void bootstrapMainSystem(MadokuMetaDataManager.MainSystemMetadata mainSystem) {
		if (mainSystem == null) {
			return;
		}
		loadMainSystemConfig(mainSystem.mainSystem(), mainSystem);
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

	private static GroupConfig resolveGroupConfig(String mainSystem, String subSystem, String group, String entry) {
		String normalizedMain = normalizePathPart(mainSystem, "main system");
		String normalizedSub = normalizePathPart(subSystem, "sub system");
		String normalizedGroup = normalizePathPart(group, "group");
		String normalizedEntry = normalizePathPart(entry, "entry");
		MadokuMetaDataManager.MainSystemMetadata hierarchy = MadokuMetaDataManager.getMainSystem(normalizedMain);
		if (hierarchy == null
			|| !hierarchy.containsSubSystem(normalizedSub)
			|| !hierarchy.containsGroup(normalizedSub, normalizedGroup)
			|| !hierarchy.containsGroupEntry(normalizedSub, normalizedGroup, normalizedEntry)) {
			return GroupConfig.disabled(normalizedMain, normalizedSub, normalizedGroup, normalizedEntry);
		}
		JsonObject mainConfig = loadMainSystemConfig(normalizedMain, hierarchy);
		if (!getBoolean(mainConfig, ENTRY_ENABLED_KEY, false)) {
			return GroupConfig.disabled(normalizedMain, normalizedSub, normalizedGroup, normalizedEntry);
		}
		JsonObject subSystemConfig = getObject(mainConfig, normalizedSub);
		if (!getBoolean(subSystemConfig, ENTRY_ENABLED_KEY, false)) {
			return GroupConfig.disabled(normalizedMain, normalizedSub, normalizedGroup, normalizedEntry);
		}
		JsonObject groupConfig = getObject(subSystemConfig, normalizedGroup);
		if (!getBoolean(groupConfig, ENTRY_ENABLED_KEY, false)) {
			return GroupConfig.disabled(normalizedMain, normalizedSub, normalizedGroup, normalizedEntry);
		}
		JsonObject entryConfig = getObject(groupConfig, normalizedEntry);
		if (!getBoolean(entryConfig, ENTRY_ENABLED_KEY, false)) {
			return GroupConfig.disabled(normalizedMain, normalizedSub, normalizedGroup, normalizedEntry);
		}
		return new GroupConfig(normalizedMain, normalizedSub, normalizedGroup, normalizedEntry, true);
	}

	private static GroupConfig resolveDirectEntryConfig(String mainSystem, String subSystem, String entry) {
		String normalizedMain = normalizePathPart(mainSystem, "main system");
		String normalizedSub = normalizePathPart(subSystem, "sub system");
		String normalizedEntry = normalizePathPart(entry, "entry");
		MadokuMetaDataManager.MainSystemMetadata hierarchy = MadokuMetaDataManager.getMainSystem(normalizedMain);
		if (hierarchy == null
			|| !hierarchy.containsSubSystem(normalizedSub)
			|| !hierarchy.containsDirectEntry(normalizedSub, normalizedEntry)) {
			return GroupConfig.disabled(normalizedMain, normalizedSub, "", normalizedEntry);
		}
		JsonObject mainConfig = loadMainSystemConfig(normalizedMain, hierarchy);
		if (!getBoolean(mainConfig, ENTRY_ENABLED_KEY, false)) {
			return GroupConfig.disabled(normalizedMain, normalizedSub, "", normalizedEntry);
		}
		JsonObject subSystemConfig = getObject(mainConfig, normalizedSub);
		if (!getBoolean(subSystemConfig, ENTRY_ENABLED_KEY, false)) {
			return GroupConfig.disabled(normalizedMain, normalizedSub, "", normalizedEntry);
		}
		JsonObject entryConfig = getObject(subSystemConfig, normalizedEntry);
		if (!getBoolean(entryConfig, ENTRY_ENABLED_KEY, false)) {
			return GroupConfig.disabled(normalizedMain, normalizedSub, "", normalizedEntry);
		}
		return new GroupConfig(normalizedMain, normalizedSub, "", normalizedEntry, true);
	}

	private static JsonObject loadMainSystemConfig(String mainSystem, MadokuMetaDataManager.MainSystemMetadata hierarchy) {
		return MAIN_CONFIG_CACHE.computeIfAbsent(
			mainSystem,
			ignored -> {
				Path file = resolveMainSystemFile(mainSystem);
				JsonObject defaults = MadokuMetaDataManager.createDefaultDebugConfig(hierarchy);
				try {
					return JSONFormatManager.ensureManagedFile(file, defaults);
				} catch (IOException exception) {
					LOGGER.error("Failed to create or read debug config file at {}.", file, exception);
					return defaults;
				}
			}
		);
	}

	private static Path resolveRootDirectory() {
		return MadokuJSONManager.getOrCreateGlobalSystemDirectory(DEBUG_CONFIG_FOLDER_NAME);
	}

	private static Path resolveMainSystemFile(String mainSystem) {
		Path file = resolveRootDirectory().resolve(mainSystemFileName(mainSystem));
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

	private static JsonObject getObject(JsonObject object, String key) {
		if (object == null || key == null || key.isBlank()) {
			return null;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonObject()) {
			return null;
		}
		return element.getAsJsonObject();
	}

	private static String normalizePathPart(String value, String label) {
		String normalized = value == null ? "" : value.trim();
		StringBuilder builder = new StringBuilder(normalized.length() + 8);
		char previous = 0;
		for (int index = 0; index < normalized.length(); index++) {
			char current = normalized.charAt(index);
			if (Character.isUpperCase(current) && index > 0 && (Character.isLowerCase(previous) || Character.isDigit(previous))) {
				builder.append('-');
			}
			builder.append(Character.toLowerCase(current));
			previous = current;
		}
		normalized = builder.toString();
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

	public static String resolveCallerMethodName() {
		return resolveCallerMethodName(1);
	}

	public static String resolveCallerMethodName(int framesToSkip) {
		int skip = Math.max(0, framesToSkip);
		return STACK_WALKER.walk(stream -> stream
			.filter(frame -> frame.getDeclaringClass() != MadokuDebugManager.class)
			.skip(skip)
			.findFirst()
			.map(StackWalker.StackFrame::getMethodName)
			.map(name -> normalizePathPart(name, "entry"))
			.orElse("unknown"));
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
			this.groupConfig = groupConfig == null ? GroupConfig.disabled("unknown", "unknown", "unknown", "unknown") : groupConfig;
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

	private record GroupConfig(String mainSystem, String subSystem, String group, String entry, boolean enabled) {
		private static GroupConfig disabled(String mainSystem, String subSystem, String group, String entry) {
			return new GroupConfig(mainSystem, subSystem, group, entry, false);
		}

		private String pathLabel() {
			if (group == null || group.isBlank()) {
				return mainSystem + "/" + subSystem + "/" + entry;
			}
			return mainSystem + "/" + subSystem + "/" + group + "/" + entry;
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

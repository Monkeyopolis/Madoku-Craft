package madoku.craft.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.config.StaticJsonSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MadokuDebug {
	private static final Logger LOGGER = LoggerFactory.getLogger("Debug");
	private static final String DEBUG_CONFIG_FOLDER_NAME = "madoku-craft-debug";
	private static final String DEBUG_CONFIG_FILE_NAME = "madoku-debug";
	private static final int MAX_RECENT_EVENTS = 512;
	private static final Object BUFFER_LOCK = new Object();

	private static final Deque<String> RECENT_EVENTS = new ArrayDeque<>(MAX_RECENT_EVENTS);
	private static final Set<Domain> DISABLED_DOMAINS = ConcurrentHashMap.newKeySet();
	private static final Set<String> DISABLED_METRIC_PATTERNS = ConcurrentHashMap.newKeySet();

	private static volatile boolean enabled = false;

	private MadokuDebug() {
	}

	public static void initialize() {
		resetFilters();
		JsonObject defaults = createDefaultConfig();

		try {
			Path directory = StaticJsonSystem.getOrCreateGlobalSystemDirectory(DEBUG_CONFIG_FOLDER_NAME);
			Path configFile = resolveJsonFile(directory, DEBUG_CONFIG_FILE_NAME);
			JsonObject normalized = StaticJsonSystem.ensureManagedFile(configFile, defaults);
			loadConfig(normalized, defaults);
		} catch (IOException | RuntimeException exception) {
			loadConfig(defaults, defaults);
			LOGGER.error("Failed to load MadokuDebug config; using defaults.", exception);
		}
	}

	public static void resetSession() {
		synchronized (BUFFER_LOCK) {
			RECENT_EVENTS.clear();
		}
	}

	public static void resetFilters() {
		DISABLED_DOMAINS.clear();
		DISABLED_METRIC_PATTERNS.clear();
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
	}

	public static boolean isDomainEnabled(Domain domain) {
		return !DISABLED_DOMAINS.contains(normalizeDomain(domain));
	}

	public static void setDomainEnabled(Domain domain, boolean value) {
		Domain normalizedDomain = normalizeDomain(domain);
		if (value) {
			DISABLED_DOMAINS.remove(normalizedDomain);
		} else {
			DISABLED_DOMAINS.add(normalizedDomain);
		}
	}

	public static void setMetricEnabled(String metricPattern, boolean value) {
		String normalizedPattern = normalizeMetricPattern(metricPattern);
		if (normalizedPattern.isEmpty()) {
			return;
		}

		if (value) {
			DISABLED_METRIC_PATTERNS.remove(normalizedPattern);
		} else {
			DISABLED_METRIC_PATTERNS.add(normalizedPattern);
		}
	}

	public static boolean shouldEmit(Domain domain, String metricId) {
		if (!enabled) {
			return false;
		}

		if (DISABLED_DOMAINS.contains(normalizeDomain(domain))) {
			return false;
		}

		String normalizedMetricId = normalizeMetric(metricId);
		for (String pattern : DISABLED_METRIC_PATTERNS) {
			if (metricPatternMatches(normalizedMetricId, pattern)) {
				return false;
			}
		}
		return true;
	}

	public static EventBuilder event(String metricId, Domain domain) {
		return new EventBuilder(metricId, domain);
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
		if (debugEvent == null || !shouldEmit(debugEvent.domain, debugEvent.metricId)) {
			return;
		}

		String formatted = format(debugEvent);
		synchronized (BUFFER_LOCK) {
			if (RECENT_EVENTS.size() >= MAX_RECENT_EVENTS) {
				RECENT_EVENTS.pollFirst();
			}
			RECENT_EVENTS.addLast(formatted);
		}
		LOGGER.info("{}{}", formatted, System.lineSeparator());
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

	private static void loadConfig(JsonObject source, JsonObject defaults) {
		enabled = getBoolean(source, "enabled", false);
		applyActiveDomains(getArray(source, "active_domains"), defaults);
		applyDisabledMetrics(getArray(source, "disabled_metrics"));
	}

	private static void applyActiveDomains(JsonArray domains, JsonObject defaults) {
		for (Domain domain : Domain.values()) {
			setDomainEnabled(domain, false);
		}

		boolean anyEnabled = false;
		if (domains != null) {
			for (JsonElement element : domains) {
				if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
					continue;
				}
				Domain domain = Domain.fromId(element.getAsString());
				if (domain == null) {
					continue;
				}
				setDomainEnabled(domain, true);
				anyEnabled = true;
			}
		}

		if (!anyEnabled) {
			JsonArray fallbackDomains = getArray(defaults, "active_domains");
			if (fallbackDomains != null) {
				for (JsonElement element : fallbackDomains) {
					if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
						continue;
					}
					Domain domain = Domain.fromId(element.getAsString());
					if (domain != null) {
						setDomainEnabled(domain, true);
						anyEnabled = true;
					}
				}
			}
		}

				if (!anyEnabled) {
					setDomainEnabled(Domain.SCHEDULER, true);
					setDomainEnabled(Domain.SLEEP, true);
					setDomainEnabled(Domain.HEALTH, true);
					setDomainEnabled(Domain.HUNGER, true);
					setDomainEnabled(Domain.MOB, true);
					setDomainEnabled(Domain.FARMING, true);
				}
			}

	private static void applyDisabledMetrics(JsonArray patterns) {
		DISABLED_METRIC_PATTERNS.clear();
		if (patterns == null) {
			return;
		}

		for (JsonElement element : patterns) {
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				continue;
			}
			String normalized = normalizeMetricPattern(element.getAsString());
			if (!normalized.isEmpty()) {
				DISABLED_METRIC_PATTERNS.add(normalized);
			}
		}
	}

	private static JsonObject createDefaultConfig() {
		JsonObject root = new JsonObject();
		root.addProperty("enabled", false);
		JsonArray activeDomains = new JsonArray();
		activeDomains.add(Domain.SCHEDULER.id());
			activeDomains.add(Domain.SLEEP.id());
			activeDomains.add(Domain.HEALTH.id());
				activeDomains.add(Domain.HUNGER.id());
				activeDomains.add(Domain.MOB.id());
				activeDomains.add(Domain.FARMING.id());
				activeDomains.add(Domain.SEASON.id());
				root.add("active_domains", activeDomains);
				root.add("disabled_metrics", new JsonArray());
				return root;
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

	private static JsonArray getArray(JsonObject object, String key) {
		if (object == null) {
			return null;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonArray()) {
			return null;
		}
		return element.getAsJsonArray();
	}

	private static Domain normalizeDomain(Domain domain) {
		return domain == null ? Domain.OTHER : domain;
	}

	private static String normalizeMetricPattern(String metricPattern) {
		if (metricPattern == null) {
			return "";
		}
		String normalized = metricPattern.trim().toLowerCase(Locale.ROOT);
		if (normalized.equals("*")) {
			return "";
		}
		return normalized;
	}

	private static String normalizeMetric(String metricId) {
		String normalized = metricId == null ? "" : metricId.trim().toLowerCase(Locale.ROOT);
		return normalized.isEmpty() ? "other.unknown" : normalized;
	}

	private static boolean metricPatternMatches(String metricId, String pattern) {
		if (pattern == null || pattern.isBlank()) {
			return false;
		}
		if (pattern.endsWith("*")) {
			return metricId.startsWith(pattern.substring(0, pattern.length() - 1));
		}
		return metricId.equals(pattern);
	}

	public enum Domain {
		PLAYER("player"),
		MOB("mob"),
		ENTITY("entity"),
		BLOCK_ENTITY("block_entity"),
		BLOCK("block"),
		ITEM("item"),
		UI("ui"),
		SPAWNING("spawning"),
		SCHEDULER("scheduler"),
		HEALTH("health"),
			HUNGER("hunger"),
			NETWORK("network"),
			CLOCK("clock"),
			SLEEP("sleep"),
			FARMING("farming"),
			SEASON("season"),
			WORLD("world"),
			OTHER("other");

		private final String id;

		Domain(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public static Domain fromId(String rawValue) {
			if (rawValue == null) {
				return null;
			}

			String value = rawValue.trim().toLowerCase(Locale.ROOT);
			if (value.isEmpty()) {
				return null;
			}

			return switch (value) {
				case "player", "players" -> PLAYER;
				case "mob", "mobs" -> MOB;
				case "entity", "entities" -> ENTITY;
				case "blockentity", "block_entity", "blockentities", "block_entities", "entityblock", "entityblocks", "entity_block", "entity_blocks" -> BLOCK_ENTITY;
				case "block", "blocks" -> BLOCK;
				case "item", "items" -> ITEM;
				case "ui" -> UI;
				case "spawn", "spawning", "mob_spawning" -> SPAWNING;
				case "scheduler", "schedulers" -> SCHEDULER;
				case "health", "hp" -> HEALTH;
						case "hunger", "food" -> HUNGER;
						case "network", "net" -> NETWORK;
						case "clock", "time_clock" -> CLOCK;
						case "sleep", "sleeping" -> SLEEP;
						case "farming", "farm", "farmland" -> FARMING;
						case "season", "seasons" -> SEASON;
						case "world" -> WORLD;
					case "other", "*" -> OTHER;
				default -> null;
			};
		}
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
		private final Domain domain;
		private final LinkedHashMap<String, String> fields = new LinkedHashMap<>();
		private Side side = Side.UNKNOWN;
		private long tick = -1L;
		private String world = "";
		private String subject = "global";
		private String details = "";

		private EventBuilder(String metricId, Domain domain) {
			this.metricId = normalizeMetric(metricId);
			this.domain = normalizeDomain(domain);
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
			emit(
				new DebugEvent(
					metricId,
					domain,
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

	private record DebugEvent(
		String metricId,
		Domain domain,
		Side side,
		long tick,
		String world,
		String subject,
		Map<String, String> fields,
		String details
	) {
	}

	private static String normalizeText(String value) {
		return value == null ? "" : value.trim();
	}
}

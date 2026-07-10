package madoku.craft.api.metadata;

import com.google.gson.JsonObject;
import madoku.craft.config.JsonFormatBuilder;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MadokuMetaDataManager {
	private static final Map<String, MainSystemMetadata> REGISTERED_MAIN_SYSTEMS = new ConcurrentHashMap<>();

	public static final MainSystemMetadata API = mainSystem(
		"api",
		subSystem("metadata-manager", entriesFromClass(madoku.craft.api.metadata.MadokuMetaDataManager.class)),
		subSystem("debug-manager", entriesFromClass(madoku.craft.api.debug.MadokuDebugManager.class)),
		subSystem(
			"chunk-manager",
			entriesFromClass(madoku.craft.api.chunk.MadokuChunkManager.class),
			group("chunk-discovery-manager", entriesFromClass("madoku.craft.api.chunk.ChunkDiscoveryManager")),
			group("chunk-processor-manager", entriesFromClass("madoku.craft.api.chunk.ChunkProcessorManager"))
		),
		subSystem(
			"time-manager",
			entriesFromClass(madoku.craft.api.time.MadokuTimeManager.class),
			group(
				"time-config-manager",
				entry("initialize")
			),
			group(
				"sleep-manager",
				entry("reset"),
				entry("clear-weather"),
				entry("should-allow-resetting-time"),
				entry("get-tick-increment"),
				entry("on-world-time-advanced")
			)
		)
	);

	public static final MainSystemMetadata CHUNK = mainSystem(
		"chunk",
		subSystem("chunk-manager", entriesFromClass(madoku.craft.api.chunk.MadokuChunkManager.class)),
		subSystem("chunk-discovery-manager", entriesFromClass("madoku.craft.api.chunk.ChunkDiscoveryManager")),
		subSystem("chunk-processor-manager", entriesFromClass("madoku.craft.api.chunk.ChunkProcessorManager"))
	);

	public static final MainSystemMetadata ECOSYSTEM = mainSystem(
		"ecosystem",
		subSystem("ecosystem-manager", entriesFromClass(madoku.craft.ecosystem.MadokuEcosystemManager.class)),
		subSystem("ecosystem-config-manager", entriesFromClass(madoku.craft.ecosystem.EcosystemConfigManager.class)),
		subSystem("ecosystem-seasonal-changes-manager", entriesFromClass(madoku.craft.ecosystem.EcosystemSeasonalChangesManager.class),
			group("seasonal-changes-config-manager", entriesFromClass(madoku.craft.ecosystem.SeasonalChangesConfigManager.class)),
			group("lifecycle", entry("state")), group("processing", entry("chunk"), entry("decision"))),
		subSystem(
			"ecosystem-natural-growth-manager",
			entriesFromClass(madoku.craft.ecosystem.EcosystemNaturalGrowthManager.class)
		),
		subSystem(
			"ecosystem-natural-erosion-manager",
			entriesFromClass(madoku.craft.ecosystem.EcosystemNaturalErosionManager.class)
		),
		subSystem(
			"ecosystem-natural-decay-manager",
			entriesFromClass(madoku.craft.ecosystem.EcosystemNaturalDecayManager.class)
		)
	);

	public static final MainSystemMetadata SEASON = mainSystem(
		"season",
		subSystem("season-manager", entriesFromClass(madoku.craft.api.season.MadokuSeasonManager.class),
			group("season-config-manager", entriesFromClass(madoku.craft.api.season.SeasonConfigManager.class)), group("lifecycle", entry("state"))),
		subSystem("season-biome-climate-manager", entriesFromClass(madoku.craft.api.season.SeasonBiomeClimateManager.class),
			group("biome-climate-config-manager", entriesFromClass(madoku.craft.api.season.BiomeClimateConfigManager.class)), group("lifecycle", entry("state"))),
		subSystem("season-environment-transition-manager", entriesFromClass(madoku.craft.api.season.SeasonEnvironmentTransitionManager.class),
			group("envrionment-transition-config-manager", entriesFromClass(madoku.craft.api.season.EnvrionmentTransitionConfigManager.class)), group("lifecycle", entry("state")))
	);

	public static final MainSystemMetadata ATTRIBUTES = mainSystem(
		"attributes",
		subSystem(
			"health",
			entriesFromClass(madoku.craft.attributes.health.MadokuHealthManager.class),
			group("health-config-manager", entriesFromClass(madoku.craft.attributes.health.HealthConfigManager.class))
		),
		subSystem(
			"hunger",
			entriesFromClass(madoku.craft.attributes.hunger.MadokuHungerManager.class),
			group("hunger-config-manager", entriesFromClass(madoku.craft.attributes.hunger.HungerConfigManager.class))
		),
		subSystem(
			"armor",
			entriesFromClass(madoku.craft.attributes.armor.MadokuArmorManager.class),
			group("armor-config-manager", entriesFromClass(madoku.craft.attributes.armor.ArmorConfigManager.class))
		),
		subSystem(
			"oxygen",
			entriesFromClass(madoku.craft.attributes.oxygen.MadokuOxygenManager.class),
			group("oxygen-config-manager", entriesFromClass(madoku.craft.attributes.oxygen.OxygenConfigManager.class))
		),
		subSystem(
			"luck",
			entriesFromClass(madoku.craft.attributes.luck.MadokuLuckManager.class),
			group("luck-config-manager", entriesFromClass(madoku.craft.attributes.luck.LuckConfigManager.class))
		)
	);

	private MadokuMetaDataManager() {
	}

	public static void initialize() {
		REGISTERED_MAIN_SYSTEMS.clear();
	}

	public static void registerMainSystem(MainSystemMetadata mainSystem) {
		if (mainSystem == null) {
			return;
		}
		REGISTERED_MAIN_SYSTEMS.put(mainSystem.mainSystem(), mainSystem);
	}

	public static MainSystemMetadata getMainSystem(String mainSystem) {
		if (mainSystem == null || mainSystem.isBlank()) {
			return null;
		}
		return REGISTERED_MAIN_SYSTEMS.get(normalizeName(mainSystem, "main system"));
	}

	public static boolean hasMainSystem(String mainSystem) {
		return getMainSystem(mainSystem) != null;
	}

	public static MainSystemMetadata mainSystem(String mainSystem, SubSystemMetadata... subSystems) {
		LinkedHashMap<String, SubSystemMetadata> orderedSubSystems = new LinkedHashMap<>();
		if (subSystems != null) {
			for (SubSystemMetadata subSystem : subSystems) {
				if (subSystem == null) {
					continue;
				}
				orderedSubSystems.put(subSystem.subSystem(), subSystem);
			}
		}
		return new MainSystemMetadata(mainSystem, orderedSubSystems);
	}

	public static SubSystemMetadata subSystem(String name, GroupMetadata... groups) {
		return subSystem(name, List.of(), groups);
	}

	public static SubSystemMetadata subSystem(String name, List<EntryMetadata> entries, GroupMetadata... groups) {
		LinkedHashMap<String, GroupMetadata> orderedGroups = new LinkedHashMap<>();
		LinkedHashMap<String, EntryMetadata> orderedEntries = new LinkedHashMap<>();
		if (entries != null) {
			for (EntryMetadata entry : entries) {
				if (entry == null) {
					continue;
				}
				orderedEntries.put(entry.entry(), entry);
			}
		}
		if (groups != null) {
			for (GroupMetadata group : groups) {
				if (group == null) {
					continue;
				}
				orderedGroups.put(group.group(), group);
			}
		}
		return new SubSystemMetadata(name, orderedEntries, orderedGroups);
	}

	public static SubSystemMetadata subSystem(String name, EntryMetadata... entries) {
		return subSystem(name, entries == null ? List.of() : List.of(entries));
	}

	public static GroupMetadata group(String name, EntryMetadata... entries) {
		LinkedHashMap<String, EntryMetadata> orderedEntries = new LinkedHashMap<>();
		if (entries != null) {
			for (EntryMetadata entry : entries) {
				if (entry == null) {
					continue;
				}
				orderedEntries.put(entry.entry(), entry);
			}
		}
		return new GroupMetadata(name, orderedEntries);
	}

	public static GroupMetadata group(String name, List<EntryMetadata> entries) {
		return group(name, entries == null ? null : entries.toArray(EntryMetadata[]::new));
	}

	public static EntryMetadata entry(String name) {
		return new EntryMetadata(name);
	}

	public static List<EntryMetadata> entriesFromClass(Class<?> type) {
		if (type == null) {
			return List.of();
		}
		LinkedHashSet<String> names = new LinkedHashSet<>();
		for (Method method : type.getDeclaredMethods()) {
			if (method == null
				|| method.isSynthetic()
				|| method.isBridge()
				|| method.getDeclaringClass() == Object.class
				|| Modifier.isPrivate(method.getModifiers())
				|| !Modifier.isStatic(method.getModifiers())) {
				continue;
			}
			names.add(normalizeName(method.getName(), "entry"));
		}
		return names.stream().sorted().map(MadokuMetaDataManager::entry).toList();
	}

	public static List<EntryMetadata> entriesFromClass(String className) {
		if (className == null || className.isBlank()) {
			return List.of();
		}
		try {
			Class<?> type = Class.forName(className.trim());
			return entriesFromClass(type);
		} catch (ClassNotFoundException exception) {
			return List.of();
		}
	}

	public static JsonObject createDefaultDebugConfig(MainSystemMetadata mainSystem) {
		return mainSystem == null ? JsonFormatBuilder.object().build() : mainSystem.toDebugConfigJson();
	}

	public record MainSystemMetadata(String mainSystem, Map<String, SubSystemMetadata> subSystems) {
		public MainSystemMetadata {
			mainSystem = normalizeName(mainSystem, "main system");
			subSystems = subSystems == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(subSystems));
		}

		public Map<String, List<String>> subSystemGroups() {
			LinkedHashMap<String, List<String>> groups = new LinkedHashMap<>();
			for (Map.Entry<String, SubSystemMetadata> entry : subSystems.entrySet()) {
				groups.put(entry.getKey(), entry.getValue().groupNames());
			}
			return Collections.unmodifiableMap(groups);
		}

		public boolean containsSubSystem(String subSystem) {
			return subSystems.containsKey(normalizeName(subSystem, "sub system"));
		}

		public boolean containsGroup(String subSystem, String group) {
			SubSystemMetadata subSystemMetadata = subSystems.get(normalizeName(subSystem, "sub system"));
			return subSystemMetadata != null && subSystemMetadata.containsGroup(group);
		}

		public boolean containsDirectEntry(String subSystem, String entry) {
			SubSystemMetadata subSystemMetadata = subSystems.get(normalizeName(subSystem, "sub system"));
			return subSystemMetadata != null && subSystemMetadata.containsDirectEntry(entry);
		}

		public boolean containsGroupEntry(String subSystem, String group, String entry) {
			SubSystemMetadata subSystemMetadata = subSystems.get(normalizeName(subSystem, "sub system"));
			return subSystemMetadata != null && subSystemMetadata.containsGroupEntry(group, entry);
		}

		public JsonObject toDebugConfigJson() {
			JsonFormatBuilder.ObjectBuilder root = JsonFormatBuilder.object();
			root.put("enabled", false);
			for (Map.Entry<String, SubSystemMetadata> entry : subSystems.entrySet()) {
				root.put(entry.getKey(), entry.getValue().toDebugConfigJson());
			}
			return root.build();
		}
	}

	public record SubSystemMetadata(String subSystem, Map<String, EntryMetadata> entries, Map<String, GroupMetadata> groups) {
		public SubSystemMetadata {
			subSystem = normalizeName(subSystem, "sub system");
			entries = entries == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(entries));
			groups = groups == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(groups));
		}

		public List<String> entryNames() {
			return List.copyOf(entries.keySet());
		}

		public List<String> groupNames() {
			return List.copyOf(groups.keySet());
		}

		public boolean containsDirectEntry(String entry) {
			return entries.containsKey(normalizeName(entry, "entry"));
		}

		public boolean containsGroup(String group) {
			return groups.containsKey(normalizeName(group, "group"));
		}

		public boolean containsGroupEntry(String group, String entry) {
			GroupMetadata groupMetadata = groups.get(normalizeName(group, "group"));
			return groupMetadata != null && groupMetadata.containsEntry(entry);
		}

		private JsonObject toDebugConfigJson() {
			JsonFormatBuilder.ObjectBuilder builder = JsonFormatBuilder.object().put("enabled", false);
			for (Map.Entry<String, EntryMetadata> entry : entries.entrySet()) {
				builder.object(entry.getKey(), value -> value.put("enabled", false));
			}
			for (Map.Entry<String, GroupMetadata> entry : groups.entrySet()) {
				builder.put(entry.getKey(), entry.getValue().toDebugConfigJson());
			}
			return builder.build();
		}
	}

	public record GroupMetadata(String group, Map<String, EntryMetadata> entries) {
		public GroupMetadata {
			group = normalizeName(group, "group");
			entries = entries == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(entries));
		}

		public List<String> entryNames() {
			return List.copyOf(entries.keySet());
		}

		public boolean containsEntry(String entry) {
			return entries.containsKey(normalizeName(entry, "entry"));
		}

		private JsonObject toDebugConfigJson() {
			JsonFormatBuilder.ObjectBuilder builder = JsonFormatBuilder.object().put("enabled", false);
			for (Map.Entry<String, EntryMetadata> entry : entries.entrySet()) {
				builder.object(entry.getKey(), value -> value.put("enabled", false));
			}
			return builder.build();
		}
	}

	public record EntryMetadata(String entry) {
		public EntryMetadata {
			entry = normalizeName(entry, "entry");
		}
	}

	private static String normalizeName(String value, String label) {
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
}

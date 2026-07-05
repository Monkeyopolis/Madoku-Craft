package madoku.craft.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MadokuDebugRegistry {
	static final String MAIN_GROUP_NAME = "main";

	public static final DebugHierarchy ATTRIBUTES = hierarchy(
		"attributes",
		subSystem("health", group("health"), group("health-penalty"), group("absorption"), group("health-boost"), group("poison"), group("regeneration"), group("wither")),
		subSystem("hunger", group("hunger"), group("hunger-depletion"), group("saturation"), group("hunger-effect"), group("starvation-penalty")),
		subSystem("armor", group("main"), group("armor-points"), group("armor-toughness-points"), group("resistance")),
		subSystem("oxygen", group("oxygen"), group("water-breathing"), group("conduit-power"), group("dolphins-grace"), group("breath-of-the-nautilus"), group("suffocating-penalty")),
		subSystem("luck", group("luck"), group("luck-effect"), group("block-drops"), group("mob-drops"), group("creeper-grief-reduction"), group("skeleton-accuracy-reduction"), group("player-critical-damage"))
	);

	private MadokuDebugRegistry() {
	}

	public static DebugHierarchy hierarchy(String mainSystem, DebugSubSystem... subSystems) {
		LinkedHashMap<String, DebugSubSystem> orderedSubSystems = new LinkedHashMap<>();
		if (subSystems != null) {
			for (DebugSubSystem subSystem : subSystems) {
				if (subSystem == null) {
					continue;
				}
				orderedSubSystems.put(subSystem.subSystem(), subSystem);
			}
		}
		return new DebugHierarchy(mainSystem, Collections.unmodifiableMap(orderedSubSystems));
	}

	public static DebugSubSystem subSystem(String name, DebugGroup... groups) {
		ArrayList<DebugGroup> orderedGroups = new ArrayList<>();
		if (groups != null) {
			for (DebugGroup group : groups) {
				if (group == null) {
					continue;
				}
				orderedGroups.add(group);
			}
		}
		return new DebugSubSystem(name, List.copyOf(orderedGroups));
	}

	public static DebugGroup group(String name) {
		return new DebugGroup(name);
	}

	public record DebugHierarchy(String mainSystem, Map<String, DebugSubSystem> subSystems) {
		public DebugHierarchy {
			mainSystem = normalizeName(mainSystem, "main system");
			subSystems = subSystems == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(subSystems));
		}

		public Map<String, List<String>> subSystemGroups() {
			LinkedHashMap<String, List<String>> groups = new LinkedHashMap<>();
			for (Map.Entry<String, DebugSubSystem> entry : subSystems.entrySet()) {
				groups.put(entry.getKey(), entry.getValue().groupNames());
			}
			return Collections.unmodifiableMap(groups);
		}

		public boolean containsSubSystem(String subSystem) {
			return subSystems.containsKey(normalizeName(subSystem, "sub system"));
		}

		public boolean containsGroup(String subSystem, String group) {
			DebugSubSystem debugSubSystem = subSystems.get(normalizeName(subSystem, "sub system"));
			return debugSubSystem != null && debugSubSystem.containsGroup(group);
		}
	}

	public record DebugSubSystem(String subSystem, List<DebugGroup> groups) {
		public DebugSubSystem {
			subSystem = normalizeName(subSystem, "sub system");
			groups = groups == null ? List.of() : List.copyOf(groups);
		}

		public List<String> groupNames() {
			ArrayList<String> names = new ArrayList<>(groups.size());
			for (DebugGroup group : groups) {
				names.add(group.group());
			}
			return List.copyOf(names);
		}

		public boolean containsGroup(String group) {
			String normalized = normalizeName(group, "group");
			if (MAIN_GROUP_NAME.equals(normalized)) {
				return true;
			}
			for (DebugGroup debugGroup : groups) {
				if (debugGroup.group().equals(normalized)) {
					return true;
				}
			}
			return false;
		}
	}

	public record DebugGroup(String group) {
		public DebugGroup {
			group = normalizeName(group, "group");
		}
	}

	private static String normalizeName(String value, String label) {
		String normalized = value == null ? "" : value.trim();
		if (normalized.isBlank()) {
			throw new IllegalArgumentException(label + " must not be blank.");
		}
		return normalized;
	}
}

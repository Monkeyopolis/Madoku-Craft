package madoku.craft.core.data;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Runtime group for data systems that publish or consume managed JSON data. */
public final class DataSystemsManager {
	private static final Set<String> REGISTERED_SYSTEMS = new LinkedHashSet<>();
	private static volatile boolean initialized;

	private DataSystemsManager() {
	}

	public static void initialize() {
		REGISTERED_SYSTEMS.clear();
		initialized = true;
	}

	public static void reset() {
		REGISTERED_SYSTEMS.clear();
		initialized = false;
	}

	public static boolean isInitialized() {
		return initialized;
	}

	public static void registerSystem(String systemId) {
		String normalized = normalize(systemId);
		if (initialized && !normalized.isBlank()) {
			REGISTERED_SYSTEMS.add(normalized);
		}
	}

	public static void unregisterSystem(String systemId) {
		REGISTERED_SYSTEMS.remove(normalize(systemId));
	}

	public static boolean hasSystem(String systemId) {
		return REGISTERED_SYSTEMS.contains(normalize(systemId));
	}

	public static Set<String> getRegisteredSystems() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(REGISTERED_SYSTEMS));
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
	}
}

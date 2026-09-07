package madoku.craft.java.core.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns module registration and deterministic initialization for the unified
 * bundle. It is deliberately independent of concrete gameplay modules.
 */
public final class MadokuModuleRegistry {
	private static final Map<String, MadokuModule> REGISTERED_MODULES = new LinkedHashMap<>();
	private static List<MadokuModule> initializationOrder;
	private static boolean initialized;
	private static boolean clientInitialized;

	private MadokuModuleRegistry() {
	}

	public static synchronized void register(MadokuModule module) {
		if (module == null) {
			throw new IllegalArgumentException("module cannot be null");
		}
		if (initialized) {
			throw new IllegalStateException("Cannot register a module after module initialization has started");
		}

		String id = module.id();
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("module id cannot be blank");
		}
		if (REGISTERED_MODULES.putIfAbsent(id, module) != null) {
			throw new IllegalArgumentException("A module is already registered with id: " + id);
		}
	}

	public static synchronized void initialize() {
		if (initialized) {
			return;
		}

		List<MadokuModule> order = resolveInitializationOrder();
		for (MadokuModule module : order) {
			module.initialize();
		}

		initializationOrder = List.copyOf(order);
		initialized = true;
	}

	public static synchronized void initializeClient() {
		if (clientInitialized) {
			return;
		}
		if (!initialized) {
			initialize();
		}

		for (MadokuModule module : initializationOrder) {
			module.initializeClient();
		}
		clientInitialized = true;
	}

	public static synchronized void onServerStarted(net.minecraft.server.MinecraftServer server) {
		if (!initialized) {
			initialize();
		}
		for (MadokuModule module : initializationOrder) {
			module.onServerStarted(server);
		}
	}

	public static synchronized void onServerStopped(net.minecraft.server.MinecraftServer server) {
		if (!initialized) {
			return;
		}
		for (int index = initializationOrder.size() - 1; index >= 0; index--) {
			initializationOrder.get(index).onServerStopped(server);
		}
	}

	public static synchronized Collection<String> registeredIds() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(REGISTERED_MODULES.keySet()));
	}

	private static List<MadokuModule> resolveInitializationOrder() {
		List<MadokuModule> order = new ArrayList<>();
		Set<String> visiting = new LinkedHashSet<>();
		Set<String> visited = new LinkedHashSet<>();

		for (MadokuModule module : REGISTERED_MODULES.values()) {
			visit(module, visiting, visited, order);
		}
		return order;
	}

	private static void visit(
		MadokuModule module,
		Set<String> visiting,
		Set<String> visited,
		List<MadokuModule> order
	) {
		String id = module.id();
		if (visited.contains(id)) {
			return;
		}
		if (!visiting.add(id)) {
			throw new IllegalStateException("Circular module dependency involving: " + id);
		}

		for (String dependencyId : module.dependencies()) {
			MadokuModule dependency = REGISTERED_MODULES.get(dependencyId);
			if (dependency == null) {
				throw new IllegalStateException("Module '" + id + "' requires missing module '" + dependencyId + "'");
			}
			visit(dependency, visiting, visited, order);
		}

		visiting.remove(id);
		visited.add(id);
		order.add(module);
	}
}

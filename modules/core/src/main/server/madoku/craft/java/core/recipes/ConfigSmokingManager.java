package madoku.craft.java.core.recipes;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ConfigSmokingManager {
	private static volatile boolean initialized;
	private ConfigSmokingManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }

	public static Set<String> buildAddedSmokingInputs() {
		Set<String> defaults = new LinkedHashSet<>();
		defaults.add("minecraft:cactus");
		defaults.add("minecraft:sea_pickle");
		defaults.add("minecraft:chorus_fruit");
		defaults.add("minecraft:oak_log");
		defaults.add("minecraft:spruce_log");
		defaults.add("minecraft:birch_log");
		defaults.add("minecraft:jungle_log");
		defaults.add("minecraft:acacia_log");
		defaults.add("minecraft:cherry_log");
		defaults.add("minecraft:dark_oak_log");
		defaults.add("minecraft:mangrove_log");
		defaults.add("minecraft:pale_oak_log");
		defaults.add("minecraft:stripped_oak_log");
		defaults.add("minecraft:stripped_spruce_log");
		defaults.add("minecraft:stripped_birch_log");
		defaults.add("minecraft:stripped_jungle_log");
		defaults.add("minecraft:stripped_acacia_log");
		defaults.add("minecraft:stripped_cherry_log");
		defaults.add("minecraft:stripped_dark_oak_log");
		defaults.add("minecraft:stripped_mangrove_log");
		defaults.add("minecraft:stripped_pale_oak_log");
		defaults.add("minecraft:crimson_stem");
		defaults.add("minecraft:warped_stem");
		defaults.add("minecraft:stripped_crimson_stem");
		defaults.add("minecraft:stripped_warped_stem");
		defaults.add("minecraft:oak_wood");
		defaults.add("minecraft:spruce_wood");
		defaults.add("minecraft:birch_wood");
		defaults.add("minecraft:jungle_wood");
		defaults.add("minecraft:acacia_wood");
		defaults.add("minecraft:cherry_wood");
		defaults.add("minecraft:pale_oak_wood");
		defaults.add("minecraft:dark_oak_wood");
		defaults.add("minecraft:mangrove_wood");
		defaults.add("minecraft:stripped_oak_wood");
		defaults.add("minecraft:stripped_spruce_wood");
		defaults.add("minecraft:stripped_birch_wood");
		defaults.add("minecraft:stripped_jungle_wood");
		defaults.add("minecraft:stripped_acacia_wood");
		defaults.add("minecraft:stripped_cherry_wood");
		defaults.add("minecraft:stripped_pale_oak_wood");
		defaults.add("minecraft:stripped_dark_oak_wood");
		defaults.add("minecraft:stripped_mangrove_wood");
		defaults.add("minecraft:crimson_hyphae");
		defaults.add("minecraft:warped_hyphae");
		defaults.add("minecraft:stripped_crimson_hyphae");
		defaults.add("minecraft:stripped_warped_hyphae");
		return Set.copyOf(defaults);
	}
}

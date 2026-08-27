package madoku.craft.core.recipes;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ConfigBlastingManager {
	private static volatile boolean initialized;
	private ConfigBlastingManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }

	public static Set<String> buildAddedBlastingInputs() {
		Set<String> defaults = new LinkedHashSet<>();
		defaults.add("minecraft:netherrack");
		defaults.add("minecraft:clay_ball");
		defaults.add("minecraft:wet_sponge");
		defaults.add("minecraft:sand");
		defaults.add("minecraft:red_sand");
		defaults.add("minecraft:clay");
		defaults.add("minecraft:quartz_block");
		defaults.add("minecraft:basalt");
		defaults.add("minecraft:nether_bricks");
		defaults.add("minecraft:polished_blackstone_bricks");
		defaults.add("minecraft:red_sandstone");
		defaults.add("minecraft:sandstone");
		defaults.add("minecraft:deepslate_tiles");
		defaults.add("minecraft:cobbled_deepslate");
		defaults.add("minecraft:deepslate_bricks");
		defaults.add("minecraft:stone_bricks");
		defaults.add("minecraft:stone");
		defaults.add("minecraft:cobblestone");
		defaults.add("minecraft:terracotta");
		defaults.add("minecraft:white_terracotta");
		defaults.add("minecraft:orange_terracotta");
		defaults.add("minecraft:magenta_terracotta");
		defaults.add("minecraft:light_blue_terracotta");
		defaults.add("minecraft:yellow_terracotta");
		defaults.add("minecraft:lime_terracotta");
		defaults.add("minecraft:pink_terracotta");
		defaults.add("minecraft:gray_terracotta");
		defaults.add("minecraft:light_gray_terracotta");
		defaults.add("minecraft:cyan_terracotta");
		defaults.add("minecraft:purple_terracotta");
		defaults.add("minecraft:blue_terracotta");
		defaults.add("minecraft:brown_terracotta");
		defaults.add("minecraft:green_terracotta");
		defaults.add("minecraft:red_terracotta");
		defaults.add("minecraft:black_terracotta");
		return Set.copyOf(defaults);
	}

	public static AddedCookingRecipe buildAddedRecipe() {
		return new AddedCookingRecipe("minecraft:soul_sand", "minecraft:tinted_glass", 0.1F, 100);
	}

	public record AddedCookingRecipe(String inputItemId, String resultItemId, float experience, int cookingTime) { }
}

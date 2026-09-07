package madoku.craft.java.core.recipes;

public final class ConfigSmeltingManager {
	private static volatile boolean initialized;
	private ConfigSmeltingManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }

	public static AddedCookingRecipe buildAddedRecipe() {
		return new AddedCookingRecipe("minecraft:soul_sand", "minecraft:tinted_glass", 0.1F, 200);
	}

	public record AddedCookingRecipe(String inputItemId, String resultItemId, float experience, int cookingTime) { }
}

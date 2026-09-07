package madoku.craft.java.core.recipes;

/** Orchestrates the recipe subsystem through its public API contract. */
public final class MadokuRecipesManager {
	private MadokuRecipesManager() {
	}

	public static void initialize() { RecipesAPIManager.initialize(); }
	public static void reset() { RecipesAPIManager.reset(); }
}

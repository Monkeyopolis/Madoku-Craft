package madoku.craft.java.core.recipes;

import java.util.List;

public final class ConfigCraftingManager {
	private static volatile boolean initialized;
	private ConfigCraftingManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }

	public static List<AddedCraftingRecipe> buildAddedRecipes() {
		return List.of(
			new AddedCraftingRecipe("chainmail/helmet", "minecraft:chainmail_helmet", List.of("NNN", "N N"), 'N', "minecraft:iron_nugget"),
			new AddedCraftingRecipe("chainmail/chestplate", "minecraft:chainmail_chestplate", List.of("N N", "NNN", "NNN"), 'N', "minecraft:iron_nugget"),
			new AddedCraftingRecipe("chainmail/leggings", "minecraft:chainmail_leggings", List.of("NNN", "N N", "N N"), 'N', "minecraft:iron_nugget"),
			new AddedCraftingRecipe("chainmail/boots", "minecraft:chainmail_boots", List.of("N N", "N N"), 'N', "minecraft:iron_nugget")
		);
	}

	public record AddedCraftingRecipe(
		String path,
		String resultItemId,
		List<String> pattern,
		char symbol,
		String ingredientItemId
	) {
		public AddedCraftingRecipe {
			pattern = List.copyOf(pattern);
		}
	}
}

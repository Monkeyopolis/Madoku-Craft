package madoku.craft.java.core.recipes;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeManager;

/** Rebuilds the client recipe lookup caches after synchronized recipe overrides change. */
public final class RecipesClientSyncAPIManager {
	private RecipesClientSyncAPIManager() {
	}

	public static void refresh() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || !(client.level.recipeAccess() instanceof RecipeManager recipeManager)) {
			return;
		}
		recipeManager.finalizeRecipeLoading(client.level.enabledFeatures());
	}
}


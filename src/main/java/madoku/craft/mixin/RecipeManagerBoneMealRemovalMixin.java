package madoku.craft.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerBoneMealRemovalMixin {
	@Unique
	private static final Set<ResourceLocation> MADOKU_CRAFT$BONE_MEAL_RECIPE_IDS = Set.of(
		ResourceLocation.fromNamespaceAndPath("minecraft", "bone_meal"),
		ResourceLocation.fromNamespaceAndPath("minecraft", "bone_meal_from_bone_block")
	);

	@ModifyVariable(method = "replaceRecipes", at = @At("HEAD"), argsOnly = true)
	private Iterable<?> madokuCraft$removeBoneMealRecipes(Iterable<?> recipes) {
		List<Object> filtered = new ArrayList<>();
		for (Object entry : recipes) {
			if (entry instanceof RecipeHolder<?> holder && MADOKU_CRAFT$BONE_MEAL_RECIPE_IDS.contains(holder.id())) {
				continue;
			}
			filtered.add(entry);
		}
		return filtered;
	}
}

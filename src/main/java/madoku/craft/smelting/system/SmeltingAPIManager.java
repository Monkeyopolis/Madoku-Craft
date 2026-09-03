package madoku.craft.smelting.system;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Public contract for Madoku Smelting runtime behavior. */
public final class SmeltingAPIManager {
	private static final SmeltingProvider UNAVAILABLE_PROVIDER = new SmeltingProvider() { };
	private static volatile SmeltingProvider provider = UNAVAILABLE_PROVIDER;

	private SmeltingAPIManager() {
	}

	public static void registerProvider(SmeltingProvider candidate) { if (candidate == null) throw new IllegalArgumentException("Smelting provider must not be null."); provider = candidate; }
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static boolean isEnabled() { return provider.isEnabled(); }

	public static int getCookTimeTicks(AbstractFurnaceBlockEntity furnace, int originalTicks) {
		return provider.getCookTimeTicks(furnace, originalTicks);
	}

	public static int getCookTimeTicks(RecipeType<?> recipeType, int originalTicks) {
		return provider.getCookTimeTicks(recipeType, originalTicks);
	}

	public static int getAdjustedFuelTicks(AbstractFurnaceBlockEntity furnace, ItemStack stack, int originalTicks) {
		return provider.getAdjustedFuelTicks(furnace, stack, originalTicks);
	}

	public static void onFurnaceServerTick(
		ServerLevel level,
		BlockPos blockPos,
		BlockState blockState,
		AbstractFurnaceBlockEntity furnace
	) {
		provider.onFurnaceServerTick(level, blockPos, blockState, furnace);
	}

	public static String describeRecipeType(RecipeType<?> recipeType) {
		return provider.describeRecipeType(recipeType);
	}
}

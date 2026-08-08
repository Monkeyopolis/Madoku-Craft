package madoku.craft.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Runtime facade for farm-plot state owned by the farming runtime. */
public final class FarmingPlotsManager {
	private FarmingPlotsManager() {
	}

	public static boolean isFertilized(ServerLevel world, BlockPos soilPos) {
		return FarmingCropsManager.isFertilized(world, soilPos);
	}

	public static boolean isManagedPlot(ServerLevel world, BlockPos soilPos) {
		return FarmingCropsManager.isManagedPlot(world, soilPos);
	}

	public static void fertilizeSoil(ServerLevel world, BlockPos soilPos) {
		FarmingCropsManager.fertilizeSoil(world, soilPos);
	}

	public static void syncPlotFromSoil(ServerLevel world, BlockPos soilPos, boolean fertilized) {
		FarmingCropsManager.syncPlotFromSoil(world, soilPos, fertilized);
	}
}

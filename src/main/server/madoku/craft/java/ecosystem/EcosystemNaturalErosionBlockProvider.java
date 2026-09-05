package madoku.craft.java.ecosystem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

final class EcosystemNaturalErosionBlockProvider implements EcosystemBlockProvider {
	@Override
	public boolean supports(BlockState state) {
		return state != null && EcosystemAPIManager.TRACKABLE_WET_GROUND_BLOCKS.contains(state.getBlock());
	}

	@Override
	public void checkEligibility(ServerLevel level, BlockPos position, BlockState state) {
		EcosystemNaturalErosionManager.discoverCandidateAt(level, position, state);
	}

	@Override
	public void randomTick(ServerLevel level, BlockPos position, BlockState state, RandomSource random) {
		EcosystemNaturalErosionManager.handleRandomPosition(level, position);
	}
}

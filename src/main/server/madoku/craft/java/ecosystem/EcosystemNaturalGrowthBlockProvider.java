package madoku.craft.java.ecosystem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

final class EcosystemNaturalGrowthBlockProvider implements EcosystemBlockProvider {
	@Override
	public boolean supports(BlockState state) {
		return state != null && EcosystemAPIManager.ECOSYSTEM_GROWTH_BLOCKS.contains(state.getBlock());
	}

	@Override
	public void checkEligibility(ServerLevel level, BlockPos position, BlockState state) {
		EcosystemNaturalGrowthManager.discoverCandidateAt(level, position, state);
	}

	@Override
	public void randomTick(ServerLevel level, BlockPos position, BlockState state, RandomSource random) {
		EcosystemNaturalGrowthManager.handleRandomPosition(level, position);
	}
}

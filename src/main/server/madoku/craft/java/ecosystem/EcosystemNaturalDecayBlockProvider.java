package madoku.craft.java.ecosystem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

final class EcosystemNaturalDecayBlockProvider implements EcosystemBlockProvider {
	@Override
	public boolean supports(BlockState state) {
		return state != null && state.is(BlockTags.LEAVES);
	}

	@Override
	public void checkEligibility(ServerLevel level, BlockPos position, BlockState state) {
		EcosystemNaturalDecayManager.discoverCandidateAt(level, position, state);
	}

	@Override
	public void randomTick(ServerLevel level, BlockPos position, BlockState state, RandomSource random) {
		EcosystemNaturalDecayManager.handleRandomPosition(level, position);
	}
}

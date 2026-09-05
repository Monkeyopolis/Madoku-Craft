package madoku.craft.java.ecosystem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Contract for an ecosystem system that participates in a block's vanilla
 * random-tick path.
 *
 * <p>Providers never select positions. Vanilla selects the position and the
 * block-state hook calls these methods only for states registered for the
 * ecosystem path.</p>
 */
public interface EcosystemBlockProvider {
	boolean supports(BlockState state);

	/** Discovers or removes eligibility for one changed/unchecked block. */
	void checkEligibility(ServerLevel level, BlockPos position, BlockState state);

	/** Advances the provider's existing candidate using this vanilla tick. */
	void randomTick(ServerLevel level, BlockPos position, BlockState state, RandomSource random);
}

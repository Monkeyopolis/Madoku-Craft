package madoku.craft.java.ecosystem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable event representing one position selected by Vanilla's chunk tick. */
public record EcosystemRandomPositionEvent(
	ServerLevel level,
	BlockPos position,
	BlockState sampledState,
	RandomSource random
) {
}

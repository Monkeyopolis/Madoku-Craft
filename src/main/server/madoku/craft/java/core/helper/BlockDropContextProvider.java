package madoku.craft.java.core.helper;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/** Provider contract for the active block-drop context. */
public interface BlockDropContextProvider {
	default void begin(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) { }
	default void end() { }
	default BlockDropContextAPIManager.Context current() { return null; }
	default ServerPlayer resolvePlayer() { return null; }
	default boolean isActiveDropPlayerPlacedBlock() { return false; }
}

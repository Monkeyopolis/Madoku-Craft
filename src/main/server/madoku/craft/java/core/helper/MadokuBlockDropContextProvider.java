package madoku.craft.java.core.helper;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/** Built-in provider backed by the Madoku block-drop context implementation. */
public final class MadokuBlockDropContextProvider implements BlockDropContextProvider {
	@Override public void begin(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) { MadokuBlockDropContextManager.begin(level, player, pos, state); }
	@Override public void end() { MadokuBlockDropContextManager.end(); }
	@Override public BlockDropContextAPIManager.Context current() {
		MadokuBlockDropContextManager.Context context = MadokuBlockDropContextManager.current();
		return context == null ? null : new BlockDropContextAPIManager.Context(context.level(), context.player(), context.pos(), context.state());
	}
	@Override public ServerPlayer resolvePlayer() { return MadokuBlockDropContextManager.resolvePlayer(); }
	@Override public boolean isActiveDropPlayerPlacedBlock() { return MadokuBlockDropContextManager.isActiveDropPlayerPlacedBlock(); }
}

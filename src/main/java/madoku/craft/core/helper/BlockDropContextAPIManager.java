package madoku.craft.core.helper;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/** Public contract for the active block-drop context. */
public final class BlockDropContextAPIManager {
	private BlockDropContextAPIManager() {
	}

	public static void begin(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		MadokuBlockDropContextManager.begin(level, player, pos, state);
	}

	public static void end() {
		MadokuBlockDropContextManager.end();
	}

	public static Context current() {
		MadokuBlockDropContextManager.Context context = MadokuBlockDropContextManager.current();
		return context == null ? null : new Context(context.level(), context.player(), context.pos(), context.state());
	}

	public static ServerPlayer resolvePlayer() {
		return MadokuBlockDropContextManager.resolvePlayer();
	}

	public static boolean isActiveDropPlayerPlacedBlock() {
		return MadokuBlockDropContextManager.isActiveDropPlayerPlacedBlock();
	}

	public record Context(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) { }
}

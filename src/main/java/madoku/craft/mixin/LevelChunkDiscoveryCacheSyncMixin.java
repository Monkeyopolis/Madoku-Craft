package madoku.craft.mixin;

import madoku.craft.chunk.ChunkManagerSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelChunkDiscoveryCacheSyncMixin {
	@Inject(
		method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
		at = @At("RETURN"),
		require = 0
	)
	private void madoku$syncChunkDiscoveryCacheSetBlock(
		BlockPos pos,
		BlockState nextState,
		int flags,
		int recursionLeft,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (!((Object) this instanceof ServerLevel)) {
			return;
		}
		if (cir != null && !cir.getReturnValue()) {
			return;
		}
		if (ChunkManagerSystem.isInternalProcessorMutationActive()) {
			return;
		}
		ServerLevel serverLevel = (ServerLevel) (Object) this;
		ChunkManagerSystem.onWorldPositionChanged(serverLevel, pos, null, nextState);
	}
}

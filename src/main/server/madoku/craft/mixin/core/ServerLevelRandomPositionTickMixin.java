package madoku.craft.mixin.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import madoku.craft.java.core.chunk.ChunkAPIManager;

@Mixin(ServerLevel.class)
public abstract class ServerLevelRandomPositionTickMixin {
	@Redirect(
		method = "tickChunk",
		slice = @org.spongepowered.asm.mixin.injection.Slice(
			from = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V"
			)
		),
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;getBlockRandomPos(IIII)Lnet/minecraft/core/BlockPos;"
		)
	)
	private BlockPos madokuCraft$observeVanillaRandomPosition(ServerLevel level, int x, int y, int z, int maxY) {
		BlockPos position = level.getBlockRandomPos(x, y, z, maxY);
		ChunkAPIManager.dispatchRandomPosition(level, position, level.getRandom());
		return position;
	}
}

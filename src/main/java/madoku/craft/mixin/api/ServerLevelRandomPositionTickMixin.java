package madoku.craft.mixin.api;

import madoku.craft.api.chunk.MadokuChunkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public abstract class ServerLevelRandomPositionTickMixin {
	@Redirect(
		method = "tickChunk",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;getBlockRandomPos(IIII)Lnet/minecraft/core/BlockPos;",
			ordinal = 1
		)
	)
	private BlockPos madokuCraft$observeVanillaRandomPosition(ServerLevel level, int x, int y, int z, int maxY) {
		BlockPos position = level.getBlockRandomPos(x, y, z, maxY);
		MadokuChunkManager.dispatchRandomPosition(level, position, level.getRandom());
		return position;
	}
}

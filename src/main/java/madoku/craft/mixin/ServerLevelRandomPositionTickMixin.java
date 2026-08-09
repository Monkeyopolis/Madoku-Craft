package madoku.craft.mixin;

import madoku.craft.api.chunk.MadokuChunkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelRandomPositionTickMixin {
	@Inject(method = "tickChunk", at = @At("TAIL"))
	private void madokuCraft$dispatchRandomPositions(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
		if (chunk == null || randomTickSpeed <= 0) {
			return;
		}

		ServerLevel level = (ServerLevel) (Object) this;
		RandomSource random = RandomSource.create();
		int minY = level.getMinY();
		int height = Math.max(1, level.getMaxY() - minY);
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();

		for (int attempt = 0; attempt < randomTickSpeed; attempt++) {
			int x = minX + random.nextInt(16);
			int y = minY + random.nextInt(height);
			int z = minZ + random.nextInt(16);
			MadokuChunkManager.dispatchRandomPosition(level, new BlockPos(x, y, z), random);
		}
	}
}

package madoku.craft.mixin.core;

import madoku.craft.java.ecosystem.EcosystemRandomPositionAPIManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

	/** Routes Vanilla's random block samples to ecosystem wake-up listeners. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelEcosystemRandomPositionMixin {
	@Redirect(
		method = "tickChunk",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;getBlockRandomPos(IIII)Lnet/minecraft/core/BlockPos;",
			ordinal = 1
		)
	)
	private BlockPos madokuCraft$dispatchRandomPosition(ServerLevel level, int x, int y, int z, int radius) {
		BlockPos position = level.getBlockRandomPos(x, y, z, radius);
		EcosystemRandomPositionAPIManager.dispatch(level, position, level.getRandom());
		return position;
	}
}

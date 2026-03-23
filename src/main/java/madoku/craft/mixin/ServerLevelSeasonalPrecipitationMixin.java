package madoku.craft.mixin;

import madoku.craft.season.MadokuSeason;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public abstract class ServerLevelSeasonalPrecipitationMixin {
	@Redirect(
		method = "tickPrecipitation(Lnet/minecraft/core/BlockPos;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"
		)
	)
	private boolean madoku$allowSnowButBlockVanillaIcePlacement(ServerLevel level, BlockPos pos, BlockState state) {
		if (!MadokuSeason.isEnabled()) {
			return level.setBlockAndUpdate(pos, state);
		}

		if (state != null && (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE))) {
			return false;
		}

		return level.setBlockAndUpdate(pos, state);
	}
}

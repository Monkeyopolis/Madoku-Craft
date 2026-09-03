package madoku.craft.mixin.season;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import madoku.craft.java.core.season.SeasonAPIManager;
import madoku.craft.java.core.season.SeasonEnvironmentTransitionAPIManager;

@Mixin(IceBlock.class)
public abstract class IceBlockSeasonalMeltMixin {
	@Shadow
	protected abstract void melt(BlockState state, Level level, BlockPos pos);

	@Inject(
		method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madoku$seasonalMeltIce(
		BlockState state,
		ServerLevel level,
		BlockPos pos,
		RandomSource random,
		CallbackInfo ci
	) {
		if (!SeasonAPIManager.isEnabled()
			|| !SeasonEnvironmentTransitionAPIManager.isWaterTransitionEnabled()
			|| level == null
			|| pos == null) {
			return;
		}

		if (SeasonAPIManager.shouldSeasonMeltAt(level, pos)) {
			melt(state, level, pos);
		}

		// Seasonal water rules own the ice state. Cancel vanilla's light-based
		// melting while the block remains inside the freezing threshold.
		ci.cancel();
	}
}



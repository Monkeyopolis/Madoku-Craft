package madoku.craft.mixin;

import madoku.craft.season.MadokuSeason;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SnowLayerBlock.class)
public abstract class SnowLayerBlockSeasonalMeltMixin {
	@Inject(
		method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madoku$seasonalMeltSnow(
		BlockState state,
		ServerLevel level,
		BlockPos pos,
		RandomSource random,
		CallbackInfo ci
	) {
		if (!MadokuSeason.isEnabled() || level == null || pos == null) {
			return;
		}

		if (MadokuSeason.shouldSeasonFreezeAt(level, level.getBiome(pos).value(), pos)) {
			return;
		}

		level.removeBlock(pos, false);
		ci.cancel();
	}
}

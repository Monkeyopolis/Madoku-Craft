package madoku.craft.mixin;

import madoku.craft.farming.MadokuFarmingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StemBlock.class)
public abstract class StemBlockFarmingMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$handleStemRandomTick(
		BlockState state,
		ServerLevel level,
		BlockPos pos,
		RandomSource random,
		CallbackInfo ci
	) {
		if (!MadokuFarmingManager.isEnabled() || !MadokuFarmingManager.isManagedCrop(state)) {
			return;
		}

		MadokuFarmingManager.trackCrop(level, pos, state);
		if (MadokuFarmingManager.isManagedPlot(level, pos.below())) {
			ci.cancel();
		}
	}
}


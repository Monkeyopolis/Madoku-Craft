package madoku.craft.mixin;

import madoku.craft.farming.MadokuFarmingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockFarmingMixin {
	@Inject(method = "turnToDirt", at = @At("HEAD"), cancellable = true)
	private static void madokuCraft$preventManagedFarmlandDirt(
		Entity entity,
		BlockState state,
		Level level,
		BlockPos pos,
		CallbackInfo ci
	) {
		if (shouldHoldManagedFarmland(level, pos)) {
			ci.cancel();
		}
	}

	@Inject(method = "shouldMaintainFarmland", at = @At("RETURN"), cancellable = true)
	private static void madokuCraft$holdManagedFarmland(
		BlockGetter level,
		BlockPos pos,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (Boolean.FALSE.equals(cir.getReturnValue()) && shouldHoldManagedFarmland(level, pos)) {
			cir.setReturnValue(true);
		}
	}

	private static boolean shouldHoldManagedFarmland(BlockGetter level, BlockPos pos) {
		if (!MadokuFarmingManager.isEnabled() || level == null || pos == null) {
			return false;
		}

		BlockPos abovePos = pos.above();
		BlockState aboveState = level.getBlockState(abovePos);
		if (MadokuFarmingManager.isManagedCrop(aboveState)) {
			if (level instanceof ServerLevel serverLevel && !MadokuFarmingManager.isManagedPlot(serverLevel, pos)) {
				MadokuFarmingManager.syncPlotFromSoil(serverLevel, pos, MadokuFarmingManager.isFertilized(serverLevel, pos));
			}
			return true;
		}

		if (level instanceof ServerLevel serverLevel) {
			if (!MadokuFarmingManager.isManagedPlot(serverLevel, pos) && MadokuFarmingManager.isManagedCrop(serverLevel, abovePos, aboveState)) {
				MadokuFarmingManager.syncPlotFromSoil(serverLevel, pos, MadokuFarmingManager.isFertilized(serverLevel, pos));
			}
			return MadokuFarmingManager.isManagedPlot(serverLevel, pos) || MadokuFarmingManager.isManagedCrop(serverLevel, abovePos, aboveState);
		}

		return false;
	}
}


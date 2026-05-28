package madoku.craft.mixin;

import madoku.craft.season.MadokuSeason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public abstract class BiomeSeasonalFreezeMixin {
	@Inject(
		method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
		at = @At("RETURN"),
		cancellable = true
	)
	private void madoku$seasonalShouldFreeze(
		LevelReader levelReader,
		BlockPos pos,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (MadokuSeason.isEnabled()
			&& levelReader instanceof ServerLevel
			&& pos != null
			&& MadokuSeason.resolveSeasonalPrecipitation((Biome) (Object) this) == MadokuSeason.SeasonalPrecipitation.RAIN) {
			cir.setReturnValue(false);
			return;
		}

		if (cir.getReturnValue()
			|| !MadokuSeason.isEnabled()
			|| !(levelReader instanceof ServerLevel serverLevel)
			|| pos == null
			|| !madoku$isSeasonalWaterCandidate(levelReader, pos)) {
			return;
		}

		boolean seasonalFreeze = MadokuSeason.shouldSeasonFreezeAt(serverLevel, (Biome) (Object) this, pos);
		if (seasonalFreeze) {
			cir.setReturnValue(true);
		}
	}

	@Inject(
		method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z",
		at = @At("RETURN"),
		cancellable = true
	)
	private void madoku$seasonalShouldFreezeWithEdgeCheck(
		LevelReader levelReader,
		BlockPos pos,
		boolean mustBeAtEdge,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (MadokuSeason.isEnabled()
			&& levelReader instanceof ServerLevel
			&& pos != null
			&& MadokuSeason.resolveSeasonalPrecipitation((Biome) (Object) this) == MadokuSeason.SeasonalPrecipitation.RAIN) {
			cir.setReturnValue(false);
			return;
		}

		if (cir.getReturnValue()
			|| !MadokuSeason.isEnabled()
			|| !(levelReader instanceof ServerLevel serverLevel)
			|| pos == null
			|| !madoku$isSeasonalWaterCandidate(levelReader, pos)
			|| (mustBeAtEdge && madoku$isSurroundedByWater(levelReader, pos))) {
			return;
		}

		boolean seasonalFreeze = MadokuSeason.shouldSeasonFreezeAt(serverLevel, (Biome) (Object) this, pos);
		if (seasonalFreeze) {
			cir.setReturnValue(true);
		}
	}

	@Inject(
		method = "shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
		at = @At("RETURN"),
		cancellable = true
	)
	private void madoku$seasonalShouldSnow(
		LevelReader levelReader,
		BlockPos pos,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (!MadokuSeason.isEnabled() || !(levelReader instanceof ServerLevel serverLevel) || pos == null) {
			return;
		}
		if (MadokuSeason.resolveSeasonalPrecipitation((Biome) (Object) this) != MadokuSeason.SeasonalPrecipitation.SNOW) {
			cir.setReturnValue(false);
			return;
		}
		if (cir.getReturnValue()) {
			return;
		}

		boolean seasonalFreeze = MadokuSeason.shouldSeasonFreezeAt(serverLevel, (Biome) (Object) this, pos);
		if (!seasonalFreeze || !madoku$canPlaceSeasonalSnow(levelReader, pos)) {
			cir.setReturnValue(false);
			return;
		}

		cir.setReturnValue(true);
	}

	private static boolean madoku$isSeasonalWaterCandidate(LevelReader levelReader, BlockPos pos) {
		BlockState state = levelReader.getBlockState(pos);
		return state != null
			&& state.getFluidState().is(FluidTags.WATER)
			&& state.getFluidState().isSource();
	}

	private static boolean madoku$isSurroundedByWater(LevelReader levelReader, BlockPos pos) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos neighborPos = pos.relative(direction);
			BlockState neighborState = levelReader.getBlockState(neighborPos);
			if (neighborState == null || !neighborState.getFluidState().is(FluidTags.WATER)) {
				return false;
			}
		}
		return true;
	}

	private static boolean madoku$canPlaceSeasonalSnow(LevelReader levelReader, BlockPos pos) {
		BlockState stateAtPos = levelReader.getBlockState(pos);
		if (stateAtPos == null || !stateAtPos.isAir()) {
			return false;
		}
		return Blocks.SNOW.defaultBlockState().canSurvive(levelReader, pos);
	}
}

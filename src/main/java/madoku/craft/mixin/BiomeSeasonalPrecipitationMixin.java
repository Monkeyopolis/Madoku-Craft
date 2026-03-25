package madoku.craft.mixin;

import madoku.craft.season.MadokuSeason;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public abstract class BiomeSeasonalPrecipitationMixin {
	@Inject(
		method = "getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madoku$seasonalPrecipitationAtPosition(
		BlockPos pos,
		CallbackInfoReturnable<Biome.Precipitation> cir
	) {
		cir.setReturnValue(MadokuSeason.resolveSeasonalPrecipitation((Biome) (Object) this).vanilla());
	}

	@Inject(
		method = "coldEnoughToSnow(Lnet/minecraft/core/BlockPos;)Z",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madoku$seasonalColdEnoughToSnow(
		BlockPos pos,
		CallbackInfoReturnable<Boolean> cir
	) {
		Biome.Precipitation precipitation = MadokuSeason.resolveSeasonalPrecipitation((Biome) (Object) this).vanilla();
		cir.setReturnValue(precipitation == Biome.Precipitation.SNOW);
	}

	@Inject(
		method = "warmEnoughToRain(Lnet/minecraft/core/BlockPos;)Z",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madoku$seasonalWarmEnoughToRain(
		BlockPos pos,
		CallbackInfoReturnable<Boolean> cir
	) {
		Biome.Precipitation precipitation = MadokuSeason.resolveSeasonalPrecipitation((Biome) (Object) this).vanilla();
		cir.setReturnValue(precipitation == Biome.Precipitation.RAIN);
	}

	@Inject(
		method = "shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madoku$seasonalShouldSnow(
		LevelReader level,
		BlockPos pos,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (level == null || pos == null) {
			cir.setReturnValue(false);
			return;
		}

		Biome.Precipitation precipitation = level instanceof ServerLevel serverLevel
			? MadokuSeason.resolveSeasonalPrecipitation(serverLevel, (Biome) (Object) this).vanilla()
			: MadokuSeason.resolveSeasonalPrecipitation((Biome) (Object) this).vanilla();

		if (precipitation != Biome.Precipitation.SNOW) {
			cir.setReturnValue(false);
			return;
		}

		if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
			cir.setReturnValue(false);
			return;
		}

		if (level.getBrightness(LightLayer.BLOCK, pos) >= 10) {
			cir.setReturnValue(false);
			return;
		}

		BlockState state = level.getBlockState(pos);
		if (!state.isAir() && !state.is(Blocks.SNOW)) {
			cir.setReturnValue(false);
			return;
		}

		cir.setReturnValue(Blocks.SNOW.defaultBlockState().canSurvive(level, pos));
	}
}

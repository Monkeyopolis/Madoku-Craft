package madoku.craft.mixin;

import madoku.craft.season.MadokuSeason;
import net.minecraft.world.level.biome.Biome;
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
		net.minecraft.core.BlockPos pos,
		CallbackInfoReturnable<Biome.Precipitation> cir
	) {
		cir.setReturnValue(MadokuSeason.resolveSeasonalPrecipitation((Biome) (Object) this).vanilla());
	}

	@Inject(
		method = "shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madoku$seasonalShouldSnow(
		net.minecraft.world.level.LevelReader level,
		net.minecraft.core.BlockPos pos,
		CallbackInfoReturnable<Boolean> cir
	) {
		cir.setReturnValue(MadokuSeason.resolveSeasonalPrecipitation((Biome) (Object) this).vanilla() == Biome.Precipitation.SNOW);
	}

	@Inject(
		method = "coldEnoughToSnow(Lnet/minecraft/core/BlockPos;)Z",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madoku$seasonalColdEnoughToSnow(
		net.minecraft.core.BlockPos pos,
		CallbackInfoReturnable<Boolean> cir
	) {
		cir.setReturnValue(MadokuSeason.resolveSeasonalPrecipitation((Biome) (Object) this).vanilla() == Biome.Precipitation.SNOW);
	}
}

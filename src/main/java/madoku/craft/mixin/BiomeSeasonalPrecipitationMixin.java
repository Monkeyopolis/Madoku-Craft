package madoku.craft.mixin;

import madoku.craft.api.season.MadokuSeasonManager;
import madoku.craft.api.season.SeasonEnvironmentTransitionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.biome.Biome;

@Mixin(Biome.class)
public abstract class BiomeSeasonalPrecipitationMixin {
	@Inject(
		method = "getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madoku$seasonalPrecipitationAtPosition(
		net.minecraft.core.BlockPos pos,
		int seaLevel,
		CallbackInfoReturnable<Biome.Precipitation> cir
	) {
		if (!MadokuSeasonManager.isEnabled() || !SeasonEnvironmentTransitionManager.isWeatherTransitionEnabled()) {
			return;
		}
		cir.setReturnValue(MadokuSeasonManager.resolveSeasonalPrecipitation((Biome) (Object) this));
	}
}

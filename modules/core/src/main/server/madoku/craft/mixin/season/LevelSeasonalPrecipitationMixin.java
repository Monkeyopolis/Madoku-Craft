package madoku.craft.mixin.season;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import madoku.craft.java.core.season.SeasonAPIManager;
import madoku.craft.java.core.season.SeasonEnvironmentTransitionAPIManager;
import madoku.craft.java.core.season.SeasonWeatherAPIManager;

@Mixin(Level.class)
public abstract class LevelSeasonalPrecipitationMixin {
	@Inject(
		method = "precipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madoku$seasonalPrecipitationAtPosition(
		BlockPos pos,
		CallbackInfoReturnable<Biome.Precipitation> cir
	) {
		if (!SeasonAPIManager.isEnabled()
			|| !((Object) this instanceof ServerLevel serverLevel)
			|| pos == null) {
			return;
		}

		if (SeasonWeatherAPIManager.isEnabled()) {
			if (!SeasonWeatherAPIManager.isPrecipitating(serverLevel)) {
				cir.setReturnValue(Biome.Precipitation.NONE);
				return;
			}
		} else if (!SeasonEnvironmentTransitionAPIManager.isWeatherTransitionEnabled() || !serverLevel.isRaining()) {
			return;
		}
		cir.setReturnValue(SeasonAPIManager.resolveSeasonalPrecipitation(serverLevel, pos));
	}
}


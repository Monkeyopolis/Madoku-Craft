package madoku.craft.mixin;

import madoku.craft.api.season.MadokuSeasonManager;
import madoku.craft.api.season.SeasonEnvironmentTransitionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
		if (!MadokuSeasonManager.isEnabled()
			|| !SeasonEnvironmentTransitionManager.isWeatherTransitionEnabled()
			|| !((Object) this instanceof ServerLevel serverLevel)
			|| pos == null
			|| !serverLevel.isRaining()) {
			return;
		}
		cir.setReturnValue(MadokuSeasonManager.resolveSeasonalPrecipitation(serverLevel, pos));
	}
}
package madoku.craft.mixin.client;

import madoku.craft.clock.MadokuTicks;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.season.MadokuSeason;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome.Precipitation;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.renderer.WeatherEffectRenderer")
public abstract class WeatherEffectRendererMixin {
	@Unique
	private static volatile boolean loggedWeatherRendererHook = false;

	@Inject(
		method = "getPrecipitationAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$seasonalClientPrecipitation(
		Level level,
		BlockPos pos,
		CallbackInfoReturnable<Biome.Precipitation> cir
	) {
		if (level == null || pos == null || !MadokuSeason.isEnabled()) {
			return;
		}

		Biome biome = level.getBiome(pos).value();
		Precipitation precipitation = MadokuSeason.resolveSeasonalPrecipitation(level, biome).vanilla();
		if (!loggedWeatherRendererHook && MadokuDebug.shouldEmit(MadokuDebug.Domain.SEASON, "season.precipitation_renderer_client")) {
			loggedWeatherRendererHook = true;
			MadokuDebug.event("season.precipitation_renderer_client", MadokuDebug.Domain.SEASON)
				.side(MadokuDebug.Side.CLIENT)
				.tick(MadokuTicks.getGameplayTicks())
				.subject("weather_renderer")
				.field("biome", biome.getClass().getName())
				.field("season", MadokuSeason.getCurrentSeasonId())
					.field("precipitation", precipitation.name())
					.log();
		}
		cir.setReturnValue(precipitation);
	}
}

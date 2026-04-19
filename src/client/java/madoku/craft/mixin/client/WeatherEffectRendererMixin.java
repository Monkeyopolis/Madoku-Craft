package madoku.craft.mixin.client;

import madoku.craft.clock.MadokuTicks;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.season.MadokuSeason;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.Precipitation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
public abstract class WeatherEffectRendererMixin {
	@Unique
	private static volatile boolean loggedWeatherRendererHook = false;

	@Shadow
	@Final
	private Minecraft minecraft;

	@Redirect(
		method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"
		)
	)
	private Precipitation madokuCraft$seasonalClientPrecipitation(Biome biome, BlockPos pos) {
		Level level = this.minecraft == null ? null : this.minecraft.level;
		if (biome == null || level == null || pos == null || !MadokuSeason.isEnabled()) {
			return biome == null ? Precipitation.NONE : biome.getPrecipitationAt(pos);
		}

		Precipitation precipitation = MadokuSeason.resolveSeasonalPrecipitation(biome).vanilla();
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
		return precipitation;
	}
}

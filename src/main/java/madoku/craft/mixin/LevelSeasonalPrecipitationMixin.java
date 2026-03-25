package madoku.craft.mixin;

import madoku.craft.debug.MadokuDebug;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.season.MadokuSeason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelSeasonalPrecipitationMixin {
	private static volatile boolean loggedPrecipitationHook = false;

	@Inject(
		method = "precipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madoku$seasonalPrecipitationAt(
		BlockPos pos,
		CallbackInfoReturnable<Biome.Precipitation> cir
	) {
		if (pos == null || !MadokuSeason.isEnabled()) {
			return;
		}

		Level level = (Level) (Object) this;
		Holder<Biome> biomeEntry = level.getBiome(pos);
		Biome biome = biomeEntry == null ? null : biomeEntry.value();
		if (biome == null) {
			return;
		}

		Biome.Precipitation precipitation = level instanceof ServerLevel serverLevel
			? MadokuSeason.resolveSeasonalPrecipitation(serverLevel, biome).vanilla()
			: MadokuSeason.resolveSeasonalPrecipitation(biome).vanilla();
		if (!loggedPrecipitationHook) {
			loggedPrecipitationHook = true;
			if (MadokuDebug.shouldEmit(MadokuDebug.Domain.SEASON, "season.precipitation_level_hook")) {
				MadokuDebug.Side side = level instanceof ServerLevel ? MadokuDebug.Side.SERVER : MadokuDebug.Side.CLIENT;
				MadokuDebug.event("season.precipitation_level_hook", MadokuDebug.Domain.SEASON)
					.side(side)
					.tick(MadokuTicks.getGameplayTicks())
					.subject("precipitation")
					.field("level", level.getClass().getName())
					.field("biome", biome.getClass().getName())
					.field("season", MadokuSeason.getCurrentSeasonId(level instanceof ServerLevel serverLevel ? serverLevel : null))
					.field("precipitation", precipitation.name())
					.log();
			}
		}
		cir.setReturnValue(precipitation);
	}
}

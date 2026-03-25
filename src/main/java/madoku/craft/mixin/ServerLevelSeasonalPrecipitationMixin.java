package madoku.craft.mixin;

import madoku.craft.clock.MadokuTicks;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.season.MadokuSeason;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public abstract class ServerLevelSeasonalPrecipitationMixin {
	private static volatile boolean loggedShouldSnowHook = false;

	@Redirect(
		method = "tickPrecipitation(Lnet/minecraft/core/BlockPos;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"
		)
	)
	private boolean madoku$allowSnowButBlockVanillaIcePlacement(ServerLevel level, BlockPos pos, BlockState state) {
		if (!MadokuSeason.isEnabled()) {
			return level.setBlockAndUpdate(pos, state);
		}

		if (state != null && (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE))) {
			return false;
		}

		return level.setBlockAndUpdate(pos, state);
	}

	@Redirect(
		method = "tickPrecipitation(Lnet/minecraft/core/BlockPos;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"
		)
	)
	private boolean madoku$seasonalShouldSnow(Biome biome, LevelReader level, BlockPos pos) {
		if (biome == null || level == null || pos == null || !MadokuSeason.isEnabled()) {
			return biome != null && biome.shouldSnow(level, pos);
		}

		Biome.Precipitation precipitation = level instanceof ServerLevel serverLevel
			? MadokuSeason.resolveSeasonalPrecipitation(serverLevel, biome).vanilla()
			: MadokuSeason.resolveSeasonalPrecipitation(biome).vanilla();
		if (!loggedShouldSnowHook) {
			loggedShouldSnowHook = true;
			if (MadokuDebug.shouldEmit(MadokuDebug.Domain.SEASON, "season.precipitation_should_snow_hook")) {
				MadokuDebug.event("season.precipitation_should_snow_hook", MadokuDebug.Domain.SEASON)
					.side(MadokuDebug.Side.SERVER)
					.tick(MadokuTicks.getGameplayTicks())
					.subject("precipitation")
					.field("level", level.getClass().getName())
					.field("biome", biome.getClass().getName())
					.field("season", level instanceof ServerLevel serverLevel ? MadokuSeason.getCurrentSeasonId(serverLevel) : MadokuSeason.getCurrentSeasonId())
					.field("precipitation", precipitation.name())
					.log();
			}
		}
		return precipitation == Biome.Precipitation.SNOW;
	}
}

package madoku.craft.mixin.season;

import madoku.craft.java.season.ClientSeasonalPrecipitationState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public abstract class BiomeSeasonalPrecipitationMixin {
	@Inject(
		method = "getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madoku$seasonalPrecipitationAtPosition(
		BlockPos pos,
		int seaLevel,
		CallbackInfoReturnable<Biome.Precipitation> cir
	) {
		if (!ClientSeasonalPrecipitationState.isSynchronized()
			|| !ClientSeasonalPrecipitationState.isPrecipitating()) {
			return;
		}
		Biome.Precipitation precipitation = ClientSeasonalPrecipitationState.resolve((Biome) (Object) this);
		cir.setReturnValue(precipitation);
	}
}

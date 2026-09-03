package madoku.craft.mixin.season;

import madoku.craft.java.season.ClientSeasonalPrecipitationState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelSeasonalWeatherClientMixin {
	@Inject(method = "getRainLevel(F)F", at = @At("RETURN"), cancellable = true)
	private void madoku$seasonalRainLevel(
		float partialTick,
		CallbackInfoReturnable<Float> cir
	) {
		if ((Object) this instanceof ClientLevel) {
			cir.setReturnValue(ClientSeasonalPrecipitationState.resolveRainLevel(cir.getReturnValue()));
		}
	}

	@Inject(method = "getThunderLevel(F)F", at = @At("RETURN"), cancellable = true)
	private void madoku$seasonalThunderLevel(
		float partialTick,
		CallbackInfoReturnable<Float> cir
	) {
		if ((Object) this instanceof ClientLevel) {
			cir.setReturnValue(ClientSeasonalPrecipitationState.resolveThunderLevel(cir.getReturnValue()));
		}
	}
}

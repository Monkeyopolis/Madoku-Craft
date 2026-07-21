package madoku.craft.mixin.client.sodium;

import madoku.craft.color.ClientColorContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = {
	"net.caffeinemc.mods.sodium.client.model.color.DefaultColorProviders$FoliageColorProvider",
	"net.caffeinemc.mods.sodium.client.model.color.DefaultColorProviders$GrassColorProvider"
}, remap = false)
public abstract class SodiumColorProviderContextMixin {
	@Inject(method = "getColor", at = @At("HEAD"))
	private void madokuCraft$beginColorContext(CallbackInfoReturnable<Integer> cir) {
		String provider = getClass().getName();
		ClientColorContext.force(provider.contains("FoliageColorProvider"));
	}

	@Inject(method = "getColor", at = @At("RETURN"))
	private void madokuCraft$endColorContext(CallbackInfoReturnable<Integer> cir) {
		ClientColorContext.clear();
	}
}

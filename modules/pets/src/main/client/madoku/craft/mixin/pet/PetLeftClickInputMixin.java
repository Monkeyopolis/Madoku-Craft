package madoku.craft.mixin.pet;

import madoku.craft.java.pet.PetPayloadAPIManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class PetLeftClickInputMixin {
	@Inject(method = "startAttack", at = @At("HEAD"))
	@SuppressWarnings("resource")
	private void madokuCraft$sendLeftClickAirSignal(CallbackInfoReturnable<Boolean> cir) {
		Minecraft client = (Minecraft) (Object) this;
		if (client.player == null || client.level == null || client.hitResult == null
			|| client.hitResult.getType() != HitResult.Type.MISS) {
			return;
		}
		ClientPlayNetworking.send(new PetPayloadAPIManager.LeftClickAirPayload());
	}
}

package madoku.craft.mixin;

import madoku.craft.pet.PetAbilitiesManager;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerPetLeftClickMixin {
	@Inject(method = "handleAnimate", at = @At("HEAD"))
	private void madokuCraft$handleMainHandSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
		if (packet.getHand() == InteractionHand.MAIN_HAND) {
			PetAbilitiesManager.handlePlayerLeftClick(((ServerGamePacketListenerImpl) (Object) this).getPlayer());
		}
	}
}

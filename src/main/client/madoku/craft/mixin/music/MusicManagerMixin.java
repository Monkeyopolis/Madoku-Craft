package madoku.craft.mixin.music;

import madoku.craft.java.utility.music.MusicAPIManager;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public final class MusicManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$overrideVanillaMusicTick(CallbackInfo callbackInfo) {
		if (MusicAPIManager.tick(net.minecraft.client.Minecraft.getInstance())) {
			callbackInfo.cancel();
		}
	}
}

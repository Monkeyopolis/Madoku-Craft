package madoku.craft.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenLabelMixin {
	@Inject(
		method = "renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$hideInventoryLabels(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
		ci.cancel();
	}
}

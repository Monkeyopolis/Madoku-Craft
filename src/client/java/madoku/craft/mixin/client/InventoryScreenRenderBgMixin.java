package madoku.craft.mixin.client;

import madoku.craft.inventory.PlayerEntitiesInventoryClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenRenderBgMixin {
	@Inject(
		method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
		at = @At("TAIL")
	)
	private void madokuCraft$renderPetInventoryBackground(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
		PlayerEntitiesInventoryClient.renderInventoryBackground((InventoryScreen) (Object) this, guiGraphics, mouseX, mouseY);
	}
}

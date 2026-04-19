package madoku.craft.mixin.client;

import madoku.craft.inventory.PlayerEntitiesInventoryClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenRenderBgMixin {
	@Inject(
		method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
		at = @At("TAIL")
	)
	private void madokuCraft$renderCreativePetInventoryBackground(
		GuiGraphics guiGraphics,
		float partialTick,
		int mouseX,
		int mouseY,
		CallbackInfo ci
	) {
		PlayerEntitiesInventoryClient.renderCreativeInventoryBackground((CreativeModeInventoryScreen) (Object) this, guiGraphics, mouseX, mouseY);
	}
}

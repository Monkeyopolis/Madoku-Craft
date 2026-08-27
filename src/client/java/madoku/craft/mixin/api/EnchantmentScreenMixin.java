package madoku.craft.mixin.api;

import madoku.craft.api.enchant.EnchantConfigManager;
import madoku.craft.api.enchant.EnchantTableManager;
import madoku.craft.MadokuCraft;
import madoku.craft.mixin.inventory.AbstractContainerScreenAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin {
	private static final Identifier MADOKU_BOOK_SLOT = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/book-slot.png");
	private static final Identifier MADOKU_BOTTLE_SLOT = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/bottle-slot.png");
	@Unique private int madokuCraft$slotIconTick;

	@Inject(method = "containerTick()V", at = @At("TAIL"))
	private void madokuCraft$tickSlotIcon(CallbackInfo ci) {
		if (EnchantConfigManager.isEnchantmentTableEnabled()) madokuCraft$slotIconTick++;
	}

	@Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$handleShiftClick(
		MouseButtonEvent event,
		boolean doubleClick,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (!EnchantConfigManager.isEnchantmentTableEnabled() || event.button() != 0 || !event.hasShiftDown()) return;

		EnchantmentScreen screen = (EnchantmentScreen) (Object) this;
		AbstractContainerScreenAccessor screenAccess = (AbstractContainerScreenAccessor) (Object) this;
		int left = screenAccess.madokuCraft$getLeftPos();
		int top = screenAccess.madokuCraft$getTopPos();
		for (int option = 0; option < 3; option++) {
			int optionTop = top + 14 + option * 19;
			if (event.x() < left + 60 || event.x() >= left + 168 || event.y() < optionTop || event.y() >= optionTop + 19) continue;

			Minecraft client = Minecraft.getInstance();
			if (client.player == null || client.gameMode == null) return;
			EnchantmentMenu menu = screen.getMenu();
			int encodedButton = EnchantTableManager.encodeShiftButton(option);
			if (menu.clickMenuButton(client.player, encodedButton)) {
				client.gameMode.handleInventoryButtonClick(menu.containerId, encodedButton);
				cir.setReturnValue(true);
			}
			return;
		}
	}

	@Inject(method = "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
	private void madokuCraft$drawInputSlotIcon(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!EnchantConfigManager.isEnchantmentTableEnabled()) return;
		EnchantmentScreen screen = (EnchantmentScreen) (Object) this;
		if (!screen.getMenu().getSlot(0).getItem().isEmpty()) return;

		Identifier texture = (madokuCraft$slotIconTick / 40) % 2 == 0 ? MADOKU_BOOK_SLOT : MADOKU_BOTTLE_SLOT;
		AbstractContainerScreenAccessor screenAccess = (AbstractContainerScreenAccessor) (Object) this;
		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			texture,
			screenAccess.madokuCraft$getLeftPos() + 15,
			screenAccess.madokuCraft$getTopPos() + 47,
			0.0F,
			0.0F,
			16,
			16,
			16,
			16
		);
	}
}

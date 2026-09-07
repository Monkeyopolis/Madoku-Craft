package madoku.craft.mixin.itemstack;

import madoku.craft.java.items.ItemsAPIManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the item-category durability presentation owned by Items. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsItemRarityBarMixin {
	private static final int SLOT_SIZE = 16;
	private static final int DURABILITY_BAR_Y_OFFSET = 1;
	private static final int DURABILITY_BAR_MAX_WIDTH = 14;
	private static final int DURABILITY_BAR_CENTER_X_OFFSET = (SLOT_SIZE - DURABILITY_BAR_MAX_WIDTH) / 2;
	private static final int DURABILITY_BAR_BG_HEIGHT = 2;
	private static final int DURABILITY_BAR_FILL_HEIGHT = 1;
	private static final int DURABILITY_BAR_BG_COLOR = 0xFF000000;

	@Inject(
		method = "itemBar(Lnet/minecraft/world/item/ItemStack;II)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$hideVanillaBottomDurabilityBar(ItemStack stack, int x, int y, CallbackInfo ci) {
		if (!stack.isEmpty() && ItemsAPIManager.isRarityCategoryItem(stack)) {
			ci.cancel();
		}
	}

	@Inject(
		method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
		at = @At("TAIL")
	)
	private void madokuCraft$drawItemDurabilityBar(
		Font textRenderer,
		ItemStack stack,
		int x,
		int y,
		String stackCountText,
		CallbackInfo ci
	) {
		if (stack == null || stack.isEmpty() || !ItemsAPIManager.isRarityCategoryItem(stack) || !stack.isBarVisible()) {
			return;
		}

		GuiGraphicsExtractor context = (GuiGraphicsExtractor) (Object) this;
		int barX = x + DURABILITY_BAR_CENTER_X_OFFSET;
		int barY = y + DURABILITY_BAR_Y_OFFSET;
		int fillWidth = stack.getBarWidth();
		int fillColor = stack.getBarColor() | 0xFF000000;
		context.fill(RenderPipelines.GUI, barX, barY, barX + DURABILITY_BAR_MAX_WIDTH, barY + DURABILITY_BAR_BG_HEIGHT, DURABILITY_BAR_BG_COLOR);
		context.fill(RenderPipelines.GUI, barX, barY, barX + fillWidth, barY + DURABILITY_BAR_FILL_HEIGHT, fillColor);
	}
}

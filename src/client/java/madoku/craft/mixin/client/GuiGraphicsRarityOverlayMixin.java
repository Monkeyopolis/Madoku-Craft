package madoku.craft.mixin.client;

import madoku.craft.item.system.MadokuItem;
import madoku.craft.rarity.MadokuRarity;
import madoku.craft.rarity.MadokuRarityTier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsRarityOverlayMixin {
	private static final int INDICATOR_X_OFFSET = -1;
	private static final int INDICATOR_Y_OFFSET = 5;
	private static final int SLOT_SIZE = 16;
	private static final int DURABILITY_BAR_Y_OFFSET = 1;
	private static final int DURABILITY_BAR_MAX_WIDTH = 14;
	private static final int DURABILITY_BAR_CENTER_X_OFFSET = (SLOT_SIZE - DURABILITY_BAR_MAX_WIDTH) / 2;
	private static final int DURABILITY_BAR_BG_HEIGHT = 2;
	private static final int DURABILITY_BAR_FILL_HEIGHT = 1;
	private static final int DURABILITY_BAR_BG_COLOR = 0xFF000000;

	@Shadow
	public abstract void drawString(Font font, String text, int x, int y, int color, boolean shadow);

	@Inject(
		method = "renderItemBar(Lnet/minecraft/world/item/ItemStack;II)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$hideVanillaBottomDurabilityBar(ItemStack stack, int x, int y, CallbackInfo ci) {
		if (!stack.isEmpty() && MadokuItem.isRarityCategoryItem(stack)) {
			ci.cancel();
		}
	}

	@Inject(
		method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
		at = @At("TAIL")
	)
	private void madokuCraft$drawRarityIndicator(
		Font textRenderer,
		ItemStack stack,
		int x,
		int y,
		String stackCountText,
		CallbackInfo ci
	) {
		if (stack.isEmpty()) {
			return;
		}

		boolean managedRarityItem = MadokuItem.isRarityCategoryItem(stack);
		GuiGraphics context = (GuiGraphics) (Object) this;

		if (managedRarityItem && stack.isBarVisible()) {
			drawTopDurabilityBar(context, stack, x, y);
		}

		if (!MadokuRarity.isEnabled()) {
			return;
		}
		if (!managedRarityItem) {
			return;
		}
		if (stackCountText != null || stack.getCount() > 1) {
			return;
		}

		MadokuRarityTier rarity = MadokuRarity.detectAppliedRarity(stack);
		if (rarity == null) {
			rarity = MadokuRarityTier.COMMON;
		}

		String indicator = rarity.inventoryIndicator();
		int indicatorWidth = textRenderer.width(indicator);
		int indicatorHeight = textRenderer.lineHeight;
		int textX = x + 17 - indicatorWidth + INDICATOR_X_OFFSET;
		int textY = y + 16 - indicatorHeight + INDICATOR_Y_OFFSET;
		Integer colorValue = rarity.color().getColor();
		int textColor = (colorValue != null ? colorValue : 0xFFFFFF) | 0xFF000000;

		this.drawString(textRenderer, indicator, textX, textY, textColor, true);
	}

	private static void drawTopDurabilityBar(GuiGraphics context, ItemStack stack, int x, int y) {
		int barX = x + DURABILITY_BAR_CENTER_X_OFFSET;
		int barY = y + DURABILITY_BAR_Y_OFFSET;
		int fillWidth = stack.getBarWidth();
		int fillColor = stack.getBarColor() | 0xFF000000;

		context.fill(RenderPipelines.GUI, barX, barY, barX + DURABILITY_BAR_MAX_WIDTH, barY + DURABILITY_BAR_BG_HEIGHT, DURABILITY_BAR_BG_COLOR);
		context.fill(RenderPipelines.GUI, barX, barY, barX + fillWidth, barY + DURABILITY_BAR_FILL_HEIGHT, fillColor);
	}

}

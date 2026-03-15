package madoku.craft.mixin.client;

import madoku.craft.item.system.MadokuItem;
import madoku.craft.rarity.MadokuRarity;
import madoku.craft.rarity.MadokuRarityTier;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsRarityOverlayMixin {
	private static final int INDICATOR_X_OFFSET = -1;
	private static final int INDICATOR_Y_OFFSET = 5;
	private static final float DECORATION_DEPTH = 200.0f;
	private static final int SLOT_SIZE = 16;
	private static final int DURABILITY_BAR_Y_OFFSET = 1;
	private static final int DURABILITY_BAR_MAX_WIDTH = 14;
	private static final int DURABILITY_BAR_CENTER_X_OFFSET = (SLOT_SIZE - DURABILITY_BAR_MAX_WIDTH) / 2;
	private static final int DURABILITY_BAR_BG_HEIGHT = 2;
	private static final int DURABILITY_BAR_FILL_HEIGHT = 1;
	private static final int DURABILITY_BAR_BG_COLOR = 0xFF000000;

	@Shadow
	@Final
	private PoseStack pose;

	@Shadow
	public abstract int drawString(Font font, String text, int x, int y, int color, boolean shadow);

	@Redirect(
		method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;isBarVisible()Z"
		)
	)
	private boolean madokuCraft$hideVanillaBottomDurabilityBar(ItemStack stack) {
		if (!stack.isEmpty() && MadokuItem.isRarityCategoryItem(stack)) {
			return false;
		}
		return stack.isBarVisible();
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
		this.pose.pushPose();
		this.pose.translate(0.0f, 0.0f, DECORATION_DEPTH);
		try {
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
		} finally {
			this.pose.popPose();
		}
	}

	private static void drawTopDurabilityBar(GuiGraphics context, ItemStack stack, int x, int y) {
		int barX = x + DURABILITY_BAR_CENTER_X_OFFSET;
		int barY = y + DURABILITY_BAR_Y_OFFSET;
		int fillWidth = stack.getBarWidth();
		int fillColor = stack.getBarColor() | 0xFF000000;

		context.fill(barX, barY, barX + DURABILITY_BAR_MAX_WIDTH, barY + DURABILITY_BAR_BG_HEIGHT, DURABILITY_BAR_BG_COLOR);
		context.fill(barX, barY, barX + fillWidth, barY + DURABILITY_BAR_FILL_HEIGHT, fillColor);
	}

}

package madoku.craft.mixin.core;

import madoku.craft.java.core.rarity.RarityAPIManager;
import madoku.craft.java.core.rarity.RarityAPIManager.Tier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsRarityOverlayMixin {
	private static final int INDICATOR_X_OFFSET = 4;
	private static final int INDICATOR_Y_OFFSET = 4;
	private static final float INDICATOR_SCALE = 0.8F;

	@Shadow
	public abstract void text(Font font, String text, int x, int y, int color, boolean shadow);

	@Inject(
		method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
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

		if (!RarityAPIManager.isEnabled()) {
			return;
		}
		if (stackCountText != null || stack.getCount() > 1) {
			return;
		}

		if (!RarityAPIManager.isRarityItem(stack)) {
			return;
		}

		Tier rarity = RarityAPIManager.detectAppliedRarity(stack);
		if (rarity == null) {
			return;
		}

		String indicator = rarity.inventoryIndicator();
		int indicatorWidth = textRenderer.width(indicator);
		int indicatorHeight = textRenderer.lineHeight;
		Integer colorValue = net.minecraft.network.chat.TextColor.fromLegacyFormat(rarity.color()).getValue();
		int textColor = (colorValue != null ? colorValue : 0xFFFFFF) | 0xFF000000;
		GuiGraphicsExtractor context = (GuiGraphicsExtractor) (Object) this;

		context.pose().pushMatrix();
		context.pose().translate(x + 12 + INDICATOR_X_OFFSET, y + 16 + INDICATOR_Y_OFFSET);
		context.pose().scale(INDICATOR_SCALE, INDICATOR_SCALE);
		this.text(textRenderer, indicator, -indicatorWidth, -indicatorHeight, textColor, true);
		context.pose().popMatrix();
	}

}

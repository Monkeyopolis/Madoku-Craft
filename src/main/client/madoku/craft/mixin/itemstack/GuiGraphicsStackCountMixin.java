package madoku.craft.mixin.itemstack;

import madoku.craft.java.items.ItemsStacksAPIManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsStackCountMixin {
	private static final float STACK_COUNT_DEFAULT_SCALE = 1.0f;
	private static final int STACK_COUNT_DEFAULT_TEXT_LENGTH = 2;
	private static final float STACK_COUNT_DECIMAL_SCALE_REDUCTION = 0.05f;
	private static final float STACK_COUNT_ADDITIONAL_TEXT_SCALE_REDUCTION = 0.15f;

	@Shadow
	@Final
	private Matrix3x2fStack pose;

	@Shadow
	public abstract void text(Font font, String text, int x, int y, int color, boolean shadow);

	@Redirect(
		method = "itemCount(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"
		)
	)
	private void madokuCraft$scaleLargeStackCounts(
		GuiGraphicsExtractor guiGraphics,
		Font font,
		String text,
		int x,
		int y,
		int color,
		boolean shadow,
		Font textRenderer,
		ItemStack stack,
		int itemX,
		int itemY,
		String stackCountText
	) {
		String renderedText = resolveDisplayText(stack, text, stackCountText);
		int drawX = x;
		if (renderedText != null && text != null && !renderedText.equals(text)) {
			drawX += font.width(text) - font.width(renderedText);
		}

		float scale = resolveScale(renderedText);
		if (scale >= 0.999f) {
			this.text(font, renderedText, drawX, y, color, shadow);
			return;
		}

		int width = font.width(renderedText);
		float bottomY = y + font.lineHeight;
		this.pose.pushMatrix();
		this.pose.translate((float) (drawX + width), bottomY);
		this.pose.scale(scale, scale);
		this.pose.translate((float) (-drawX - width), -bottomY);
		this.text(font, renderedText, drawX, y, color, shadow);
		this.pose.popMatrix();
	}

	private static String resolveDisplayText(ItemStack stack, String originalText, String explicitText) {
		if (originalText == null || originalText.isBlank()) {
			return originalText;
		}
		if (explicitText != null && !explicitText.isBlank()) {
			return originalText;
		}
		if (stack == null || stack.isEmpty()) {
			return originalText;
		}
		int count = stack.getCount();
		if (count < 1000) {
			return originalText;
		}
		return ItemsStacksAPIManager.formatCompactStackCount(count);
	}

	private static float resolveScale(String text) {
		if (text == null || text.isBlank()) {
			return 1.0f;
		}

		float scale = STACK_COUNT_DEFAULT_SCALE;
		int additionalTextLength = Math.max(0, text.length() - STACK_COUNT_DEFAULT_TEXT_LENGTH);
		scale -= additionalTextLength * STACK_COUNT_ADDITIONAL_TEXT_SCALE_REDUCTION;
		if (text.indexOf('.') >= 0) {
			scale -= STACK_COUNT_DECIMAL_SCALE_REDUCTION;
		}

		return Math.max(0.1f, scale);
	}
}

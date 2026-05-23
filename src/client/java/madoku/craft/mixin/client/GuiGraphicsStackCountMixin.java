package madoku.craft.mixin.client;

import madoku.craft.itemstack.system.MadokuItemStack;
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
	private static final float STACK_COUNT_SCALE_LARGE = 0.9f;
	private static final float STACK_COUNT_SCALE_COMPACT = 0.65f;
	private static final float STACK_COUNT_SCALE_DECIMAL = 0.7f;

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

		float scale = resolveScale(renderedText, stack);
		if (scale >= 0.999f) {
			this.text(font, renderedText, drawX, y, color, shadow);
			return;
		}

		int width = font.width(renderedText);
		this.pose.pushMatrix();
		this.pose.translate((float) (drawX + width), (float) y);
		this.pose.scale(scale, scale);
		this.pose.translate((float) (-drawX - width), (float) (-y));
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
		return MadokuItemStack.formatCompactStackCount(count);
	}

	private static float resolveScale(String text, ItemStack stack) {
		if (text != null && text.indexOf('.') >= 0) {
			return STACK_COUNT_SCALE_DECIMAL;
		}
		if (stack != null && !stack.isEmpty()) {
			int count = stack.getCount();
			if (count > 999) {
				return STACK_COUNT_SCALE_COMPACT;
			}
			if (count > 99) {
				return STACK_COUNT_SCALE_LARGE;
			}
			return 1.0f;
		}
		if (text == null || text.isBlank()) {
			return 1.0f;
		}
		try {
			int parsed = Integer.parseInt(text);
			if (parsed > 999) {
				return STACK_COUNT_SCALE_COMPACT;
			}
			if (parsed > 99) {
				return STACK_COUNT_SCALE_LARGE;
			}
			return 1.0f;
		} catch (NumberFormatException ignored) {
			return text.length() > 2 ? STACK_COUNT_SCALE_LARGE : 1.0f;
		}
	}
}

package madoku.craft.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsStackCountMixin {
	private static final float STACK_COUNT_SCALE = 0.9f;

	@Shadow
	@Final
	private Matrix3x2fStack pose;

	@Shadow
	public abstract void drawString(Font font, String text, int x, int y, int color, boolean shadow);

	@Redirect(
		method = "renderItemCount(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"
		)
	)
	private void madokuCraft$scaleLargeStackCounts(
		GuiGraphics guiGraphics,
		Font font,
		String text,
		int x,
		int y,
		int color,
		boolean shadow
	) {
		if (!shouldScale(text)) {
			this.drawString(font, text, x, y, color, shadow);
			return;
		}

		int width = font.width(text);
		this.pose.pushMatrix();
		this.pose.translate((float) (x + width), (float) y);
		this.pose.scale(STACK_COUNT_SCALE, STACK_COUNT_SCALE);
		this.pose.translate((float) (-x - width), (float) (-y));
		this.drawString(font, text, x, y, color, shadow);
		this.pose.popMatrix();
	}

	private static boolean shouldScale(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		try {
			return Integer.parseInt(text) > 99;
		} catch (NumberFormatException ignored) {
			return text.length() > 2;
		}
	}
}

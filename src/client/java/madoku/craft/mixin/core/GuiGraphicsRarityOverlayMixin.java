package madoku.craft.mixin.core;

import madoku.craft.core.rarity.MadokuRarityManager;
import madoku.craft.core.rarity.RarityTierManager.Tier;
import madoku.craft.items.MadokuItemsManager;
import madoku.craft.pet.PetEntitiesManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
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
	private static final int SLOT_SIZE = 16;
	private static final int DURABILITY_BAR_Y_OFFSET = 1;
	private static final int DURABILITY_BAR_MAX_WIDTH = 14;
	private static final int DURABILITY_BAR_CENTER_X_OFFSET = (SLOT_SIZE - DURABILITY_BAR_MAX_WIDTH) / 2;
	private static final int DURABILITY_BAR_BG_HEIGHT = 2;
	private static final int DURABILITY_BAR_FILL_HEIGHT = 1;
	private static final int DURABILITY_BAR_BG_COLOR = 0xFF000000;

	@Shadow
	public abstract void text(Font font, String text, int x, int y, int color, boolean shadow);

	@Inject(
		method = "itemBar(Lnet/minecraft/world/item/ItemStack;II)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$hideVanillaBottomDurabilityBar(ItemStack stack, int x, int y, CallbackInfo ci) {
		if (!stack.isEmpty() && MadokuItemsManager.isRarityCategoryItem(stack)) {
			ci.cancel();
		}
	}

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

		boolean managedRarityItem = MadokuItemsManager.isRarityCategoryItem(stack)
			|| PetEntitiesManager.isPetItem(stack);
		GuiGraphicsExtractor context = (GuiGraphicsExtractor) (Object) this;

		if (managedRarityItem && stack.isBarVisible()) {
			drawTopDurabilityBar(context, stack, x, y);
		}

		if (!MadokuRarityManager.isEnabled()) {
			return;
		}
		if (!managedRarityItem) {
			return;
		}
		if (stackCountText != null || stack.getCount() > 1) {
			return;
		}

		Tier rarity = MadokuRarityManager.detectAppliedRarity(stack);
		if (rarity == null) {
			return;
		}

		String indicator = rarity.inventoryIndicator();
		int indicatorWidth = textRenderer.width(indicator);
		int indicatorHeight = textRenderer.lineHeight;
		Integer colorValue = net.minecraft.network.chat.TextColor.fromLegacyFormat(rarity.color()).getValue();
		int textColor = (colorValue != null ? colorValue : 0xFFFFFF) | 0xFF000000;

		context.pose().pushMatrix();
		context.pose().translate(x + 12 + INDICATOR_X_OFFSET, y + 16 + INDICATOR_Y_OFFSET);
		context.pose().scale(INDICATOR_SCALE, INDICATOR_SCALE);
		this.text(textRenderer, indicator, -indicatorWidth, -indicatorHeight, textColor, true);
		context.pose().popMatrix();
	}

	private static void drawTopDurabilityBar(GuiGraphicsExtractor context, ItemStack stack, int x, int y) {
		int barX = x + DURABILITY_BAR_CENTER_X_OFFSET;
		int barY = y + DURABILITY_BAR_Y_OFFSET;
		int fillWidth = stack.getBarWidth();
		int fillColor = stack.getBarColor() | 0xFF000000;

		context.fill(RenderPipelines.GUI, barX, barY, barX + DURABILITY_BAR_MAX_WIDTH, barY + DURABILITY_BAR_BG_HEIGHT, DURABILITY_BAR_BG_COLOR);
		context.fill(RenderPipelines.GUI, barX, barY, barX + fillWidth, barY + DURABILITY_BAR_FILL_HEIGHT, fillColor);
	}

}

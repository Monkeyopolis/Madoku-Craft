package madoku.craft.trade;

import madoku.craft.mixin.inventory.AbstractContainerScreenAccessor;
import madoku.craft.mixin.trade.MerchantScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public final class MerchantEggVariantsClient {
	private static final Identifier DISCOUNT_STRIKETHROUGH_SPRITE =
		Identifier.withDefaultNamespace("container/villager/discount_strikethrough");
	private static final Item[] EGG_VARIANTS = {Items.EGG, Items.BLUE_EGG, Items.BROWN_EGG};
	private static final int OFFER_ROWS = 7;
	private static final int COST_X = 10;
	private static final int FIRST_ROW_Y = 19;
	private static final int ROW_SPACING = 20;

	private MerchantEggVariantsClient() {
	}

	public static void initialize() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof MerchantScreen merchantScreen)) {
				return;
			}

			ScreenEvents.afterExtract(merchantScreen).register((currentScreen, graphics, mouseX, mouseY, tickProgress) -> {
				renderEggVariants(merchantScreen, graphics);
			});
		});
	}

	private static void renderEggVariants(MerchantScreen screen, GuiGraphicsExtractor graphics) {
		if (!(screen.getMenu() instanceof MerchantMenu menu)) {
			return;
		}

		MerchantOffers offers = menu.getOffers();
		if (offers == null || offers.isEmpty()) {
			return;
		}

		int leftPos = ((AbstractContainerScreenAccessor) screen).madokuCraft$getLeftPos();
		int topPos = ((AbstractContainerScreenAccessor) screen).madokuCraft$getTopPos();
		int scrollOff = ((MerchantScreenAccessor) screen).madokuCraft$getScrollOff();
		int rowCount = Math.min(OFFER_ROWS, offers.size() - scrollOff);
		for (int row = 0; row < rowCount; row++) {
			MerchantOffer offer = offers.get(scrollOff + row);
			if (offer == null) {
				continue;
			}
			renderEggCost(graphics, offer.getCostA(), offer.getBaseCostA(), leftPos + COST_X, topPos + FIRST_ROW_Y + (row * ROW_SPACING));
		}
	}

	private static void renderEggCost(GuiGraphicsExtractor graphics, ItemStack realCost, ItemStack baseCost, int x, int y) {
		if (!realCost.is(Items.EGG) && !baseCost.is(Items.EGG)) {
			return;
		}

		Font font = Minecraft.getInstance().font;
		ItemStack displayRealCost = displayStack(realCost);
		ItemStack displayBaseCost = displayStack(baseCost);
		graphics.item(displayRealCost, x, y);

		if (baseCost.getCount() == realCost.getCount()) {
			graphics.itemDecorations(font, displayRealCost, x, y);
			return;
		}

		graphics.itemDecorations(font, displayBaseCost, x, y, baseCost.getCount() == 1 ? "1" : null);
		graphics.itemDecorations(font, displayRealCost, x + 14, y, realCost.getCount() == 1 ? "1" : null);
		graphics.blitSprite(RenderPipelines.GUI, DISCOUNT_STRIKETHROUGH_SPRITE, x + 7, y + 12, 9, 2);
	}

	private static ItemStack displayStack(ItemStack original) {
		if (!original.is(Items.EGG)) {
			return original;
		}

		Item item = EGG_VARIANTS[(int) ((System.currentTimeMillis() / 1000L) % EGG_VARIANTS.length)];
		return new ItemStack(item, original.getCount());
	}
}

package madoku.craft.levels;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class MadokuLevelsScreen extends Screen {
	private static final Identifier BACKGROUND_TEXTURE_ATTRIBUTES =
		Identifier.fromNamespaceAndPath("madoku-craft", "textures/containers/player_levels_attributes.png");
	private static final Identifier BACKGROUND_TEXTURE_VANILLA =
		Identifier.fromNamespaceAndPath("madoku-craft", "textures/containers/player_levels_vanilla.png");
	private static final Identifier ENTRY_TEXTURE =
		Identifier.fromNamespaceAndPath("madoku-craft", "textures/rows/player_entries.png");
	private static final Identifier XP_BAR_BACKGROUND_TEXTURE = Identifier.withDefaultNamespace("hud/experience_bar_background");
	private static final Identifier XP_BAR_PROGRESS_TEXTURE = Identifier.withDefaultNamespace("hud/experience_bar_progress");
	private static final int TEXTURE_SIZE = 256;
	private static final int PANEL_WIDTH = 176;
	private static final int PANEL_HEIGHT = 165;
	private static final int TOP_MARGIN = 8;
	private static final int XP_BAR_WIDTH = 148;
	private static final int XP_BAR_HEIGHT = 5;
	private static final int ENTRY_WIDTH = 80;
	private static final int ENTRY_HEIGHT = 30;
	private static final int ENTRY_GAP = 5;
	private static final int ENTRY_COLUMN_GAP = 4;
	private static final int ATTRIBUTE_ENTRY_LIMIT = 6;
	private static final int VANILLA_ENTRY_LIMIT = 4;
	private static final int ENTRY_BUTTON_WIDTH = 12;
	private static final int ENTRY_BUTTON_HEIGHT = 12;
	private static final int BOTTOM_MARGIN = 7;
	private static final int ENTRY_SIDE_INSET = 4;
	private static final int ENTRY_BUTTON_RIGHT_INSET = 4;
	private static final int SUBTEXT_COLOR = 0xFF404040;
	private static final int ICON_SIZE = 16;
	private static final int ICON_TEXTURE_SIZE = 16;
	private static final float HEADER_TEXT_SCALE = 0.9F;
	private static final float INFO_TEXT_SCALE = 0.8F;
	private static final float ENTRY_TITLE_SCALE = 0.8F;
	private static final float ENTRY_TEXT_SCALE = 0.7F;

	private int stateVersion = -1;

	public MadokuLevelsScreen() {
		super(Component.literal("MadokuLevels"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		this.addRenderableOnly((guiGraphics, mouseX, mouseY, partialTick) -> {
			guiGraphics.fill(RenderPipelines.GUI, 0, 0, this.width, this.height, 0x88000000);

			int panelX = panelX();
			int panelY = panelY();
			MadokuLevelsClientState.Snapshot snapshot = MadokuLevelsClientState.snapshot();

			guiGraphics.blit(
				RenderPipelines.GUI_TEXTURED,
				backgroundTexture(snapshot),
				panelX,
				panelY,
				0.0F,
				0.0F,
				PANEL_WIDTH,
				PANEL_HEIGHT,
				TEXTURE_SIZE,
				TEXTURE_SIZE
			);

			if (!snapshot.hasData()) {
				String waitingTitle = "MadokuLevels";
				String waitingText = "Waiting for level data...";
				drawScaledCenteredText(guiGraphics, waitingTitle, this.width / 2, panelY + 18, HEADER_TEXT_SCALE, 0xFF404040);
				drawScaledCenteredText(guiGraphics, waitingText, this.width / 2, panelY + 32, INFO_TEXT_SCALE, SUBTEXT_COLOR);
				return;
			}

			int usernameY = panelY + TOP_MARGIN;
			int xpBarX = panelX + (PANEL_WIDTH - XP_BAR_WIDTH) / 2;
			int infoY = usernameY + 10;
			int xpBarY = infoY + 7;
			int xpTextY = xpBarY + XP_BAR_HEIGHT + 1;
			int entriesTop = entriesTop();
			List<MadokuLevelStat> stats = snapshot.visibleStats();
			int maxVisibleEntries = maxVisibleEntries(snapshot.useAttributesContainer(), entriesTop);
			int visibleEntries = Math.min(stats.size(), maxVisibleEntries);

			String username = snapshot.username();
			String levelText = "Level: " + snapshot.level();
			String pointsText = "Points: " + snapshot.availablePoints();
			String xpText = snapshot.currentXp() + " / " + snapshot.requiredXp();

			drawScaledCenteredText(guiGraphics, username, this.width / 2, usernameY, HEADER_TEXT_SCALE, 0xFF404040);
			int pointsTextWidth = scaledTextWidth(pointsText, INFO_TEXT_SCALE);
			drawScaledLeftText(guiGraphics, levelText, xpBarX, infoY, INFO_TEXT_SCALE, 0xFF404040);
			drawScaledLeftText(guiGraphics, pointsText, xpBarX + XP_BAR_WIDTH - pointsTextWidth, infoY, INFO_TEXT_SCALE, 0xFF404040);

			int xpFilledWidth = snapshot.requiredXp() <= 0
				? 0
				: Math.max(0, Math.min(XP_BAR_WIDTH, Math.round((snapshot.currentXp() / (float) snapshot.requiredXp()) * XP_BAR_WIDTH)));
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BAR_BACKGROUND_TEXTURE, xpBarX, xpBarY, XP_BAR_WIDTH, XP_BAR_HEIGHT);
			if (xpFilledWidth > 0) {
				guiGraphics.enableScissor(xpBarX, xpBarY, xpBarX + xpFilledWidth, xpBarY + XP_BAR_HEIGHT);
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BAR_PROGRESS_TEXTURE, xpBarX, xpBarY, XP_BAR_WIDTH, XP_BAR_HEIGHT);
				guiGraphics.disableScissor();
			}
			drawScaledCenteredText(guiGraphics, xpText, this.width / 2, xpTextY, INFO_TEXT_SCALE, SUBTEXT_COLOR);

			int gridLeftX = panelX + (PANEL_WIDTH - ((ENTRY_WIDTH * 2) + ENTRY_COLUMN_GAP)) / 2;
			for (int index = 0; index < visibleEntries; index++) {
				MadokuLevelStat stat = stats.get(index);
				int row = index / 2;
				int column = index % 2;
				int entryX = gridLeftX + column * (ENTRY_WIDTH + ENTRY_COLUMN_GAP);
				int entryY = entriesTop + row * (ENTRY_HEIGHT + ENTRY_GAP);
				int iconSlotX = entryX + ENTRY_SIDE_INSET;
				int iconCenterY = entryY + (ENTRY_HEIGHT / 2);
				int iconX = iconSlotX;
				int iconY = iconCenterY - (ICON_SIZE / 2);
				int buttonX = entryX + ENTRY_WIDTH - ENTRY_BUTTON_WIDTH - ENTRY_BUTTON_RIGHT_INSET;
				int textLeft = iconX + ICON_SIZE + 4;
				int textRight = buttonX - 4;
				int textCenterX = textLeft + Math.max(0, (textRight - textLeft) / 2);
				int statLevel = snapshot.statLevel(stat);
				String statLevelText = statLevel + "/" + snapshot.maxStatLevel();

				guiGraphics.blit(
					RenderPipelines.GUI_TEXTURED,
					ENTRY_TEXTURE,
					entryX,
					entryY,
					0.0F,
					0.0F,
					ENTRY_WIDTH,
					ENTRY_HEIGHT,
					ENTRY_WIDTH,
					ENTRY_HEIGHT
				);

				guiGraphics.blit(
					RenderPipelines.GUI_TEXTURED,
					stat.iconTexture(),
					iconX,
					iconY,
					0.0F,
					0.0F,
					ICON_SIZE,
					ICON_SIZE,
					ICON_TEXTURE_SIZE,
					ICON_TEXTURE_SIZE
				);
				drawScaledCenteredText(guiGraphics, stat.label(), textCenterX, entryY + 5, ENTRY_TITLE_SCALE, 0xFF404040);
				drawScaledCenteredText(guiGraphics, statLevelText, textCenterX, entryY + 18, ENTRY_TEXT_SCALE, SUBTEXT_COLOR);
			}
		});
		rebuildStatButtons();
	}

	@Override
	public void tick() {
		super.tick();
		if (stateVersion != MadokuLevelsClientState.version()) {
			rebuildWidgets();
		}
	}

	private void rebuildStatButtons() {
		stateVersion = MadokuLevelsClientState.version();
		MadokuLevelsClientState.Snapshot snapshot = MadokuLevelsClientState.snapshot();
		int panelX = panelX();
		int entriesTop = entriesTop();
		List<MadokuLevelStat> stats = snapshot.visibleStats();
		int maxVisibleEntries = maxVisibleEntries(snapshot.useAttributesContainer(), entriesTop);
		int visibleEntries = Math.min(stats.size(), maxVisibleEntries);
		int gridLeftX = panelX + (PANEL_WIDTH - ((ENTRY_WIDTH * 2) + ENTRY_COLUMN_GAP)) / 2;

		for (int index = 0; index < visibleEntries; index++) {
			MadokuLevelStat stat = stats.get(index);
			int row = index / 2;
			int column = index % 2;
			int entryX = gridLeftX + column * (ENTRY_WIDTH + ENTRY_COLUMN_GAP);
			int entryY = entriesTop + row * (ENTRY_HEIGHT + ENTRY_GAP);
			int buttonX = entryX + ENTRY_WIDTH - ENTRY_BUTTON_WIDTH - ENTRY_BUTTON_RIGHT_INSET;
			int buttonY = entryY + (ENTRY_HEIGHT - ENTRY_BUTTON_HEIGHT) / 2;

			Button button = Button.builder(Component.literal("+"), pressed -> MadokuLevelsClient.requestStatUpgrade(stat))
				.bounds(buttonX, buttonY, ENTRY_BUTTON_WIDTH, ENTRY_BUTTON_HEIGHT)
				.build();
			button.active = snapshot.hasData()
				&& snapshot.availablePoints() > 0
				&& snapshot.statLevel(stat) < snapshot.maxStatLevel();
			this.addRenderableWidget(button);
		}
	}

	private int panelX() {
		return (this.width - PANEL_WIDTH) / 2;
	}

	private Identifier backgroundTexture(MadokuLevelsClientState.Snapshot snapshot) {
		return snapshot.useAttributesContainer() ? BACKGROUND_TEXTURE_ATTRIBUTES : BACKGROUND_TEXTURE_VANILLA;
	}

	private int panelY() {
		return (this.height - PANEL_HEIGHT) / 2;
	}

	private int panelBottom() {
		return panelY() + PANEL_HEIGHT;
	}

	private int entriesTop() {
		return panelY() + 47;
	}

	private int rowsThatFit(int entriesTop) {
		int availableHeight = panelBottom() - entriesTop - BOTTOM_MARGIN;
		return Math.max(1, (availableHeight + ENTRY_GAP) / (ENTRY_HEIGHT + ENTRY_GAP));
	}

	private int maxVisibleEntries(boolean useAttributesContainer, int entriesTop) {
		int rowLimited = rowsThatFit(entriesTop) * 2;
		int containerLimited = useAttributesContainer ? ATTRIBUTE_ENTRY_LIMIT : VANILLA_ENTRY_LIMIT;
		return Math.min(rowLimited, containerLimited);
	}

	private void drawScaledLeftText(
		Object guiGraphics,
		String text,
		int x,
		int y,
		float scale,
		int color
	) {
		if (text == null || text.isEmpty()) {
			return;
		}

		var graphics = guiGraphics;
		((net.minecraft.client.gui.GuiGraphicsExtractor) graphics).pose().pushMatrix();
		((net.minecraft.client.gui.GuiGraphicsExtractor) graphics).pose().scale(scale, scale);
		((net.minecraft.client.gui.GuiGraphicsExtractor) graphics).text(
			this.font,
			text,
			Math.round(x / scale),
			Math.round(y / scale),
			color,
			false
		);
		((net.minecraft.client.gui.GuiGraphicsExtractor) graphics).pose().popMatrix();
	}

	private void drawScaledCenteredText(Object guiGraphics, String text, int centerX, int y, float scale, int color) {
		int startX = Math.round(centerX - (this.font.width(text) * scale / 2.0F));
		drawScaledLeftText(guiGraphics, text, startX, y, scale, color);
	}

	private int scaledTextWidth(String text, float scale) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		return Math.round(this.font.width(text) * scale);
	}
}

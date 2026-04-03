package madoku.craft.inventory;

import madoku.craft.mixin.client.AbstractContainerScreenAccessor;
import madoku.craft.mixin.client.SlotAccessor;
import madoku.craft.pet.PlayerEntitiesSystem;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class PlayerEntitiesInventoryClient {
	private static final Identifier INVENTORY_TEXTURE =
		Identifier.fromNamespaceAndPath("madoku-craft", "textures/containers/trinket_inventory.png");
	private static final Identifier RECIPE_BUTTON_TEXTURE =
		Identifier.fromNamespaceAndPath("madoku-craft", "textures/icons/button.png");
	private static final Identifier RECIPE_BUTTON_HIGHLIGHTED_TEXTURE =
		Identifier.fromNamespaceAndPath("madoku-craft", "textures/icons/button_highlighted.png");
	private static final Identifier ENTITY_SLOT_TEXTURE =
		Identifier.fromNamespaceAndPath("madoku-craft", "textures/icons/trinket.png");
	private static final int TEXTURE_SIZE = 256;
	private static final int INVENTORY_WIDTH = 176;
	private static final int INVENTORY_HEIGHT = 166;
	private static final int SLOT_SIZE = 16;
	private static final int RECIPE_BUTTON_WIDTH = 20;
	private static final int RECIPE_BUTTON_HEIGHT = 18;
	private static final int RECIPE_BUTTON_ICON_SIZE = 18;
	private static final int DEFAULT_RECIPE_BUTTON_X_OFFSET = 104;
	private static final int DEFAULT_RECIPE_BUTTON_Y_OFFSET = -22;
	private static final int RECIPE_BUTTON_MATCH_TOLERANCE = 12;
	private static final int RECIPE_BUTTON_X = 95;
	private static final int RECIPE_BUTTON_Y = 61;
	private static final int OFFHAND_SLOT_INDEX = 45;
	private static final int OFFHAND_SLOT_X = 117;
	private static final int OFFHAND_SLOT_Y = 62;
	private static final int PLAYER_PREVIEW_LEFT = 26;
	private static final int PLAYER_PREVIEW_TOP = 8;
	private static final int PLAYER_PREVIEW_RIGHT = 75;
	private static final int PLAYER_PREVIEW_BOTTOM = 78;
	private static final int PLAYER_PREVIEW_SCALE = 30;
	private static final float PLAYER_PREVIEW_VERTICAL_OFFSET = 0.0625F;
	private static Method playerPreviewRenderMethod;
	private static boolean lookedUpPlayerPreviewRenderMethod;

	private PlayerEntitiesInventoryClient() {
	}

	public static void initialize() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof InventoryScreen inventoryScreen)) {
				return;
			}

			ScreenEvents.afterBackground(inventoryScreen).register((currentScreen, graphics, mouseX, mouseY, tickProgress) -> {
				applyLayout(inventoryScreen);
				int leftPos = leftPos(inventoryScreen);
				int topPos = topPos(inventoryScreen);
				graphics.blit(
					RenderPipelines.GUI_TEXTURED,
					INVENTORY_TEXTURE,
					leftPos,
					topPos,
					0.0F,
					0.0F,
					INVENTORY_WIDTH,
					INVENTORY_HEIGHT,
					TEXTURE_SIZE,
					TEXTURE_SIZE
				);
				drawPlayerPreview(inventoryScreen, graphics, mouseX, mouseY);
				drawEntityPlaceholders(inventoryScreen, graphics);
				drawRecipeBookButtonIcon(inventoryScreen, graphics, mouseX, mouseY);
			});

			ScreenMouseEvents.allowMouseClick(inventoryScreen).register((currentScreen, event) -> {
				applyLayout(inventoryScreen);
				AbstractWidget recipeButton = findRecipeButton(inventoryScreen);
				if (recipeButton == null) {
					return true;
				}
				if (!isHovered(recipeButton, (int) event.x(), (int) event.y())) {
					return true;
				}

				recipeButton.onClick(event, false);
				return false;
			});
		});
	}

	private static void applyLayout(InventoryScreen screen) {
		moveOffhandSlot(screen);
		positionRecipeBookButton(screen);
	}

	private static void moveOffhandSlot(InventoryScreen screen) {
		if (OFFHAND_SLOT_INDEX < 0 || OFFHAND_SLOT_INDEX >= screen.getMenu().slots.size()) {
			return;
		}

		Slot offhandSlot = screen.getMenu().slots.get(OFFHAND_SLOT_INDEX);
		((SlotAccessor) offhandSlot).madokuCraft$setX(OFFHAND_SLOT_X);
		((SlotAccessor) offhandSlot).madokuCraft$setY(OFFHAND_SLOT_Y);
	}

	private static void positionRecipeBookButton(InventoryScreen screen) {
		AbstractWidget recipeButton = findRecipeButton(screen);
		if (recipeButton == null) {
			return;
		}

		recipeButton.setX(leftPos(screen) + RECIPE_BUTTON_X);
		recipeButton.setY(topPos(screen) + RECIPE_BUTTON_Y);
		recipeButton.visible = false;
	}

	private static void drawRecipeBookButtonIcon(InventoryScreen screen, Object graphics, int mouseX, int mouseY) {
		AbstractWidget recipeButton = findRecipeButton(screen);
		if (recipeButton == null) {
			return;
		}

		Identifier texture = isHovered(recipeButton, mouseX, mouseY)
			? RECIPE_BUTTON_HIGHLIGHTED_TEXTURE
			: RECIPE_BUTTON_TEXTURE;
		int iconX = recipeButton.getX() + (recipeButton.getWidth() - RECIPE_BUTTON_ICON_SIZE) / 2;
		int iconY = recipeButton.getY() + (recipeButton.getHeight() - RECIPE_BUTTON_ICON_SIZE) / 2;

		((net.minecraft.client.gui.GuiGraphicsExtractor) graphics).blit(
			RenderPipelines.GUI_TEXTURED,
			texture,
			iconX,
			iconY,
			0.0F,
			0.0F,
			RECIPE_BUTTON_ICON_SIZE,
			RECIPE_BUTTON_ICON_SIZE,
			RECIPE_BUTTON_ICON_SIZE,
			RECIPE_BUTTON_ICON_SIZE
		);
	}

	private static void drawEntityPlaceholders(InventoryScreen screen, Object graphics) {
		net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics = (net.minecraft.client.gui.GuiGraphicsExtractor) graphics;
		for (int slotIndex = PlayerEntitiesSystem.FIRST_SLOT_INDEX;
			slotIndex < PlayerEntitiesSystem.FIRST_SLOT_INDEX + PlayerEntitiesSystem.SLOT_COUNT;
			slotIndex++) {
			if (slotIndex >= screen.getMenu().slots.size()) {
				break;
			}

			Slot slot = screen.getMenu().slots.get(slotIndex);
			if (slot == null || !slot.isActive() || slot.hasItem()) {
				continue;
			}

			int iconX = leftPos(screen) + slot.x;
			int iconY = topPos(screen) + slot.y;
			guiGraphics.blit(
				RenderPipelines.GUI_TEXTURED,
				ENTITY_SLOT_TEXTURE,
				iconX,
				iconY,
				0.0F,
				0.0F,
				SLOT_SIZE,
				SLOT_SIZE,
				SLOT_SIZE,
				SLOT_SIZE
			);
		}
	}

	private static void drawPlayerPreview(InventoryScreen screen, Object graphics, int mouseX, int mouseY) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}

		Method method = resolvePlayerPreviewRenderMethod();
		if (method == null) {
			return;
		}

		try {
			method.invoke(
				null,
				graphics,
				leftPos(screen) + PLAYER_PREVIEW_LEFT,
				topPos(screen) + PLAYER_PREVIEW_TOP,
				leftPos(screen) + PLAYER_PREVIEW_RIGHT,
				topPos(screen) + PLAYER_PREVIEW_BOTTOM,
				PLAYER_PREVIEW_SCALE,
				PLAYER_PREVIEW_VERTICAL_OFFSET,
				(float) mouseX,
				(float) mouseY,
				client.player
			);
		} catch (ReflectiveOperationException ignored) {
		}
	}

	private static Method resolvePlayerPreviewRenderMethod() {
		if (lookedUpPlayerPreviewRenderMethod) {
			return playerPreviewRenderMethod;
		}

		lookedUpPlayerPreviewRenderMethod = true;
		for (Method method : InventoryScreen.class.getDeclaredMethods()) {
			Class<?>[] parameterTypes = method.getParameterTypes();
			if (!Modifier.isStatic(method.getModifiers()) || parameterTypes.length != 10) {
				continue;
			}
			if (parameterTypes[0].getSimpleName().contains("GuiGraphics")
				&& parameterTypes[1] == int.class
				&& parameterTypes[2] == int.class
				&& parameterTypes[3] == int.class
				&& parameterTypes[4] == int.class
				&& parameterTypes[5] == int.class
				&& parameterTypes[6] == float.class
				&& parameterTypes[7] == float.class
				&& parameterTypes[8] == float.class
				&& LivingEntity.class.isAssignableFrom(parameterTypes[9])) {
				method.setAccessible(true);
				playerPreviewRenderMethod = method;
				break;
			}
		}

		return playerPreviewRenderMethod;
	}

	private static AbstractWidget findRecipeButton(InventoryScreen screen) {
		int defaultX = leftPos(screen) + DEFAULT_RECIPE_BUTTON_X_OFFSET;
		int defaultY = (screen.height / 2) + DEFAULT_RECIPE_BUTTON_Y_OFFSET;
		int movedX = leftPos(screen) + RECIPE_BUTTON_X;
		int movedY = topPos(screen) + RECIPE_BUTTON_Y;
		AbstractWidget match = null;
		int bestScore = Integer.MAX_VALUE;

		for (AbstractWidget widget : Screens.getWidgets(screen)) {
			if (widget.getWidth() != RECIPE_BUTTON_WIDTH || widget.getHeight() != RECIPE_BUTTON_HEIGHT) {
				continue;
			}

			int defaultScore = Math.abs(widget.getX() - defaultX) + Math.abs(widget.getY() - defaultY);
			int movedScore = Math.abs(widget.getX() - movedX) + Math.abs(widget.getY() - movedY);
			int score = Math.min(defaultScore, movedScore);
			if (score < bestScore) {
				bestScore = score;
				match = widget;
			}
		}

		return bestScore <= RECIPE_BUTTON_MATCH_TOLERANCE ? match : null;
	}

	private static int leftPos(InventoryScreen screen) {
		return ((AbstractContainerScreenAccessor) screen).madokuCraft$getLeftPos();
	}

	private static int topPos(InventoryScreen screen) {
		return ((AbstractContainerScreenAccessor) screen).madokuCraft$getTopPos();
	}

	private static boolean isHovered(AbstractWidget widget, int mouseX, int mouseY) {
		return mouseX >= widget.getX()
			&& mouseX < widget.getRight()
			&& mouseY >= widget.getY()
			&& mouseY < widget.getBottom();
	}
}

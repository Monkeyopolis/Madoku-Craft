package madoku.craft.inventory;

import madoku.craft.mixin.client.AbstractContainerScreenAccessor;
import madoku.craft.mixin.client.CreativeModeInventoryScreenAccessor;
import madoku.craft.mixin.client.SlotAccessor;
import madoku.craft.pet.PlayerEntitiesSystem;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.WeakHashMap;

public final class PlayerEntitiesInventoryClient {
	private static final ResourceLocation SURVIVAL_INVENTORY_TEXTURE =
		ResourceLocation.fromNamespaceAndPath("madoku-craft", "textures/containers/survival_pet_inventory.png");
	private static final ResourceLocation CREATIVE_INVENTORY_TEXTURE =
		ResourceLocation.fromNamespaceAndPath("madoku-craft", "textures/containers/creative_pet_inventory.png");
	private static final ResourceLocation RECIPE_BUTTON_TEXTURE =
		ResourceLocation.fromNamespaceAndPath("madoku-craft", "textures/icons/button.png");
	private static final ResourceLocation RECIPE_BUTTON_HIGHLIGHTED_TEXTURE =
		ResourceLocation.fromNamespaceAndPath("madoku-craft", "textures/icons/button_highlighted.png");
	private static final ResourceLocation ENTITY_SLOT_TEXTURE =
		ResourceLocation.fromNamespaceAndPath("madoku-craft", "textures/icons/trinket.png");
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
	private static final int[] CREATIVE_ARMOR_SLOT_XS = {53, 53, 33, 33};
	private static final int[] CREATIVE_ARMOR_SLOT_YS = {8, 32, 8, 32};
	private static final int[] CREATIVE_PET_SLOT_XS = {109, 129, 109, 129};
	private static final int[] CREATIVE_PET_SLOT_YS = {8, 8, 32, 32};
	private static final int CREATIVE_OFFHAND_SLOT_X = 149;
	private static final int CREATIVE_OFFHAND_SLOT_Y = 32;
	private static final int OFFHAND_SLOT_INDEX = 45;
	private static final int OFFHAND_SLOT_X = 117;
	private static final int OFFHAND_SLOT_Y = 62;
	private static final int PLAYER_PREVIEW_LEFT = 26;
	private static final int PLAYER_PREVIEW_TOP = 8;
	private static final int PLAYER_PREVIEW_RIGHT = 75;
	private static final int PLAYER_PREVIEW_BOTTOM = 78;
	private static final int PLAYER_PREVIEW_SCALE = 30;
	private static final float PLAYER_PREVIEW_VERTICAL_OFFSET = 0.0625F;
	private static final int CREATIVE_PLAYER_PREVIEW_LEFT = 73;
	private static final int CREATIVE_PLAYER_PREVIEW_TOP = 6;
	private static final int CREATIVE_PLAYER_PREVIEW_RIGHT = 104;
	private static final int CREATIVE_PLAYER_PREVIEW_BOTTOM = 48;
	private static final int CREATIVE_PLAYER_PREVIEW_SCALE = 20;
	private static final float CREATIVE_PLAYER_PREVIEW_VERTICAL_OFFSET = 0.0F;
	private static final Map<CreativeModeInventoryScreen, CreativeLayoutSnapshot> CREATIVE_LAYOUTS = new WeakHashMap<>();
	private static Method playerPreviewRenderMethod;
	private static boolean lookedUpPlayerPreviewRenderMethod;

	private PlayerEntitiesInventoryClient() {
	}

	public static void initialize() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof InventoryScreen inventoryScreen) {
				applyLayout(inventoryScreen);
				ScreenMouseEvents.allowMouseClick(inventoryScreen).register((currentScreen, mouseX, mouseY, button) ->
					allowInventoryMouseClick(inventoryScreen, mouseX, mouseY, button)
				);
			}

			if (screen instanceof CreativeModeInventoryScreen creativeScreen) {
				syncCreativeLayout(creativeScreen);
			}
		});
	}

	public static void renderInventoryBackground(InventoryScreen screen, GuiGraphics graphics, int mouseX, int mouseY) {
		if (screen == null || graphics == null) {
			return;
		}

		applyLayout(screen);
		int leftPos = leftPos(screen);
		int topPos = topPos(screen);
		graphics.blit(
			SURVIVAL_INVENTORY_TEXTURE,
			leftPos,
			topPos,
			0.0F,
			0.0F,
			INVENTORY_WIDTH,
			INVENTORY_HEIGHT,
			TEXTURE_SIZE,
			TEXTURE_SIZE
		);
		drawPlayerPreview(screen, graphics, mouseX, mouseY);
		drawEntityPlaceholders(screen, graphics);
		drawRecipeBookButtonIcon(screen, graphics, mouseX, mouseY);
	}

	public static void renderCreativeInventoryBackground(
		CreativeModeInventoryScreen screen,
		GuiGraphics graphics,
		int mouseX,
		int mouseY
	) {
		if (screen == null || graphics == null || !((CreativeModeInventoryScreenAccessor) screen).madokuCraft$isInventoryOpen()) {
			return;
		}

		int leftPos = leftPos(screen);
		int topPos = topPos(screen);
		graphics.blit(
			CREATIVE_INVENTORY_TEXTURE,
			leftPos,
			topPos,
			0.0F,
			0.0F,
			INVENTORY_WIDTH,
			INVENTORY_HEIGHT,
			TEXTURE_SIZE,
			TEXTURE_SIZE
		);
		drawPlayerPreview(
			screen,
			graphics,
			mouseX,
			mouseY,
			CREATIVE_PLAYER_PREVIEW_LEFT,
			CREATIVE_PLAYER_PREVIEW_TOP,
			CREATIVE_PLAYER_PREVIEW_RIGHT,
			CREATIVE_PLAYER_PREVIEW_BOTTOM,
			CREATIVE_PLAYER_PREVIEW_SCALE,
			CREATIVE_PLAYER_PREVIEW_VERTICAL_OFFSET
		);
		drawEntityPlaceholders(screen, graphics);
	}

	public static void syncCreativeLayout(CreativeModeInventoryScreen screen) {
		if (screen == null) {
			return;
		}

		boolean inventoryOpen = ((CreativeModeInventoryScreenAccessor) screen).madokuCraft$isInventoryOpen();
		if (inventoryOpen) {
			applyCreativeLayout(screen);
			return;
		}
		rememberCreativeLayout(screen);
		restoreCreativeLayout(screen);
	}

	private static boolean allowInventoryMouseClick(InventoryScreen screen, double mouseX, double mouseY, int button) {
		applyLayout(screen);
		AbstractWidget recipeButton = findRecipeButton(screen);
		if (recipeButton == null) {
			return true;
		}
		if (!isHovered(recipeButton, (int) mouseX, (int) mouseY)) {
			return true;
		}

		invokeRecipeButton(recipeButton, mouseX, mouseY, button);
		return false;
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

	private static void applyCreativeLayout(CreativeModeInventoryScreen screen) {
		moveCreativeArmorSlots(screen);
		moveCreativeOffhandSlot(screen);
		moveCreativePetSlots(screen);
	}

	private static void rememberCreativeLayout(CreativeModeInventoryScreen screen) {
		if (screen == null
			|| ((CreativeModeInventoryScreenAccessor) screen).madokuCraft$isInventoryOpen()
			|| CREATIVE_LAYOUTS.containsKey(screen)) {
			return;
		}
		CREATIVE_LAYOUTS.put(screen, CreativeLayoutSnapshot.capture(screen));
	}

	private static void restoreCreativeLayout(CreativeModeInventoryScreen screen) {
		CreativeLayoutSnapshot snapshot = CREATIVE_LAYOUTS.get(screen);
		if (screen == null || snapshot == null) {
			return;
		}
		snapshot.restore(screen);
	}

	private static void moveCreativeArmorSlots(CreativeModeInventoryScreen screen) {
		for (int index = 0; index < CREATIVE_ARMOR_SLOT_XS.length; index++) {
			int slotIndex = 5 + index;
			if (slotIndex < 0 || slotIndex >= screen.getMenu().slots.size()) {
				break;
			}

			Slot slot = screen.getMenu().slots.get(slotIndex);
			((SlotAccessor) slot).madokuCraft$setX(CREATIVE_ARMOR_SLOT_XS[index]);
			((SlotAccessor) slot).madokuCraft$setY(CREATIVE_ARMOR_SLOT_YS[index]);
		}
	}

	private static void moveCreativeOffhandSlot(CreativeModeInventoryScreen screen) {
		if (OFFHAND_SLOT_INDEX < 0 || OFFHAND_SLOT_INDEX >= screen.getMenu().slots.size()) {
			return;
		}

		Slot offhandSlot = screen.getMenu().slots.get(OFFHAND_SLOT_INDEX);
		((SlotAccessor) offhandSlot).madokuCraft$setX(CREATIVE_OFFHAND_SLOT_X);
		((SlotAccessor) offhandSlot).madokuCraft$setY(CREATIVE_OFFHAND_SLOT_Y);
	}

	private static void moveCreativePetSlots(CreativeModeInventoryScreen screen) {
		for (int slot = 0; slot < PlayerEntitiesSystem.SLOT_COUNT; slot++) {
			int slotIndex = PlayerEntitiesSystem.FIRST_SLOT_INDEX + slot;
			if (slotIndex < 0 || slotIndex >= screen.getMenu().slots.size()) {
				break;
			}

			Slot petSlot = screen.getMenu().slots.get(slotIndex);
			((SlotAccessor) petSlot).madokuCraft$setX(CREATIVE_PET_SLOT_XS[slot]);
			((SlotAccessor) petSlot).madokuCraft$setY(CREATIVE_PET_SLOT_YS[slot]);
		}
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

	private static void invokeRecipeButton(AbstractWidget recipeButton, double mouseX, double mouseY, int button) {
		try {
			Method onPress = recipeButton.getClass().getMethod("onPress");
			onPress.invoke(recipeButton);
			return;
		} catch (ReflectiveOperationException ignored) {
		}

		recipeButton.mouseClicked(mouseX, mouseY, button);
	}

	private static void drawRecipeBookButtonIcon(InventoryScreen screen, GuiGraphics graphics, int mouseX, int mouseY) {
		AbstractWidget recipeButton = findRecipeButton(screen);
		if (recipeButton == null) {
			return;
		}

		ResourceLocation texture = isHovered(recipeButton, mouseX, mouseY)
			? RECIPE_BUTTON_HIGHLIGHTED_TEXTURE
			: RECIPE_BUTTON_TEXTURE;
		int iconX = recipeButton.getX() + (recipeButton.getWidth() - RECIPE_BUTTON_ICON_SIZE) / 2;
		int iconY = recipeButton.getY() + (recipeButton.getHeight() - RECIPE_BUTTON_ICON_SIZE) / 2;

		graphics.blit(
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

	private static void drawEntityPlaceholders(AbstractContainerScreen<?> screen, GuiGraphics graphics) {
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
			graphics.blit(
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

	private static void drawPlayerPreview(InventoryScreen screen, GuiGraphics graphics, int mouseX, int mouseY) {
		drawPlayerPreview(
			screen,
			graphics,
			mouseX,
			mouseY,
			PLAYER_PREVIEW_LEFT,
			PLAYER_PREVIEW_TOP,
			PLAYER_PREVIEW_RIGHT,
			PLAYER_PREVIEW_BOTTOM,
			PLAYER_PREVIEW_SCALE,
			PLAYER_PREVIEW_VERTICAL_OFFSET
		);
	}

	private static void drawPlayerPreview(
		AbstractContainerScreen<?> screen,
		GuiGraphics graphics,
		int mouseX,
		int mouseY,
		int previewLeft,
		int previewTop,
		int previewRight,
		int previewBottom,
		int previewScale,
		float previewVerticalOffset
	) {
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
				leftPos(screen) + previewLeft,
				topPos(screen) + previewTop,
				leftPos(screen) + previewRight,
				topPos(screen) + previewBottom,
				previewScale,
				previewVerticalOffset,
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

		for (AbstractWidget widget : Screens.getButtons(screen)) {
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

	private static int leftPos(AbstractContainerScreen<?> screen) {
		return ((AbstractContainerScreenAccessor) screen).madokuCraft$getLeftPos();
	}

	private static int topPos(AbstractContainerScreen<?> screen) {
		return ((AbstractContainerScreenAccessor) screen).madokuCraft$getTopPos();
	}

	private static boolean isHovered(AbstractWidget widget, int mouseX, int mouseY) {
		return mouseX >= widget.getX()
			&& mouseX < widget.getRight()
			&& mouseY >= widget.getY()
			&& mouseY < widget.getBottom();
	}

	private record CreativeLayoutSnapshot(int[] armorXs, int[] armorYs, int offhandX, int offhandY, int[] petXs, int[] petYs) {
		private static CreativeLayoutSnapshot capture(CreativeModeInventoryScreen screen) {
			int armorCount = Math.min(CREATIVE_ARMOR_SLOT_XS.length, Math.max(0, screen.getMenu().slots.size() - 5));
			int[] armorXs = new int[armorCount];
			int[] armorYs = new int[armorCount];
			for (int index = 0; index < armorCount; index++) {
				Slot slot = screen.getMenu().slots.get(5 + index);
				armorXs[index] = slot.x;
				armorYs[index] = slot.y;
			}

			int offhandX = 0;
			int offhandY = 0;
			if (OFFHAND_SLOT_INDEX >= 0 && OFFHAND_SLOT_INDEX < screen.getMenu().slots.size()) {
				Slot offhandSlot = screen.getMenu().slots.get(OFFHAND_SLOT_INDEX);
				offhandX = offhandSlot.x;
				offhandY = offhandSlot.y;
			}

			int petCount = Math.min(PlayerEntitiesSystem.SLOT_COUNT, Math.max(0, screen.getMenu().slots.size() - PlayerEntitiesSystem.FIRST_SLOT_INDEX));
			int[] petXs = new int[petCount];
			int[] petYs = new int[petCount];
			for (int slot = 0; slot < petCount; slot++) {
				Slot petSlot = screen.getMenu().slots.get(PlayerEntitiesSystem.FIRST_SLOT_INDEX + slot);
				petXs[slot] = petSlot.x;
				petYs[slot] = petSlot.y;
			}

			return new CreativeLayoutSnapshot(armorXs, armorYs, offhandX, offhandY, petXs, petYs);
		}

		private void restore(CreativeModeInventoryScreen screen) {
			for (int index = 0; index < armorXs.length; index++) {
				int slotIndex = 5 + index;
				if (slotIndex < 0 || slotIndex >= screen.getMenu().slots.size()) {
					break;
				}
				Slot slot = screen.getMenu().slots.get(slotIndex);
				((SlotAccessor) slot).madokuCraft$setX(armorXs[index]);
				((SlotAccessor) slot).madokuCraft$setY(armorYs[index]);
			}

			if (OFFHAND_SLOT_INDEX >= 0 && OFFHAND_SLOT_INDEX < screen.getMenu().slots.size()) {
				Slot offhandSlot = screen.getMenu().slots.get(OFFHAND_SLOT_INDEX);
				((SlotAccessor) offhandSlot).madokuCraft$setX(offhandX);
				((SlotAccessor) offhandSlot).madokuCraft$setY(offhandY);
			}

			for (int slot = 0; slot < petXs.length; slot++) {
				int slotIndex = PlayerEntitiesSystem.FIRST_SLOT_INDEX + slot;
				if (slotIndex < 0 || slotIndex >= screen.getMenu().slots.size()) {
					break;
				}
				Slot petSlot = screen.getMenu().slots.get(slotIndex);
				((SlotAccessor) petSlot).madokuCraft$setX(petXs[slot]);
				((SlotAccessor) petSlot).madokuCraft$setY(petYs[slot]);
			}
		}
	}
}

package madoku.craft.pet;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import madoku.craft.MadokuCraft;
import madoku.craft.pet.PetComponentsManager.PetHolder;
import madoku.craft.pet.PetComponentsManager.PetInventory;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/** Client-side rendering and synchronized state for the pet HUD. */
public final class PetHudManagerClient {
	private static final Identifier ABILITY_HUD_ID = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "pet_ability_hud");
	private static final Identifier ABILITY_SLOT_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/gui/interface/ability_slot.png");
	private static final RenderPipeline ABILITY_SLOT_PIPELINE = RenderPipelines.GUI_TEXTURED;
	private static final int ABILITY_SLOT_TEXTURE_SIZE = 16;
	private static final int HOTBAR_HALF_WIDTH = 91;
	private static final int HOTBAR_SLOT_ROW_HEIGHT = 22;
	private static final int OFFHAND_SLOT_WIDTH = 29;
	private static final int OFFHAND_TO_ABILITY_SPACING = 7;
	private static final int ABILITY_SLOT_SIZE = 16;
	private static final int ABILITY_SLOT_SPACING = 1;
	private static final int ABILITY_SLOT_Y_OFFSET = (HOTBAR_SLOT_ROW_HEIGHT - ABILITY_SLOT_SIZE) / 2;
	private static final int ABILITY_ITEM_RENDER_SIZE = 12;
	private static final float ABILITY_ITEM_SCALE = 0.75F;
	private static final int ABILITY_COOLDOWN_OVERLAY_COLOR = 0x7FFFFFFF;
	private static final float ABILITY_COOLDOWN_TEXT_SCALE = 0.75F;
	private static final int ABILITY_COOLDOWN_TEXT_COLOR = 0xFFFFFFFF;
	private static final int ABILITY_COOLDOWN_TEXT_Y_SPACING = 2;
	private static final long[] abilityCooldownEndTicks = new long[PetEntitiesManager.SLOT_COUNT];

	private PetHudManagerClient() {
	}

	public static void initialize() {
		HudElementRegistry.attachElementAfter(
			net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HOTBAR,
			ABILITY_HUD_ID,
			PetHudManagerClient::render
		);
	}

	public static void reset() {
		java.util.Arrays.fill(abilityCooldownEndTicks, 0L);
	}

	public static void setAbilityCooldowns(int[] remainingTicks) {
		Minecraft client = Minecraft.getInstance();
		long now = client.level == null ? 0L : client.level.getGameTime();
		for (int slot = 0; slot < abilityCooldownEndTicks.length; slot++) {
			int remaining = remainingTicks != null && slot < remainingTicks.length ? Math.max(0, remainingTicks[slot]) : 0;
			abilityCooldownEndTicks[slot] = remaining == 0 ? 0L : now + remaining;
		}
	}

	private static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.gui.hud.isHidden() || player.isSpectator() || !PetConfigManager.isEnabled()) return;
		if (!(player instanceof PetHolder holder)) return;
		PetInventory inventory = holder.madokuCraft$getPetInventory();
		if (inventory == null) return;

		java.util.List<Integer> visibleSlots = visibleAbilitySlots(inventory);
		if (visibleSlots.isEmpty()) return;
		int slotY = context.guiHeight() - HOTBAR_SLOT_ROW_HEIGHT + ABILITY_SLOT_Y_OFFSET;
		int[] slotXs = computeAbilitySlotXs(context, player, visibleSlots.size());
		for (int index = 0; index < visibleSlots.size(); index++) {
			int slot = visibleSlots.get(index);
			int slotX = slotXs[index];
			context.blit(ABILITY_SLOT_PIPELINE, ABILITY_SLOT_TEXTURE, slotX, slotY, 0.0F, 0.0F, ABILITY_SLOT_SIZE, ABILITY_SLOT_SIZE, ABILITY_SLOT_TEXTURE_SIZE, ABILITY_SLOT_TEXTURE_SIZE);
			ItemStack stack = inventory.getItem(slot);
			if (stack == null || stack.isEmpty()) continue;

			int itemOffset = (ABILITY_SLOT_SIZE - ABILITY_ITEM_RENDER_SIZE) / 2;
			int itemX = slotX + itemOffset;
			int itemY = slotY + itemOffset;
			renderScaledItem(context, stack, itemX, itemY);
			renderCooldown(context, client, stack, slot, slotX, slotY, itemX, itemY);
		}
	}

	private static java.util.List<Integer> visibleAbilitySlots(PetInventory inventory) {
		java.util.List<Integer> visible = new java.util.ArrayList<>(PetEntitiesManager.SLOT_COUNT);
		for (int slot = 0; slot < Math.min(PetEntitiesManager.SLOT_COUNT, inventory.getContainerSize()); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack != null && !stack.isEmpty() && PetAbilitiesManager.hasAbility(stack)) visible.add(slot);
		}
		return visible;
	}

	private static void renderCooldown(GuiGraphicsExtractor context, Minecraft client, ItemStack stack, int slot, int slotX, int slotY, int itemX, int itemY) {
		int totalCooldownTicks = PetAbilitiesManager.cooldownTicks(client.player, slot, stack);
		if (totalCooldownTicks <= 0 || slot < 0 || slot >= PetEntitiesManager.SLOT_COUNT) return;
		long remainingTicks = Math.max(0L, abilityCooldownEndTicks[slot] - client.level.getGameTime());
		if (remainingTicks <= 0L) return;

		float cooldownPercent = Math.min(1.0F, remainingTicks / (float) totalCooldownTicks);
		int overlayTop = itemY + Mth.floor(ABILITY_ITEM_RENDER_SIZE * (1.0F - cooldownPercent));
		int overlayBottom = overlayTop + Mth.ceil(ABILITY_ITEM_RENDER_SIZE * cooldownPercent);
		context.fill(RenderPipelines.GUI, itemX, overlayTop, itemX + ABILITY_ITEM_RENDER_SIZE, overlayBottom, ABILITY_COOLDOWN_OVERLAY_COLOR);
		renderCooldownLabel(context, client, slotX, slotY, remainingTicks);
	}

	private static void renderCooldownLabel(GuiGraphicsExtractor context, Minecraft client, int slotX, int slotY, long remainingTicks) {
		String cooldownText = Integer.toString(Math.max(1, Mth.ceil(remainingTicks / 20.0F)));
		int textWidth = client.font.width(cooldownText);
		float centerX = slotX + (ABILITY_SLOT_SIZE / 2.0F);
		int textY = slotY - Math.round(client.font.lineHeight * ABILITY_COOLDOWN_TEXT_SCALE) - ABILITY_COOLDOWN_TEXT_Y_SPACING;
		context.pose().pushMatrix();
		context.pose().translate(centerX, textY);
		context.pose().scale(ABILITY_COOLDOWN_TEXT_SCALE, ABILITY_COOLDOWN_TEXT_SCALE);
		context.text(client.font, cooldownText, Math.round(-textWidth / 2.0F), 0, ABILITY_COOLDOWN_TEXT_COLOR, true);
		context.pose().popMatrix();
	}

	private static void renderScaledItem(GuiGraphicsExtractor context, ItemStack stack, int x, int y) {
		context.pose().pushMatrix();
		context.pose().translate(x, y);
		context.pose().scale(ABILITY_ITEM_SCALE, ABILITY_ITEM_SCALE);
		context.item(stack, 0, 0);
		context.pose().popMatrix();
	}

	private static int[] computeAbilitySlotXs(GuiGraphicsExtractor context, LocalPlayer player, int slotCount) {
		int[] xs = new int[slotCount];
		int centerX = context.guiWidth() / 2;
		boolean offhandOnLeft = player.getMainArm() == HumanoidArm.RIGHT;
		boolean offhandVisible = !player.getOffhandItem().isEmpty();
		int hotbarLeftEdge = centerX - HOTBAR_HALF_WIDTH;
		int hotbarRightEdge = centerX + HOTBAR_HALF_WIDTH;
		if (offhandOnLeft) {
			int anchorX = offhandVisible ? hotbarLeftEdge - OFFHAND_SLOT_WIDTH : hotbarLeftEdge;
			int startX = anchorX - OFFHAND_TO_ABILITY_SPACING - ABILITY_SLOT_SIZE;
			for (int slot = 0; slot < xs.length; slot++) xs[slot] = startX - (slot * (ABILITY_SLOT_SIZE + ABILITY_SLOT_SPACING));
			return xs;
		}
		int anchorX = offhandVisible ? hotbarRightEdge + OFFHAND_SLOT_WIDTH : hotbarRightEdge;
		int startX = anchorX + OFFHAND_TO_ABILITY_SPACING;
		for (int slot = 0; slot < xs.length; slot++) xs[slot] = startX + (slot * (ABILITY_SLOT_SIZE + ABILITY_SLOT_SPACING));
		return xs;
	}
}

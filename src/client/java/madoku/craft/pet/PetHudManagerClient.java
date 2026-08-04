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
	private static final Identifier ABILITY_SLOT_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/gui/interface/ability-slot.png");
	private static final Identifier ABILITY_ARROW_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/gui/icons/arrow-projectile.png");
	private static final Identifier ABILITY_BEE_SWARM_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/gui/icons/bee-swarm.png");
	private static final Identifier ABILITY_BLOCK_DAMAGE_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/gui/icons/block-damage.png");
	private static final Identifier ABILITY_BONUS_ARMOR_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/gui/icons/bonus-armor.png");
	private static final Identifier ABILITY_BONUS_DAMAGE_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/gui/icons/bonus-damage.png");
	private static final Identifier ABILITY_BONUS_HEALTH_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/gui/icons/bonus-health.png");
	private static final Identifier ABILITY_EGG_VOLLEY_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/gui/icons/egg-volley.png");
	private static final Identifier ABILITY_EXPLOSIVE_PROJECTILE_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/gui/icons/explosive-projectile.png");
	private static final Identifier ABILITY_FALL_DAMAGE_REDUCTION_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/gui/icons/fall-damage-reduction.png");
	private static final Identifier ABILITY_HEALTH_REGENERATION_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/gui/icons/health-regeneration.png");
	private static final Identifier ABILITY_MOB_SCAN_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/gui/icons/mob-scan.png");
	private static final Identifier ABILITY_WEB_PROJECTILE_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/gui/icons/web-projectile.png");
	private static final RenderPipeline ABILITY_SLOT_PIPELINE = RenderPipelines.GUI_TEXTURED;
	private static final int ABILITY_SLOT_TEXTURE_SIZE = 16;
	private static final int HOTBAR_HALF_WIDTH = 91;
	private static final int HOTBAR_SLOT_ROW_HEIGHT = 22;
	private static final int OFFHAND_SLOT_WIDTH = 29;
	private static final int OFFHAND_TO_ABILITY_SPACING = 4;
	private static final int ABILITY_SLOT_SIZE = 16;
	private static final int ABILITY_CARD_SIZE = ABILITY_SLOT_SIZE / 2;
	private static final int ABILITY_ICON_SIZE = 6;
	private static final int ABILITY_ICON_OFFSET = (ABILITY_CARD_SIZE - ABILITY_ICON_SIZE) / 2;
	private static final int ABILITY_CARD_SPACING = 2;
	private static final int ABILITY_CARD_STRIDE = ABILITY_CARD_SIZE + ABILITY_CARD_SPACING;
	private static final int ABILITY_ROWS_PER_COLUMN = 2;
	private static final int ABILITY_SLOT_Y_OFFSET = ((HOTBAR_SLOT_ROW_HEIGHT - ABILITY_SLOT_SIZE) / 2) - 1;
	private static final float ABILITY_COOLDOWN_TEXT_SCALE = 0.5F;
	private static final int ABILITY_COOLDOWN_TEXT_COLOR = 0xFFFFFFFF;
	private static final int ABILITY_COOLDOWN_OVERLAY_COLOR = 0x7FFFFFFF;
	private static final long[][] abilityCooldownEndTicks = new long[PetEntitiesManager.SLOT_COUNT][MadokuPetManager.MAX_ABILITY_COOLDOWNS_PER_PET];

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
		for (long[] slotCooldowns : abilityCooldownEndTicks) java.util.Arrays.fill(slotCooldowns, 0L);
	}

	public static void setAbilityCooldowns(int[] remainingTicks) {
		Minecraft client = Minecraft.getInstance();
		long now = client.level == null ? 0L : client.level.getGameTime();
		for (int slot = 0; slot < abilityCooldownEndTicks.length; slot++) {
			for (int cooldownIndex = 0; cooldownIndex < abilityCooldownEndTicks[slot].length; cooldownIndex++) {
				int flatIndex = slot * MadokuPetManager.MAX_ABILITY_COOLDOWNS_PER_PET + cooldownIndex;
				int remaining = remainingTicks != null && flatIndex < remainingTicks.length ? Math.max(0, remainingTicks[flatIndex]) : 0;
				if (remaining == 0) {
					abilityCooldownEndTicks[slot][cooldownIndex] = 0L;
				} else if (abilityCooldownEndTicks[slot][cooldownIndex] <= now) {
					abilityCooldownEndTicks[slot][cooldownIndex] = now + remaining;
				}
			}
		}
	}

	private static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.gui.hud.isHidden() || player.isSpectator() || !PetConfigManager.isEnabled()) return;
		if (!(player instanceof PetHolder holder)) return;
		PetInventory inventory = holder.madokuCraft$getPetInventory();
		if (inventory == null) return;

		java.util.List<AbilityHudEntry> abilities = visibleAbilities(inventory);
		if (abilities.isEmpty()) return;
		int slotY = context.guiHeight() - HOTBAR_SLOT_ROW_HEIGHT + ABILITY_SLOT_Y_OFFSET;
		int[] columnXs = computeAbilityColumnXs(context, player, (abilities.size() + ABILITY_ROWS_PER_COLUMN - 1) / ABILITY_ROWS_PER_COLUMN);
		for (int index = 0; index < abilities.size(); index++) {
			AbilityHudEntry entry = abilities.get(index);
			int cardX = columnXs[index / ABILITY_ROWS_PER_COLUMN];
			int cardY = slotY + ((index % ABILITY_ROWS_PER_COLUMN) * ABILITY_CARD_STRIDE);
			renderScaledTexture(context, ABILITY_SLOT_TEXTURE, cardX, cardY);
			renderScaledTexture(context, abilityIcon(entry.ability().abilityType), cardX + ABILITY_ICON_OFFSET, cardY + ABILITY_ICON_OFFSET, ABILITY_ICON_SIZE);
			if (entry.cooldownIndex() >= 0) {
				renderCooldown(context, client, entry.petSlot(), entry.cooldownIndex(), entry.ability().cooldownTicks, cardX + ABILITY_ICON_OFFSET, cardY + ABILITY_ICON_OFFSET);
			}
		}
	}

	private static java.util.List<AbilityHudEntry> visibleAbilities(PetInventory inventory) {
		java.util.List<AbilityHudEntry> visible = new java.util.ArrayList<>();
		for (int slot = 0; slot < Math.min(PetEntitiesManager.SLOT_COUNT, inventory.getContainerSize()); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack == null || stack.isEmpty() || !PetAbilitiesManager.hasAbility(stack)) continue;
			PetConfigManager.PetRule rule = PetConfigManager.resolvePetRule(stack);
			if (rule == null) continue;

			int cooldownIndex = 0;
			for (PetConfigManager.PetAbilityRule ability : rule.abilities) {
				int abilityCooldownIndex = -1;
				if (ability.cooldownTicks > 0L) {
					if (cooldownIndex >= MadokuPetManager.MAX_ABILITY_COOLDOWNS_PER_PET) continue;
					abilityCooldownIndex = cooldownIndex++;
				}
				visible.add(new AbilityHudEntry(slot, ability, abilityCooldownIndex));
			}
		}
		return visible;
	}

	private static void renderScaledTexture(GuiGraphicsExtractor context, Identifier texture, int x, int y) {
		renderScaledTexture(context, texture, x, y, ABILITY_CARD_SIZE);
	}

	private static void renderScaledTexture(GuiGraphicsExtractor context, Identifier texture, int x, int y, int size) {
		context.pose().pushMatrix();
		context.pose().translate(x, y);
		context.pose().scale(size / (float) ABILITY_SLOT_TEXTURE_SIZE, size / (float) ABILITY_SLOT_TEXTURE_SIZE);
		context.blit(ABILITY_SLOT_PIPELINE, texture, 0, 0, 0.0F, 0.0F, ABILITY_SLOT_TEXTURE_SIZE, ABILITY_SLOT_TEXTURE_SIZE, ABILITY_SLOT_TEXTURE_SIZE, ABILITY_SLOT_TEXTURE_SIZE);
		context.pose().popMatrix();
	}

	private static void renderCooldown(GuiGraphicsExtractor context, Minecraft client, int slot, int cooldownIndex, long totalCooldownTicks, int cardX, int cardY) {
		if (slot < 0 || slot >= PetEntitiesManager.SLOT_COUNT || cooldownIndex < 0 || cooldownIndex >= MadokuPetManager.MAX_ABILITY_COOLDOWNS_PER_PET) return;
		long remainingTicks = Math.max(0L, abilityCooldownEndTicks[slot][cooldownIndex] - client.level.getGameTime());
		if (remainingTicks <= 0L) return;

		float cooldownPercent = Math.min(1.0F, remainingTicks / (float) totalCooldownTicks);
		int overlayTop = cardY + Mth.floor(ABILITY_ICON_SIZE * (1.0F - cooldownPercent));
		int overlayBottom = overlayTop + Mth.ceil(ABILITY_ICON_SIZE * cooldownPercent);
		context.fill(RenderPipelines.GUI, cardX, overlayTop, cardX + ABILITY_ICON_SIZE, overlayBottom, ABILITY_COOLDOWN_OVERLAY_COLOR);
		renderCooldownNumber(context, client, cardX, cardY, remainingTicks);
	}

	private static void renderCooldownNumber(GuiGraphicsExtractor context, Minecraft client, int cardX, int cardY, long remainingTicks) {
		String cooldownText = Integer.toString(Math.max(1, Mth.ceil(remainingTicks / 20.0F)));
		int textWidth = client.font.width(cooldownText);
		float textScale = ABILITY_COOLDOWN_TEXT_SCALE;
		int textX = Math.round(cardX + ((ABILITY_ICON_SIZE - (textWidth * textScale)) / 2.0F));
		int textY = Math.round(cardY + ((ABILITY_ICON_SIZE - (client.font.lineHeight * textScale)) / 2.0F));
		context.pose().pushMatrix();
		context.pose().scale(textScale, textScale);
		context.text(client.font, cooldownText, Math.round(textX / textScale), Math.round(textY / textScale), ABILITY_COOLDOWN_TEXT_COLOR, true);
		context.pose().popMatrix();
	}

	private static Identifier abilityIcon(String abilityType) {
		return switch (PetConfigManager.normalizeAbilityId(abilityType)) {
			case MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW -> ABILITY_ARROW_TEXTURE;
			case MadokuPetManager.PET_ABILITY_BEE_SWARM -> ABILITY_BEE_SWARM_TEXTURE;
			case MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK -> ABILITY_BLOCK_DAMAGE_TEXTURE;
			case MadokuPetManager.PET_ABILITY_ARMOR_BONUS -> ABILITY_BONUS_ARMOR_TEXTURE;
			case MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS -> ABILITY_BONUS_DAMAGE_TEXTURE;
			case MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS -> ABILITY_BONUS_HEALTH_TEXTURE;
			case MadokuPetManager.PET_ABILITY_EGG_PROJECTILE -> ABILITY_EGG_VOLLEY_TEXTURE;
			case MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE -> ABILITY_EXPLOSIVE_PROJECTILE_TEXTURE;
			case MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION -> ABILITY_FALL_DAMAGE_REDUCTION_TEXTURE;
			case MadokuPetManager.PET_ABILITY_HEALTH_REGENERATION -> ABILITY_HEALTH_REGENERATION_TEXTURE;
			case MadokuPetManager.PET_ABILITY_MOB_SCAN -> ABILITY_MOB_SCAN_TEXTURE;
			case MadokuPetManager.PET_ABILITY_WEB_PROJECTILE -> ABILITY_WEB_PROJECTILE_TEXTURE;
			default -> ABILITY_ARROW_TEXTURE;
		};
	}

	private static int[] computeAbilityColumnXs(GuiGraphicsExtractor context, LocalPlayer player, int columnCount) {
		int[] xs = new int[columnCount];
		int centerX = context.guiWidth() / 2;
		boolean offhandOnLeft = player.getMainArm() == HumanoidArm.RIGHT;
		boolean offhandVisible = !player.getOffhandItem().isEmpty();
		int hotbarLeftEdge = centerX - HOTBAR_HALF_WIDTH;
		int hotbarRightEdge = centerX + HOTBAR_HALF_WIDTH;
		if (offhandOnLeft) {
			int anchorX = offhandVisible ? hotbarLeftEdge - OFFHAND_SLOT_WIDTH : hotbarLeftEdge;
			int startX = anchorX - OFFHAND_TO_ABILITY_SPACING - ABILITY_CARD_SIZE;
			for (int column = 0; column < xs.length; column++) xs[column] = startX - (column * ABILITY_CARD_STRIDE);
			return xs;
		}
		int anchorX = offhandVisible ? hotbarRightEdge + OFFHAND_SLOT_WIDTH : hotbarRightEdge;
		int startX = anchorX + OFFHAND_TO_ABILITY_SPACING;
		for (int column = 0; column < xs.length; column++) xs[column] = startX + (column * ABILITY_CARD_STRIDE);
		return xs;
	}

	private record AbilityHudEntry(int petSlot, PetConfigManager.PetAbilityRule ability, int cooldownIndex) {}
}

package madoku.craft.java.hud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.loader.api.FabricLoader;
import madoku.craft.mixin.hud.GuiAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Renders the HUD attribute bars from vanilla client-side player state. */
public final class MadokuHudAttributeBars {
	private static final RenderPipeline GUI_PIPELINE = RenderPipelines.GUI_TEXTURED;
	private static final Identifier HEART_EMPTY_TEXTURE = Identifier.withDefaultNamespace("hud/heart/container");
	private static final Identifier HEART_FILL_TEXTURE = Identifier.withDefaultNamespace("hud/heart/full");
	private static final Identifier HEART_HALF_TEXTURE = Identifier.withDefaultNamespace("hud/heart/half");
	private static final Identifier FOOD_EMPTY_TEXTURE = Identifier.withDefaultNamespace("hud/food_empty");
	private static final Identifier FOOD_FULL_TEXTURE = Identifier.withDefaultNamespace("hud/food_full");
	private static final Identifier FOOD_HALF_TEXTURE = Identifier.withDefaultNamespace("hud/food_half");
	private static final Identifier ARMOR_EMPTY_TEXTURE = Identifier.withDefaultNamespace("hud/armor_empty");
	private static final Identifier ARMOR_FULL_TEXTURE = Identifier.withDefaultNamespace("hud/armor_full");
	private static final Identifier ARMOR_HALF_TEXTURE = Identifier.withDefaultNamespace("hud/armor_half");
	private static final Identifier OXYGEN_EMPTY_TEXTURE = Identifier.withDefaultNamespace("hud/air_empty");
	private static final Identifier OXYGEN_FULL_TEXTURE = Identifier.withDefaultNamespace("hud/air");
	private static final Identifier LUCK_TEXTURE = Identifier.fromNamespaceAndPath("madoku-craft", "textures/icons/hud-luck.png");
	private static final int ICON_SIZE = 9;
	private static final int TEXT_SPACING = 2;
	private static final int FOOD_RIGHT_EDGE = 91;
	private static final int OXYGEN_RIGHT_EDGE = 91;
	private static final int VANILLA_SECOND_LEFT_SLOT = 8;
	private static final int OXYGEN_SLOT_SHIFT = 2;
	private static final String ATTRIBUTES_MOD_ID = "madoku-craft-attributes";
	private static final float TEXT_SCALE = 0.8F;
	private static final int COLOR = 0xFFFFFFFF;
	private static final long TICKS_PER_SECOND = 20L;
	private static boolean initialized;

	private MadokuHudAttributeBars() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		HudElementRegistry.replaceElement(
			VanillaHudElements.HEALTH_BAR,
			oldElement -> (context, tickCounter) -> renderHealth(context, tickCounter, oldElement)
		);
		HudElementRegistry.replaceElement(
			VanillaHudElements.FOOD_BAR,
			oldElement -> (context, tickCounter) -> renderHunger(context, tickCounter, oldElement)
		);
		HudElementRegistry.replaceElement(
			VanillaHudElements.ARMOR_BAR,
			oldElement -> (context, tickCounter) -> renderArmor(context, tickCounter, oldElement)
		);
		HudElementRegistry.replaceElement(
			VanillaHudElements.AIR_BAR,
			oldElement -> (context, tickCounter) -> renderOxygen(context, tickCounter, oldElement)
		);
	}

	public static void reset() {
		initialized = false;
	}

	private static void renderHealth(GuiGraphicsExtractor context, DeltaTracker tickCounter, HudElement oldElement) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (!hasPlayer(player, level) || !HudConfigManager.isEnabled("health")) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		hideVanilla(context, tickCounter, oldElement);
		float health = Math.max(0.0F, player.getHealth());
		float effectiveHealth = Math.max(0.0F, health + player.getAbsorptionAmount());
		float maxHealth = Math.max(1.0F, player.getMaxHealth());
		float healthPercent = Math.min(1.0F, health / maxHealth);
		int x = context.guiWidth() / 2 - 91;
		int y = context.guiHeight() - HudStatusBarHeightRegistry.getHeight(VanillaHudElements.HEALTH_BAR);
		Gui gui = client.gui;
		int guiTicks = gui.hud.getGuiTicks();
		if (isHealthBlinking(gui, guiTicks)) y -= 1;
		context.blitSprite(GUI_PIPELINE, HEART_EMPTY_TEXTURE, x, y, ICON_SIZE, ICON_SIZE);
		Identifier fill = healthPercent < 0.9F ? HEART_HALF_TEXTURE : HEART_FILL_TEXTURE;
		if (healthPercent > 0.1F) {
			context.blitSprite(GUI_PIPELINE, fill, x, y, ICON_SIZE, ICON_SIZE);
		}
		drawText(context, client, "Health: " + format(effectiveHealth) + "/" + format(maxHealth), x + ICON_SIZE + TEXT_SPACING, y + 1);
	}

	private static void renderHunger(GuiGraphicsExtractor context, DeltaTracker tickCounter, HudElement oldElement) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (!hasPlayer(player, level) || !HudConfigManager.isEnabled("hunger")) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		hideVanilla(context, tickCounter, oldElement);
		int max = HudPayloadManager.hasServerHunger() ? Math.max(1, HudPayloadManager.getServerHungerMax()) : 20;
		int current = HudPayloadManager.hasServerHunger()
			? clamp(HudPayloadManager.getServerHungerCurrent(), 0, max)
			: clamp(player.getFoodData().getFoodLevel(), 0, max);
		float percent = current / (float) max;
		String text = "Hunger: " + current + "/" + max;
		int x = computeRightAlignedX(context, client, text, "Hunger: 20/20", FOOD_RIGHT_EDGE, VANILLA_SECOND_LEFT_SLOT);
		int y = context.guiHeight() - HudStatusBarHeightRegistry.getHeight(VanillaHudElements.FOOD_BAR);
		context.blitSprite(GUI_PIPELINE, FOOD_EMPTY_TEXTURE, x, y, ICON_SIZE, ICON_SIZE);
		if (percent > 0.1F) {
			context.blitSprite(GUI_PIPELINE, percent < 0.9F ? FOOD_HALF_TEXTURE : FOOD_FULL_TEXTURE, x, y, ICON_SIZE, ICON_SIZE);
		}
		drawText(context, client, text, x + ICON_SIZE + TEXT_SPACING, y + 1);
	}

	private static void renderArmor(GuiGraphicsExtractor context, DeltaTracker tickCounter, HudElement oldElement) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (!hasPlayer(player, level) || !HudConfigManager.isEnabled("armor")) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		int pieces = countArmorPieces(player);
		if (pieces <= 0) {
			return;
		}
		hideVanilla(context, tickCounter, oldElement);
		int x = context.guiWidth() / 2 - 91;
		int y = context.guiHeight() - HudStatusBarHeightRegistry.getHeight(VanillaHudElements.ARMOR_BAR);
		context.blitSprite(GUI_PIPELINE, ARMOR_EMPTY_TEXTURE, x, y, ICON_SIZE, ICON_SIZE);
		context.blitSprite(GUI_PIPELINE, pieces < 4 ? ARMOR_HALF_TEXTURE : ARMOR_FULL_TEXTURE, x, y, ICON_SIZE, ICON_SIZE);
		double armor = Math.max(0.0D, player.getAttributeValue(Attributes.ARMOR));
		drawText(context, client, "Armor: " + format(armor), x + ICON_SIZE + TEXT_SPACING, y + 1);
	}

	private static void renderOxygen(GuiGraphicsExtractor context, DeltaTracker tickCounter, HudElement oldElement) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (!hasPlayer(player, level) || !HudConfigManager.isEnabled()) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		boolean oxygenEnabled = HudConfigManager.isEnabled("oxygen");
		// Luck is an Attributes-module UI feature. The other bars remain
		// vanilla-backed HUD features and must work without that module.
		boolean luckEnabled = FabricLoader.getInstance().isModLoaded(ATTRIBUTES_MOD_ID)
			&& HudConfigManager.isEnabled("luck");
		int maxAir = Math.max(1, player.getMaxAirSupply());
		int air = clamp(player.getAirSupply(), 0, maxAir);
		boolean renderOxygen = oxygenEnabled && (air < maxAir || player.isEyeInFluid(FluidTags.WATER));
		if (!renderOxygen && !luckEnabled) {
			return;
		}

		hideVanilla(context, tickCounter, oldElement);
		String text;
		Identifier icon;
		if (renderOxygen) {
			text = "Oxygen: " + displaySeconds(air) + "/" + displaySeconds(maxAir);
			icon = air <= 0 ? OXYGEN_EMPTY_TEXTURE : OXYGEN_FULL_TEXTURE;
		} else {
			text = "Luck: " + formatLuck(player);
			icon = LUCK_TEXTURE;
		}
		int slot = renderOxygen ? VANILLA_SECOND_LEFT_SLOT : Math.max(0, VANILLA_SECOND_LEFT_SLOT - OXYGEN_SLOT_SHIFT);
		int x = computeRightAlignedX(context, client, text, renderOxygen ? "Oxygen: 30/30" : "Luck: 100%", OXYGEN_RIGHT_EDGE, slot);
		int y = context.guiHeight() - HudStatusBarHeightRegistry.getHeight(VanillaHudElements.AIR_BAR);
		if (renderOxygen) {
			context.blitSprite(GUI_PIPELINE, icon, x, y, ICON_SIZE, ICON_SIZE);
		} else {
			context.blit(GUI_PIPELINE, icon, x, y, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
		}
		drawText(context, client, text, x + ICON_SIZE + TEXT_SPACING, y + 1);
	}

	private static boolean hasPlayer(LocalPlayer player, ClientLevel level) {
		Minecraft client = Minecraft.getInstance();
		return player != null && level != null && client.gui != null
			&& !client.gui.hud.isHidden() && !player.isSpectator();
	}

	private static void hideVanilla(GuiGraphicsExtractor context, DeltaTracker tickCounter, HudElement oldElement) {
		context.pose().pushMatrix();
		context.pose().translate(-10000.0F, -10000.0F);
		oldElement.extractRenderState(context, tickCounter);
		context.pose().popMatrix();
	}

	private static boolean isHealthBlinking(Gui gui, int ticks) {
		long blinkTime = ((GuiAccessor) gui.hud).madokuCraft$getHealthBlinkTime();
		return blinkTime > ticks && ((blinkTime - ticks) / 3L) % 2L == 1L;
	}

	private static int computeRightAlignedX(
		GuiGraphicsExtractor context,
		Minecraft client,
		String text,
		String baseline,
		int rightEdge,
		int slot
	) {
		int baseX = context.guiWidth() / 2 + rightEdge - ICON_SIZE - (slot * 8) + 4;
		return baseX + Math.round((client.font.width(baseline) - client.font.width(text)) * TEXT_SCALE);
	}

	private static void drawText(GuiGraphicsExtractor context, Minecraft client, String text, int x, int y) {
		context.pose().pushMatrix();
		context.pose().scale(TEXT_SCALE, TEXT_SCALE);
		context.text(client.font, text, Math.round(x / TEXT_SCALE), Math.round(y / TEXT_SCALE), COLOR, true);
		context.pose().popMatrix();
	}

	private static int countArmorPieces(LocalPlayer player) {
		int pieces = 0;
		if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) pieces++;
		if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) pieces++;
		if (!player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) pieces++;
		if (!player.getItemBySlot(EquipmentSlot.FEET).isEmpty()) pieces++;
		return pieces;
	}

	private static String formatLuck(LocalPlayer player) {
		AttributeInstance luck = player.getAttribute(Attributes.LUCK);
		double value = luck == null ? 0.0D : luck.getValue();
		return Math.max(0L, Math.round(value)) + "%";
	}

	private static int displaySeconds(int ticks) {
		return (int) Math.ceil(Math.max(0, ticks) / (double) TICKS_PER_SECOND);
	}

	private static String format(double value) {
		if (!Double.isFinite(value)) return "0";
		String text = String.format(java.util.Locale.ROOT, "%.3f", value);
		while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
		return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}

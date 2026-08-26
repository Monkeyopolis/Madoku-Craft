package madoku.craft.hud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import madoku.craft.MadokuCraft;
import madoku.craft.attributes.MadokuHungerManager;
import madoku.craft.attributes.MadokuLuckManager;
import madoku.craft.attributes.MadokuOxygenManager;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import java.util.Locale;

public final class HudAttributesManager {
	private static final RenderPipeline HEART_PIPELINE = RenderPipelines.GUI_TEXTURED;
	private static final Identifier HEART_EMPTY_TEXTURE = Identifier.withDefaultNamespace("hud/heart/container");
	private static final Identifier HEART_EMPTY_BLINKING_TEXTURE = Identifier.withDefaultNamespace("hud/heart/container_blinking");
	private static final Identifier HEART_EMPTY_HARDCORE_TEXTURE = Identifier.withDefaultNamespace("hud/heart/container_hardcore");
	private static final Identifier HEART_EMPTY_HARDCORE_BLINKING_TEXTURE = Identifier.withDefaultNamespace("hud/heart/container_hardcore_blinking");
	private static final Identifier ABSORBING_FULL_TEXTURE = Identifier.withDefaultNamespace("hud/heart/absorbing_full");
	private static final Identifier ABSORBING_FULL_BLINKING_TEXTURE = Identifier.withDefaultNamespace("hud/heart/absorbing_full_blinking");
	private static final Identifier ABSORBING_HALF_TEXTURE = Identifier.withDefaultNamespace("hud/heart/absorbing_half");
	private static final Identifier ABSORBING_HALF_BLINKING_TEXTURE = Identifier.withDefaultNamespace("hud/heart/absorbing_half_blinking");
	private static final Identifier ABSORBING_HARDCORE_FULL_TEXTURE = Identifier.withDefaultNamespace("hud/heart/absorbing_hardcore_full");
	private static final Identifier ABSORBING_HARDCORE_FULL_BLINKING_TEXTURE = Identifier.withDefaultNamespace("hud/heart/absorbing_hardcore_full_blinking");
	private static final Identifier ABSORBING_HARDCORE_HALF_TEXTURE = Identifier.withDefaultNamespace("hud/heart/absorbing_hardcore_half");
	private static final Identifier ABSORBING_HARDCORE_HALF_BLINKING_TEXTURE = Identifier.withDefaultNamespace("hud/heart/absorbing_hardcore_half_blinking");
	private static final RenderPipeline FOOD_PIPELINE = RenderPipelines.GUI_TEXTURED;
	private static final Identifier FOOD_EMPTY_TEXTURE = Identifier.withDefaultNamespace("hud/food_empty");
	private static final Identifier FOOD_HALF_TEXTURE = Identifier.withDefaultNamespace("hud/food_half");
	private static final Identifier FOOD_FULL_TEXTURE = Identifier.withDefaultNamespace("hud/food_full");
	private static final Identifier FOOD_EMPTY_HUNGER_TEXTURE = Identifier.withDefaultNamespace("hud/food_empty_hunger");
	private static final Identifier FOOD_HALF_HUNGER_TEXTURE = Identifier.withDefaultNamespace("hud/food_half_hunger");
	private static final Identifier FOOD_FULL_HUNGER_TEXTURE = Identifier.withDefaultNamespace("hud/food_full_hunger");
	private static final RenderPipeline ARMOR_PIPELINE = RenderPipelines.GUI_TEXTURED;
	private static final Identifier ARMOR_EMPTY_TEXTURE = Identifier.withDefaultNamespace("hud/armor_empty");
	private static final Identifier ARMOR_HALF_TEXTURE = Identifier.withDefaultNamespace("hud/armor_half");
	private static final Identifier ARMOR_FULL_TEXTURE = Identifier.withDefaultNamespace("hud/armor_full");
	private static final RenderPipeline OXYGEN_PIPELINE = RenderPipelines.GUI_TEXTURED;
	private static final Identifier OXYGEN_EMPTY_TEXTURE = Identifier.withDefaultNamespace("hud/air_empty");
	private static final Identifier OXYGEN_POPPING_TEXTURE = Identifier.withDefaultNamespace("hud/air_bursting");
	private static final Identifier OXYGEN_FULL_TEXTURE = Identifier.withDefaultNamespace("hud/air");
	private static final Identifier LUCK_HUD_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/icons/hud-luck.png");
	private static final int HEART_SIZE = 9;
	private static final int HEART_TEXT_SPACING = 2;
	private static final int FOOD_SIZE = 9;
	private static final int FOOD_TEXT_SPACING = 2;
	private static final int ARMOR_SIZE = 9;
	private static final int ARMOR_TEXT_SPACING = 2;
	private static final int ARMOR_ROW_SPACING = 10;
	private static final int OXYGEN_SIZE = 9;
	private static final int OXYGEN_TEXT_SPACING = 2;
	private static final int OXYGEN_X_OFFSET_RIGHT = 4;
	private static final int OXYGEN_RIGHT_EDGE = 91;
	private static final int SECOND_LEFT_VANILLA_AIR_SLOT_INDEX = 8;
	private static final int LUCK_SLOT_SHIFT_RIGHT = 2;
	private static final int LUCK_AIR_SLOT_INDEX = Math.max(0, SECOND_LEFT_VANILLA_AIR_SLOT_INDEX - LUCK_SLOT_SHIFT_RIGHT);
	private static final int FOOD_X_OFFSET_RIGHT = 4;
	private static final int FOOD_RIGHT_EDGE = 91;
	private static final int SECOND_LEFT_VANILLA_FOOD_SLOT_INDEX = 8;
	private static final String HUNGER_BASELINE_TEXT = "Hunger: 20/20";
	private static final String OXYGEN_BASELINE_TEXT = "Oxygen: 30/30";
	private static final String LUCK_BASELINE_TEXT = "Luck: 100%";
	private static final int TICKS_PER_SECOND = 20;
	private static final int OXYGEN_POP_TICKS_PER_SECOND_LOSS = 2;
	private static final float HEALTH_TEXT_SCALE = 0.8F;
	private static final float HUNGER_TEXT_SCALE = 0.8F;
	private static final float ARMOR_TEXT_SCALE = 0.8F;
	private static final float OXYGEN_TEXT_SCALE = 0.8F;
	private static final int COLOR = 0xFFFFFFFF;
	private static final float HEALTH_STEP = 0.125F;
	private static final float ARMOR_STEP = 0.25F;
	private static volatile int cachedAirSupply = 600;
	private static volatile int cachedMaxAirSupply = 600;
	private static volatile int cachedOxygenPoints = 10;
	private static volatile int previousDisplayedOxygenSeconds = -1;
	private static volatile int oxygenPopTicksRemaining;
	private static volatile long lastOxygenStateUpdateTick = Long.MIN_VALUE;

	private HudAttributesManager() {
	}

	public static void initialize() {
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, oldElement -> (context, tickCounter) -> renderHealthHud(context, tickCounter, oldElement));
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, oldElement -> (context, tickCounter) -> renderHungerHud(context, tickCounter, oldElement));
		HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, oldElement -> (context, tickCounter) -> renderArmorHud(context, tickCounter, oldElement));
		HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR, oldElement -> (context, tickCounter) -> renderOxygenHud(context, tickCounter, oldElement));
	}

	public static void reset() {
		clearOxygenHudState();
	}

	static void clearOxygenHudState() {
		cachedMaxAirSupply = Math.max(1, MadokuOxygenManager.getMaximumOxygenTicksForEntity(null));
		cachedAirSupply = cachedMaxAirSupply;
		cachedOxygenPoints = 10;
		previousDisplayedOxygenSeconds = -1;
		oxygenPopTicksRemaining = 0;
		lastOxygenStateUpdateTick = Long.MIN_VALUE;
	}

	static void renderHealthHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, HudElement oldElement) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		if (player == null || level == null) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		if (player.isSpectator()) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		if (!HudConfigManager.isEnabled() || !HudConfigManager.isEnabled("health")) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		// Keep vanilla health timers progressing while hiding its rendered location.
		context.pose().pushMatrix();
		context.pose().translate(-10000.0F, -10000.0F);
		oldElement.extractRenderState(context, tickCounter);
		context.pose().popMatrix();

		float health = roundToStep(player.getHealth(), HEALTH_STEP);
		float effectiveHealth = roundToStep(player.getHealth() + player.getAbsorptionAmount(), HEALTH_STEP);
		float maxHealth = roundToStep(Math.max(1.0F, player.getMaxHealth()), HEALTH_STEP);
		boolean hardcore = level.getLevelData().isHardcore();
		Gui gui = client.gui;
		int ticks = gui.hud.getGuiTicks();
		boolean blinking = isBlinking(gui, ticks);

		int heartX = context.guiWidth() / 2 - 91;
		int vanillaHealthY = context.guiHeight() - HudStatusBarHeightRegistry.getHeight(VanillaHudElements.HEALTH_BAR);
		int heartY = vanillaHealthY;

		if (player.hasEffect(MobEffects.REGENERATION)) {
			int regenIndex = ticks % Math.max(1, (int) Math.ceil(maxHealth + 5.0F));
			if (regenIndex == 0) {
				heartY -= 2;
			}
		}

		if (Math.round(health + player.getAbsorptionAmount()) <= 4) {
			heartY += player.getRandom().nextInt(2);
		}

		Identifier containerTexture = selectContainerTexture(hardcore, blinking);
		Identifier fillTexture = selectHeartTexture(player, health, maxHealth, hardcore, blinking);
		context.blitSprite(HEART_PIPELINE, containerTexture, heartX, heartY, HEART_SIZE, HEART_SIZE);
		if (fillTexture != null) {
			context.blitSprite(HEART_PIPELINE, fillTexture, heartX, heartY, HEART_SIZE, HEART_SIZE);
		}

		String healthText = "Health: " + formatHealth(effectiveHealth) + "/" + formatHealth(maxHealth);
		int textX = heartX + HEART_SIZE + HEART_TEXT_SPACING;
		int textY = heartY + 1;
		context.pose().pushMatrix();
		context.pose().scale(HEALTH_TEXT_SCALE, HEALTH_TEXT_SCALE);
		context.text(
			client.font,
			healthText,
			Math.round(textX / HEALTH_TEXT_SCALE),
			Math.round(textY / HEALTH_TEXT_SCALE),
			COLOR,
			true
		);
		context.pose().popMatrix();
	}

	static void renderHungerHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, HudElement oldElement) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		if (player == null || level == null) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		if (player.isSpectator()) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		if (!HudConfigManager.isEnabled() || !HudConfigManager.isEnabled("hunger")) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		// Keep vanilla food timers progressing while hiding the rendered location.
		context.pose().pushMatrix();
		context.pose().translate(-10000.0F, -10000.0F);
		oldElement.extractRenderState(context, tickCounter);
		context.pose().popMatrix();

		int fallbackMax = Math.max(1, MadokuHungerManager.getConfiguredMaximumHungerPoints());
		int currentHunger;
		int maxHunger;
		if (HudPayloadManager.hasServerHunger()) {
			maxHunger = Math.max(1, HudPayloadManager.getServerHungerMax());
			currentHunger = MadokuHudManager.clamp(HudPayloadManager.getServerHungerCurrent(), 0, maxHunger);
		} else {
			maxHunger = fallbackMax;
			currentHunger = MadokuHudManager.clamp(player.getFoodData().getFoodLevel(), 0, maxHunger);
		}
		float hungerPercent = currentHunger / (float) Math.max(1, maxHunger);

		String hungerText = "Hunger: " + currentHunger + "/" + maxHunger;
		int foodX = computeFoodX(context, client, hungerText);
		int foodY = context.guiHeight() - HudStatusBarHeightRegistry.getHeight(VanillaHudElements.FOOD_BAR);
		boolean hasHungerEffect = player.hasEffect(MobEffects.HUNGER);

		context.blitSprite(FOOD_PIPELINE, selectFoodContainerTexture(hasHungerEffect), foodX, foodY, FOOD_SIZE, FOOD_SIZE);
		Identifier fillTexture = selectFoodFillTexture(hasHungerEffect, hungerPercent);
		if (fillTexture != null) {
			context.blitSprite(FOOD_PIPELINE, fillTexture, foodX, foodY, FOOD_SIZE, FOOD_SIZE);
		}

		int textX = foodX + FOOD_SIZE + FOOD_TEXT_SPACING;
		int textY = foodY + 1;
		context.pose().pushMatrix();
		context.pose().scale(HUNGER_TEXT_SCALE, HUNGER_TEXT_SCALE);
		context.text(
			client.font,
			hungerText,
			Math.round(textX / HUNGER_TEXT_SCALE),
			Math.round(textY / HUNGER_TEXT_SCALE),
			COLOR,
			true
			);
			context.pose().popMatrix();
		}

	static void renderArmorHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, HudElement oldElement) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		if (player == null || level == null) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		if (player.isSpectator()) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		if (!HudConfigManager.isEnabled() || !HudConfigManager.isEnabled("armor")) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		int armorPieces = countArmorPieces(player);
		if (armorPieces <= 0) {
			return;
		}

		// Keep vanilla armor bar state updates while hiding vanilla visuals.
		context.pose().pushMatrix();
		context.pose().translate(-10000.0F, -10000.0F);
		oldElement.extractRenderState(context, tickCounter);
		context.pose().popMatrix();

		int armorX = context.guiWidth() / 2 - 91;
		int armorY = computeArmorY(context);

		context.blitSprite(ARMOR_PIPELINE, ARMOR_EMPTY_TEXTURE, armorX, armorY, ARMOR_SIZE, ARMOR_SIZE);
		Identifier fillTexture = selectArmorFillTexture(armorPieces);
		if (fillTexture != null) {
			context.blitSprite(ARMOR_PIPELINE, fillTexture, armorX, armorY, ARMOR_SIZE, ARMOR_SIZE);
		}

		float armorPoints = roundToStep((float) Math.max(0.0d, player.getAttributeValue(Attributes.ARMOR)), ARMOR_STEP);
		String armorText = "Armor: " + formatHealth(armorPoints);
		int textX = armorX + ARMOR_SIZE + ARMOR_TEXT_SPACING;
		int textY = armorY + 1;
		context.pose().pushMatrix();
		context.pose().scale(ARMOR_TEXT_SCALE, ARMOR_TEXT_SCALE);
		context.text(
			client.font,
			armorText,
			Math.round(textX / ARMOR_TEXT_SCALE),
			Math.round(textY / ARMOR_TEXT_SCALE),
			COLOR,
			true
		);
			context.pose().popMatrix();
		}

	static void renderOxygenHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, HudElement oldElement) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		if (player == null || level == null) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		if (player.isSpectator()) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		if (!HudConfigManager.isEnabled()) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}
		boolean oxygenEnabled = HudConfigManager.isEnabled("oxygen");
		boolean luckEnabled = HudConfigManager.isEnabled("luck") && MadokuLuckManager.isEnabled();
		if (!oxygenEnabled && !luckEnabled) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		updateOxygenState(player, level.getGameTime());

		boolean shouldRenderOxygen = oxygenEnabled && (cachedAirSupply < cachedMaxAirSupply || player.isEyeInFluid(FluidTags.WATER));
		if (!shouldRenderOxygen && !luckEnabled) {
			return;
		}

		int statusY = context.guiHeight() - HudStatusBarHeightRegistry.getHeight(VanillaHudElements.AIR_BAR);
		String statusText;
		int statusX;
		if (shouldRenderOxygen) {
			statusText = buildOxygenTextFromSeconds(cachedAirSupply, cachedMaxAirSupply);
			statusX = computeStatusX(context, client, statusText, OXYGEN_BASELINE_TEXT, SECOND_LEFT_VANILLA_AIR_SLOT_INDEX);
			context.blitSprite(OXYGEN_PIPELINE, selectOxygenTexture(cachedOxygenPoints), statusX, statusY, OXYGEN_SIZE, OXYGEN_SIZE);
		} else {
			statusText = buildLuckText(player);
			statusX = computeStatusX(context, client, statusText, LUCK_BASELINE_TEXT, LUCK_AIR_SLOT_INDEX);
			context.blit(
				OXYGEN_PIPELINE,
				LUCK_HUD_TEXTURE,
				statusX,
				statusY,
				0.0F,
				0.0F,
				OXYGEN_SIZE,
				OXYGEN_SIZE,
				OXYGEN_SIZE,
				OXYGEN_SIZE
			);
		}

		int textX = statusX + OXYGEN_SIZE + OXYGEN_TEXT_SPACING;
		int textY = statusY + 1;
		context.pose().pushMatrix();
		context.pose().scale(OXYGEN_TEXT_SCALE, OXYGEN_TEXT_SCALE);
		context.text(
			client.font,
			statusText,
			Math.round(textX / OXYGEN_TEXT_SCALE),
			Math.round(textY / OXYGEN_TEXT_SCALE),
			COLOR,
			true
		);
		context.pose().popMatrix();
	}


	private static boolean isBlinking(Gui gui, int ticks) {
		long healthBlinkTime = ((GuiAccessor) gui.hud).madokuCraft$getHealthBlinkTime();
		long currentTicks = ticks;
		return healthBlinkTime > currentTicks && ((healthBlinkTime - currentTicks) / 3L) % 2L == 1L;
	}

	private static Identifier selectContainerTexture(boolean hardcore, boolean blinking) {
		if (!hardcore) {
			return blinking ? HEART_EMPTY_BLINKING_TEXTURE : HEART_EMPTY_TEXTURE;
		}
		return blinking ? HEART_EMPTY_HARDCORE_BLINKING_TEXTURE : HEART_EMPTY_HARDCORE_TEXTURE;
	}

	private static Identifier selectFoodContainerTexture(boolean hasHungerEffect) {
		return hasHungerEffect ? FOOD_EMPTY_HUNGER_TEXTURE : FOOD_EMPTY_TEXTURE;
	}

	private static Identifier selectFoodFillTexture(boolean hasHungerEffect, float hungerPercent) {
		if (hungerPercent <= 0.1F) {
			return null;
		}

		boolean half = hungerPercent < 0.9F;
		if (hasHungerEffect) {
			return half ? FOOD_HALF_HUNGER_TEXTURE : FOOD_FULL_HUNGER_TEXTURE;
		}
		return half ? FOOD_HALF_TEXTURE : FOOD_FULL_TEXTURE;
	}

	private static int computeFoodX(GuiGraphicsExtractor context, Minecraft client, String hungerText) {
		int foodRightEdge = context.guiWidth() / 2 + FOOD_RIGHT_EDGE;
		int baselineWidth = getScaledTextWidth(client, HUNGER_BASELINE_TEXT, HUNGER_TEXT_SCALE);
		int currentWidth = getScaledTextWidth(client, hungerText, HUNGER_TEXT_SCALE);
		int baseX = foodRightEdge - FOOD_SIZE - (SECOND_LEFT_VANILLA_FOOD_SLOT_INDEX * 8) + FOOD_X_OFFSET_RIGHT;
		return baseX + (baselineWidth - currentWidth);
	}

	private static int computeStatusX(GuiGraphicsExtractor context, Minecraft client, String statusText, String baselineText, int airSlotIndex) {
		int oxygenRightEdge = context.guiWidth() / 2 + OXYGEN_RIGHT_EDGE;
		int baselineWidth = getScaledTextWidth(client, baselineText, OXYGEN_TEXT_SCALE);
		int currentWidth = getScaledTextWidth(client, statusText, OXYGEN_TEXT_SCALE);
		int baseX = oxygenRightEdge - OXYGEN_SIZE - (Math.max(0, airSlotIndex) * 8) + OXYGEN_X_OFFSET_RIGHT;
		return baseX + (baselineWidth - currentWidth);
	}

	private static int getScaledTextWidth(Minecraft client, String text, float scale) {
		return Math.round(client.font.width(text) * scale);
	}

	private static int computeArmorY(GuiGraphicsExtractor context) {
		int healthY = context.guiHeight() - HudStatusBarHeightRegistry.getHeight(VanillaHudElements.HEALTH_BAR);
		return healthY - ARMOR_ROW_SPACING;
	}

	private static Identifier selectHeartTexture(LocalPlayer player, float health, float maxHealth, boolean hardcore, boolean blinking) {
		if (health <= 0.0F) {
			return null;
		}

		float healthPercent = health / Math.max(1.0F, maxHealth);
		if (healthPercent <= 0.1F) {
			return null;
		}

		boolean half = healthPercent < 0.9F;
		String fill = half ? "half" : "full";
		String hardcorePrefix = hardcore ? "hardcore_" : "";
		String blinkingSuffix = blinking ? "_blinking" : "";

		if (player.hasEffect(MobEffects.WITHER)) {
			return Identifier.withDefaultNamespace("hud/heart/withered_" + hardcorePrefix + fill + blinkingSuffix);
		}

		if (player.getAbsorptionAmount() > 0.0F) {
			return selectAbsorbingTexture(hardcore, half, blinking);
		}

		if (player.hasEffect(MobEffects.POISON)) {
			return Identifier.withDefaultNamespace("hud/heart/poisoned_" + hardcorePrefix + fill + blinkingSuffix);
		}

		if (player.isFullyFrozen()) {
			return Identifier.withDefaultNamespace("hud/heart/frozen_" + hardcorePrefix + fill + blinkingSuffix);
		}

		return Identifier.withDefaultNamespace("hud/heart/" + hardcorePrefix + fill + blinkingSuffix);
	}

	private static Identifier selectAbsorbingTexture(boolean hardcore, boolean half, boolean blinking) {
		if (hardcore) {
			if (half) {
				return blinking ? ABSORBING_HARDCORE_HALF_BLINKING_TEXTURE : ABSORBING_HARDCORE_HALF_TEXTURE;
			}
			return blinking ? ABSORBING_HARDCORE_FULL_BLINKING_TEXTURE : ABSORBING_HARDCORE_FULL_TEXTURE;
		}
		if (half) {
			return blinking ? ABSORBING_HALF_BLINKING_TEXTURE : ABSORBING_HALF_TEXTURE;
		}
		return blinking ? ABSORBING_FULL_BLINKING_TEXTURE : ABSORBING_FULL_TEXTURE;
	}

	private static Identifier selectArmorFillTexture(int armorPieces) {
		if (armorPieces >= 4) {
			return ARMOR_FULL_TEXTURE;
		}
		if (armorPieces >= 2) {
			return ARMOR_HALF_TEXTURE;
		}
		return null;
	}

	private static Identifier selectOxygenTexture(int oxygenPoints) {
		if (oxygenPoints <= 0) {
			return OXYGEN_EMPTY_TEXTURE;
		}
		if (oxygenPopTicksRemaining > 0) {
			return OXYGEN_POPPING_TEXTURE;
		}
		return OXYGEN_FULL_TEXTURE;
	}

	private static int countArmorPieces(LocalPlayer player) {
		if (player == null) {
			return 0;
		}
		int pieces = 0;
		if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) pieces++;
		if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) pieces++;
		if (!player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) pieces++;
		if (!player.getItemBySlot(EquipmentSlot.FEET).isEmpty()) pieces++;
		return pieces;
	}

	private static float roundToStep(float value, float step) {
		if (value <= 0.0F || step <= 0.0F) {
			return 0.0F;
		}
		return Math.round(value / step) * step;
	}

	private static String formatHealth(float value) {
		String text = String.format(Locale.ROOT, "%.3f", value);
		while (text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		if (text.endsWith(".")) {
			text = text.substring(0, text.length() - 1);
		}
		return text;
	}

	private static int toOxygenPoints(int airSupply, int maxAirSupply) {
		double ratio = (Math.max(0, airSupply) * 10.0) / Math.max(1, maxAirSupply);
		return Math.max(0, Math.min(10, (int) Math.ceil(ratio)));
	}

	private static int toDisplaySeconds(int airTicks) {
		return (int) Math.ceil(Math.max(0, airTicks) / (double) TICKS_PER_SECOND);
	}

	private static String buildOxygenTextFromSeconds(int currentAirSupply, int maxAirSupply) {
		int normalizedMax = Math.max(1, maxAirSupply);
		int normalizedCurrent = Math.max(0, Math.min(normalizedMax, currentAirSupply));
		int maxSeconds = Math.max(1, toDisplaySeconds(normalizedMax));
		int currentSeconds = Math.max(0, Math.min(maxSeconds, toDisplaySeconds(normalizedCurrent)));
		return "Oxygen: " + currentSeconds + "/" + maxSeconds;
	}

	private static String buildLuckText(LocalPlayer player) {
		double luckValue = 0.0d;
		if (player != null) {
			AttributeInstance luckAttribute = player.getAttribute(Attributes.LUCK);
			if (luckAttribute != null) {
				luckValue = luckAttribute.getValue();
			}
		}
		if (!Double.isFinite(luckValue)) {
			luckValue = 0.0d;
		}
		return "Luck: " + formatLuckPoints(luckValue);
	}

	private static String formatLuckPoints(double value) {
		long points = Math.max(0L, Math.round(value));
		return points + "%";
	}

	private static void updateOxygenState(LocalPlayer player, long gameTime) {
		if (lastOxygenStateUpdateTick == gameTime) {
			return;
		}
		lastOxygenStateUpdateTick = gameTime;
		cachedMaxAirSupply = Math.max(1, player.getMaxAirSupply());
		cachedAirSupply = MadokuHudManager.clamp(player.getAirSupply(), 0, cachedMaxAirSupply);
		cachedOxygenPoints = toOxygenPoints(cachedAirSupply, cachedMaxAirSupply);
		int displayedSeconds = toDisplaySeconds(cachedAirSupply);
		if (previousDisplayedOxygenSeconds >= 0
			&& displayedSeconds < previousDisplayedOxygenSeconds
			&& displayedSeconds > 0) {
			oxygenPopTicksRemaining = OXYGEN_POP_TICKS_PER_SECOND_LOSS;
			player.playSound(SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.75F, 1.0F);
		} else if (oxygenPopTicksRemaining > 0) {
			oxygenPopTicksRemaining--;
		}
		previousDisplayedOxygenSeconds = displayedSeconds;
	}

}

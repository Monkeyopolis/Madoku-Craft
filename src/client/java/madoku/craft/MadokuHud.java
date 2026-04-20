package madoku.craft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.hunger.MadokuHunger;
import madoku.craft.pet.PlayerEntitiesHolder;
import madoku.craft.pet.PlayerEntitiesInventory;
import madoku.craft.pet.PlayerEntitiesSystem;
import madoku.craft.mixin.client.GuiAccessor;
import madoku.craft.oxygen.MadokuOxygen;
import madoku.craft.season.MadokuSeason;
import madoku.craft.time.MadokuTime;
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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class MadokuHud {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuHud.class);
	private static final Identifier MADOKU_HUD_ID = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_hud");
	private static final Identifier MADOKU_ABILITY_HUD_ID = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_ability_hud");
	private static final Identifier ABILITY_SLOT_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/gui/interface/ability_slot.png");
	private static final int ABILITY_SLOT_TEXTURE_SIZE = 16;
	private static final RenderPipeline HEART_PIPELINE = RenderPipelines.GUI_TEXTURED;
	private static final RenderPipeline ABILITY_SLOT_PIPELINE = RenderPipelines.GUI_TEXTURED;
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
	private static final int WORLD_X = 4;
	private static final int WORLD_Y = 4;
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
	private static final int FOOD_X_OFFSET_RIGHT = 4;
	private static final int FOOD_RIGHT_EDGE = 91;
	private static final int SECOND_LEFT_VANILLA_FOOD_SLOT_INDEX = 8;
	private static final String HUNGER_BASELINE_TEXT = "Hunger: 20/20";
	private static final String OXYGEN_BASELINE_TEXT = "Oxygen: 20/20";
	private static final int VANILLA_MAX_FOOD_LEVEL = 20;
	private static final int TICKS_PER_SECOND = 20;
	private static final int OXYGEN_POP_TICKS_PER_SECOND_LOSS = 2;
	private static final float WORLD_HUD_SCALE = 0.8F;
	private static final float HEALTH_TEXT_SCALE = 0.8F;
	private static final float HUNGER_TEXT_SCALE = 0.8F;
	private static final float ARMOR_TEXT_SCALE = 0.8F;
	private static final float OXYGEN_TEXT_SCALE = 0.8F;
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
	private static final int COLOR = 0xFFFFFFFF;
	private static final float HEALTH_STEP = 0.125F;
	private static final float ARMOR_STEP = 0.25F;
	private static final boolean DEFAULT_WORLD_HUD_ENABLED = true;
	private static final boolean DEFAULT_HEALTH_HUD_ENABLED = true;
	private static final boolean DEFAULT_HUNGER_HUD_ENABLED = true;
	private static final boolean DEFAULT_ARMOR_HUD_ENABLED = true;
	private static final boolean DEFAULT_OXYGEN_HUD_ENABLED = true;
	private static final boolean DEFAULT_SEASON_HUD_ENABLED = true;
	private static final String HUD_CONFIG_FOLDER_NAME = "madoku-craft-hud";
	private static final String HUD_CONFIG_FILE_NAME = "madoku-hud";
	private static volatile boolean initialized = false;
	private static volatile Settings settings = Settings.defaults();
	private static volatile long serverDay = 1L;
	private static volatile int serverHour = 6;
	private static volatile int serverMinute = 0;
	private static volatile boolean hasServerTime = false;
	private static volatile int serverDifficulty = 1;
	private static volatile boolean hasServerDifficulty = false;
	private static volatile String serverSeason = "spring";
	private static volatile boolean hasServerSeason = false;
	private static volatile int serverHungerCurrent = 0;
	private static volatile int serverHungerPending = 0;
	private static volatile int serverHungerMax = VANILLA_MAX_FOOD_LEVEL;
	private static volatile boolean hasServerHunger = false;
	private static final long[] petAbilityCooldownEndTicks = new long[PlayerEntitiesSystem.SLOT_COUNT];
	private static volatile int cachedAirSupply = 300;
	private static volatile int cachedMaxAirSupply = 300;
	private static volatile int cachedOxygenPoints = 10;
	private static volatile int previousDisplayedOxygenSeconds = -1;
	private static volatile int oxygenPopTicksRemaining = 0;
	private static volatile long lastOxygenStateUpdateTick = Long.MIN_VALUE;

	private MadokuHud() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		loadClientConfig();
		initialized = true;
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, MADOKU_HUD_ID, MadokuHud::renderWorldHud);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, MADOKU_ABILITY_HUD_ID, MadokuHud::renderAbilityHud);
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, oldElement -> (context, tickCounter) ->
			renderHealthHud(context, tickCounter, oldElement)
		);
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, oldElement -> (context, tickCounter) ->
			renderHungerHud(context, tickCounter, oldElement)
		);
		HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, oldElement -> (context, tickCounter) ->
			renderArmorHud(context, tickCounter, oldElement)
		);
		HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR, oldElement -> (context, tickCounter) ->
			renderOxygenHud(context, tickCounter, oldElement)
		);
	}

	private static void renderWorldHud(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (!settings.worldHudEnabled) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null) {
			return;
		}

		long day;
		int hour;
		int minute;

		if (hasServerTime) {
			day = serverDay;
			hour = serverHour;
			minute = serverMinute;
		} else {
			long dayTime = level.getOverworldClockTime();
			day = MadokuTime.getDay(dayTime);
			int totalMinutes = MadokuTime.getTotalMinutes(dayTime);
			hour = totalMinutes / 60;
			minute = totalMinutes % 60;
		}

		drawScaledString(context, client, "Day: " + displayDay(day), WORLD_X, WORLD_Y, COLOR);
		int lineIndex = 1;
		drawScaledString(context, client, "Time: " + hour + ":" + twoDigits(minute), WORLD_X, lineOffset(client, lineIndex++), COLOR);
		drawScaledString(context, client, "Biome: " + getBiomeDisplayName(player, level), WORLD_X, lineOffset(client, lineIndex++), COLOR);
		drawScaledString(context, client, "Difficulty: " + getDifficultyDisplayText(), WORLD_X, lineOffset(client, lineIndex++), COLOR);
		if (settings.seasonHudEnabled && MadokuSeason.isEnabled() && hasServerSeason) {
			drawScaledString(context, client, "Season: " + getSeasonDisplayText(), WORLD_X, lineOffset(client, lineIndex), COLOR);
		}
	}

	private static void renderAbilityHud(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.options.hideGui || player.isSpectator() || !PlayerEntitiesSystem.isEnabled()) {
			return;
		}
		if (!(player instanceof PlayerEntitiesHolder holder)) {
			return;
		}

		PlayerEntitiesInventory inventory = holder.madokuCraft$getPlayerEntitiesInventory();
		if (inventory == null) {
			return;
		}

		int slotY = context.guiHeight() - HOTBAR_SLOT_ROW_HEIGHT + ABILITY_SLOT_Y_OFFSET;
		List<Integer> visibleSlots = visibleAbilitySlots(inventory);
		if (visibleSlots.isEmpty()) {
			return;
		}

		int[] slotXs = computeAbilitySlotXs(context, player, visibleSlots.size());
		for (int index = 0; index < visibleSlots.size(); index++) {
			int slot = visibleSlots.get(index);
			int slotX = slotXs[index];
			context.blit(
				ABILITY_SLOT_PIPELINE,
				ABILITY_SLOT_TEXTURE,
				slotX,
				slotY,
				0.0F,
				0.0F,
				ABILITY_SLOT_SIZE,
				ABILITY_SLOT_SIZE,
				ABILITY_SLOT_TEXTURE_SIZE,
				ABILITY_SLOT_TEXTURE_SIZE
			);

			ItemStack stack = inventory.getItem(slot);
			if (stack == null || stack.isEmpty()) {
				continue;
			}

			int itemOffset = (ABILITY_SLOT_SIZE - ABILITY_ITEM_RENDER_SIZE) / 2;
			int itemX = slotX + itemOffset;
			int itemY = slotY + itemOffset;
			renderScaledAbilityItem(context, stack, itemX, itemY);
			renderAbilityCooldownOverlay(context, client, stack, slot, slotX, slotY, itemX, itemY);
		}
	}

	private static void renderHealthHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, HudElement oldElement) {
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

		if (!settings.healthHudEnabled) {
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
		int ticks = gui.getGuiTicks();
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

	private static void renderHungerHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, HudElement oldElement) {
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

		if (!settings.hungerHudEnabled) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		// Keep vanilla food timers progressing while hiding the rendered location.
		context.pose().pushMatrix();
		context.pose().translate(-10000.0F, -10000.0F);
		oldElement.extractRenderState(context, tickCounter);
		context.pose().popMatrix();

		int fallbackMax = Math.max(1, MadokuHunger.getConfiguredMaximumHungerPoints());
		int currentHunger;
		int maxHunger;
		int pendingHunger;
		if (hasServerHunger) {
			maxHunger = Math.max(1, serverHungerMax);
			currentHunger = clampInt(serverHungerCurrent, 0, maxHunger);
			pendingHunger = Math.max(0, serverHungerPending);
		} else {
			int vanillaFoodLevel = clampInt(player.getFoodData().getFoodLevel(), 0, VANILLA_MAX_FOOD_LEVEL);
			maxHunger = fallbackMax;
			currentHunger = clampInt(Math.round((vanillaFoodLevel / (float) VANILLA_MAX_FOOD_LEVEL) * maxHunger), 0, maxHunger);
			pendingHunger = 0;
		}
		float hungerPercent = currentHunger / (float) Math.max(1, maxHunger);

		String hungerText = "Hunger: " + ((long) currentHunger + (long) pendingHunger) + "/" + maxHunger;
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

	private static void renderArmorHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, HudElement oldElement) {
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

		if (!settings.armorHudEnabled) {
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

	private static void renderOxygenHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, HudElement oldElement) {
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

		if (!settings.oxygenHudEnabled) {
			oldElement.extractRenderState(context, tickCounter);
			return;
		}

		updateOxygenState(player, level.getGameTime());

		boolean shouldRender = cachedAirSupply < cachedMaxAirSupply || player.isEyeInFluid(FluidTags.WATER);
		if (!shouldRender) {
			return;
		}

		String oxygenText = buildOxygenTextFromSeconds(cachedAirSupply, cachedMaxAirSupply);
		int oxygenX = computeOxygenX(context, client, oxygenText);
		int oxygenY = context.guiHeight() - HudStatusBarHeightRegistry.getHeight(VanillaHudElements.AIR_BAR);
		context.blitSprite(OXYGEN_PIPELINE, selectOxygenTexture(cachedOxygenPoints), oxygenX, oxygenY, OXYGEN_SIZE, OXYGEN_SIZE);

		int textX = oxygenX + OXYGEN_SIZE + OXYGEN_TEXT_SPACING;
		int textY = oxygenY + 1;
		context.pose().pushMatrix();
		context.pose().scale(OXYGEN_TEXT_SCALE, OXYGEN_TEXT_SCALE);
		context.text(
			client.font,
			oxygenText,
			Math.round(textX / OXYGEN_TEXT_SCALE),
			Math.round(textY / OXYGEN_TEXT_SCALE),
			COLOR,
			true
		);
		context.pose().popMatrix();
	}

	private static int lineOffset(Minecraft client, int lines) {
		int lineStep = Math.round((client.font.lineHeight + 4) * WORLD_HUD_SCALE);
		return WORLD_Y + (lineStep * lines);
	}

	private static void drawScaledString(GuiGraphicsExtractor context, Minecraft client, String text, int x, int y, int color) {
		context.pose().pushMatrix();
		context.pose().translate(x, y);
		context.pose().scale(WORLD_HUD_SCALE, WORLD_HUD_SCALE);
		context.text(client.font, text, 0, 0, color, true);
		context.pose().popMatrix();
	}

	private static String twoDigits(int value) {
		return value < 10 ? "0" + value : Integer.toString(value);
	}

	private static String getBiomeDisplayName(LocalPlayer player, ClientLevel level) {
		return level.getBiome(player.blockPosition())
			.unwrapKey()
			.map(key -> biomeIdentifierToName(key.identifier()))
			.orElse("Unknown");
	}

	private static String biomeIdentifierToName(Identifier biomeIdentifier) {
		String normalized = biomeIdentifier.getPath().replace('_', ' ').replace('/', ' ');
		String[] words = normalized.split(" ");
		StringBuilder builder = new StringBuilder();
		for (String word : words) {
			if (word.isEmpty()) {
				continue;
			}
			if (!builder.isEmpty()) {
				builder.append(' ');
			}
			builder.append(Character.toUpperCase(word.charAt(0)));
			if (word.length() > 1) {
				builder.append(word.substring(1));
			}
		}
		return builder.isEmpty() ? "Unknown" : builder.toString();
	}

	private static long displayDay(long rawDay) {
		return rawDay + 1L;
	}

	private static String getDifficultyDisplayText() {
		if (!hasServerDifficulty) {
			return "1";
		}
		return Integer.toString(Math.max(1, serverDifficulty));
	}

	private static String getSeasonDisplayText() {
		if (!hasServerSeason) {
			return "Unknown";
		}
		return capitalizeWord(serverSeason);
	}

	private static String capitalizeWord(String value) {
		if (value == null || value.isBlank()) {
			return "Unknown";
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
	}

	private static boolean isBlinking(Gui gui, int ticks) {
		long healthBlinkTime = ((GuiAccessor) gui).madokuCraft$getHealthBlinkTime();
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

	private static int computeOxygenX(GuiGraphicsExtractor context, Minecraft client, String oxygenText) {
		int oxygenRightEdge = context.guiWidth() / 2 + OXYGEN_RIGHT_EDGE;
		int baselineWidth = getScaledTextWidth(client, OXYGEN_BASELINE_TEXT, OXYGEN_TEXT_SCALE);
		int currentWidth = getScaledTextWidth(client, oxygenText, OXYGEN_TEXT_SCALE);
		int baseX = oxygenRightEdge - OXYGEN_SIZE - (SECOND_LEFT_VANILLA_AIR_SLOT_INDEX * 8) + OXYGEN_X_OFFSET_RIGHT;
		return baseX + (baselineWidth - currentWidth);
	}

	private static int getScaledTextWidth(Minecraft client, String text, float scale) {
		return Math.round(client.font.width(text) * scale);
	}

	private static List<Integer> visibleAbilitySlots(PlayerEntitiesInventory inventory) {
		List<Integer> visible = new java.util.ArrayList<>(PlayerEntitiesSystem.SLOT_COUNT);
		for (int slot = 0; slot < Math.min(PlayerEntitiesSystem.SLOT_COUNT, inventory.getContainerSize()); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack == null || stack.isEmpty() || !PlayerEntitiesSystem.hasAbility(stack)) {
				continue;
			}
			visible.add(slot);
		}
		return visible;
	}

	private static void renderAbilityCooldownOverlay(
		GuiGraphicsExtractor context,
		Minecraft client,
		ItemStack stack,
		int slot,
		int slotX,
		int slotY,
		int itemX,
		int itemY
	) {
		if (client == null || client.level == null || stack == null || stack.isEmpty()) {
			return;
		}

		int totalCooldownTicks = PlayerEntitiesSystem.abilityCooldownTicks(client.player, slot, stack);
		if (totalCooldownTicks <= 0 || slot < 0 || slot >= petAbilityCooldownEndTicks.length) {
			return;
		}

		long remainingTicks = Math.max(0L, petAbilityCooldownEndTicks[slot] - client.level.getGameTime());
		if (remainingTicks <= 0L) {
			return;
		}

		float cooldownPercent = Math.min(1.0F, remainingTicks / (float) totalCooldownTicks);
		int overlayTop = itemY + net.minecraft.util.Mth.floor(ABILITY_ITEM_RENDER_SIZE * (1.0F - cooldownPercent));
		int overlayBottom = overlayTop + net.minecraft.util.Mth.ceil(ABILITY_ITEM_RENDER_SIZE * cooldownPercent);
		context.fill(RenderPipelines.GUI, itemX, overlayTop, itemX + ABILITY_ITEM_RENDER_SIZE, overlayBottom, ABILITY_COOLDOWN_OVERLAY_COLOR);
		renderAbilityCooldownLabel(context, client, slotX, slotY, remainingTicks);
	}

	private static void renderAbilityCooldownLabel(
		GuiGraphicsExtractor context,
		Minecraft client,
		int slotX,
		int slotY,
		long remainingTicks
	) {
		if (client == null || remainingTicks <= 0L) {
			return;
		}

		String cooldownText = Integer.toString(Math.max(1, net.minecraft.util.Mth.ceil(remainingTicks / 20.0F)));
		int textWidth = client.font.width(cooldownText);
		float centerX = slotX + (ABILITY_SLOT_SIZE / 2.0F);
		int textY = slotY - Math.round(client.font.lineHeight * ABILITY_COOLDOWN_TEXT_SCALE) - ABILITY_COOLDOWN_TEXT_Y_SPACING;
		context.pose().pushMatrix();
		context.pose().translate(centerX, textY);
		context.pose().scale(ABILITY_COOLDOWN_TEXT_SCALE, ABILITY_COOLDOWN_TEXT_SCALE);
		context.text(
			client.font,
			cooldownText,
			Math.round(-textWidth / 2.0F),
			0,
			ABILITY_COOLDOWN_TEXT_COLOR,
			true
		);
		context.pose().popMatrix();
	}

	private static void renderScaledAbilityItem(GuiGraphicsExtractor context, ItemStack stack, int x, int y) {
		if (context == null || stack == null || stack.isEmpty()) {
			return;
		}
		context.pose().pushMatrix();
		context.pose().translate(x, y);
		context.pose().scale(ABILITY_ITEM_SCALE, ABILITY_ITEM_SCALE);
		context.item(stack, 0, 0);
		context.pose().popMatrix();
	}

	private static int[] computeAbilitySlotXs(GuiGraphicsExtractor context, LocalPlayer player, int slotCount) {
		int[] xs = new int[Math.max(0, slotCount)];
		if (slotCount <= 0) {
			return xs;
		}
		int centerX = context.guiWidth() / 2;
		boolean offhandOnLeft = player.getMainArm() == HumanoidArm.RIGHT;
		boolean offhandVisible = player != null && !player.getOffhandItem().isEmpty();
		int hotbarLeftEdge = centerX - HOTBAR_HALF_WIDTH;
		int hotbarRightEdge = centerX + HOTBAR_HALF_WIDTH;
		if (offhandOnLeft) {
			int anchorX = offhandVisible ? hotbarLeftEdge - OFFHAND_SLOT_WIDTH : hotbarLeftEdge;
			int startX = anchorX - OFFHAND_TO_ABILITY_SPACING - ABILITY_SLOT_SIZE;
			for (int slot = 0; slot < xs.length; slot++) {
				xs[slot] = startX - (slot * (ABILITY_SLOT_SIZE + ABILITY_SLOT_SPACING));
			}
			return xs;
		}

		int anchorX = offhandVisible ? hotbarRightEdge + OFFHAND_SLOT_WIDTH : hotbarRightEdge;
		int startX = anchorX + OFFHAND_TO_ABILITY_SPACING;
		for (int slot = 0; slot < xs.length; slot++) {
			xs[slot] = startX + (slot * (ABILITY_SLOT_SIZE + ABILITY_SLOT_SPACING));
		}
		return xs;
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

	private static void updateOxygenState(LocalPlayer player, long gameTime) {
		if (lastOxygenStateUpdateTick == gameTime) {
			return;
		}
		lastOxygenStateUpdateTick = gameTime;
		cachedMaxAirSupply = Math.max(1, MadokuOxygen.getMaximumOxygenTicksForEntity(player));
		cachedAirSupply = clampInt(player.getAirSupply(), 0, cachedMaxAirSupply);
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

	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static void loadClientConfig() {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path directory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(HUD_CONFIG_FOLDER_NAME);
			Path configFile = resolveJsonFile(directory, HUD_CONFIG_FILE_NAME);
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(configFile, defaults);
			Settings loaded = Settings.fromJson(normalized);
			JsonStaticSystem.writeManagedFile(configFile, loaded.toConfigJson(), defaults);
			settings = loaded;
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load MadokuHud client config; using defaults.", exception);
		}
	}

	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Config file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		try {
			return element.getAsBoolean();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	public static void setServerTime(long day, int hour, int minute) {
		serverDay = day;
		serverHour = hour;
		serverMinute = minute;
		hasServerTime = true;
	}

	public static void clearServerTime() {
		hasServerTime = false;
	}

	public static void setServerDifficulty(int level) {
		serverDifficulty = Math.max(1, level);
		hasServerDifficulty = true;
	}

	public static void clearServerDifficulty() {
		serverDifficulty = 1;
		hasServerDifficulty = false;
	}

	public static void setServerSeason(String season) {
		if (season == null || season.isBlank()) {
			clearServerSeason();
			return;
		}
		serverSeason = season == null ? "unknown" : season;
		hasServerSeason = true;
	}

	public static boolean hasServerSeason() {
		return hasServerSeason;
	}

	public static String getServerSeason() {
		return serverSeason;
	}

	public static void clearServerSeason() {
		serverSeason = "spring";
		hasServerSeason = false;
	}

	public static void setPetAbilityCooldowns(int[] remainingTicks) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		long now = level == null ? 0L : level.getGameTime();
		for (int slot = 0; slot < petAbilityCooldownEndTicks.length; slot++) {
			int remaining = remainingTicks != null && slot < remainingTicks.length ? Math.max(0, remainingTicks[slot]) : 0;
			petAbilityCooldownEndTicks[slot] = remaining <= 0 ? 0L : now + remaining;
		}
	}

	public static void clearPetAbilityHudState() {
		for (int slot = 0; slot < petAbilityCooldownEndTicks.length; slot++) {
			petAbilityCooldownEndTicks[slot] = 0L;
		}
	}

	public static void setServerHunger(int current, int pending, int max) {
		serverHungerCurrent = Math.max(0, current);
		serverHungerPending = Math.max(0, pending);
		serverHungerMax = Math.max(1, max);
		hasServerHunger = true;
	}

	public static void clearServerHunger() {
		serverHungerCurrent = 0;
		serverHungerPending = 0;
		serverHungerMax = VANILLA_MAX_FOOD_LEVEL;
		hasServerHunger = false;
	}

	public static void clearOxygenHudState() {
		cachedMaxAirSupply = Math.max(1, MadokuOxygen.getMaximumOxygenTicksForEntity(null));
		cachedAirSupply = cachedMaxAirSupply;
		cachedOxygenPoints = 10;
		previousDisplayedOxygenSeconds = -1;
		oxygenPopTicksRemaining = 0;
		lastOxygenStateUpdateTick = Long.MIN_VALUE;
	}

	public static boolean canConsumeFoodClient(boolean ignoreHunger) {
		if (ignoreHunger) {
			return true;
		}
		if (!hasServerHunger) {
			return true;
		}
		long total = (long) Math.max(0, serverHungerCurrent) + (long) Math.max(0, serverHungerPending);
		return total < Math.max(1, serverHungerMax);
	}

	private static final class Settings {
		private final boolean worldHudEnabled;
			private final boolean healthHudEnabled;
			private final boolean hungerHudEnabled;
			private final boolean armorHudEnabled;
			private final boolean oxygenHudEnabled;
			private final boolean seasonHudEnabled;

			private Settings(
				boolean worldHudEnabled,
				boolean healthHudEnabled,
				boolean hungerHudEnabled,
				boolean armorHudEnabled,
				boolean oxygenHudEnabled,
				boolean seasonHudEnabled
			) {
				this.worldHudEnabled = worldHudEnabled;
				this.healthHudEnabled = healthHudEnabled;
				this.hungerHudEnabled = hungerHudEnabled;
				this.armorHudEnabled = armorHudEnabled;
				this.oxygenHudEnabled = oxygenHudEnabled;
				this.seasonHudEnabled = seasonHudEnabled;
			}

		private static Settings defaults() {
			return new Settings(
				DEFAULT_WORLD_HUD_ENABLED,
					DEFAULT_HEALTH_HUD_ENABLED,
					DEFAULT_HUNGER_HUD_ENABLED,
					DEFAULT_ARMOR_HUD_ENABLED,
					DEFAULT_OXYGEN_HUD_ENABLED,
					DEFAULT_SEASON_HUD_ENABLED
				);
			}

		private static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			return new Settings(
				getBoolean(source, "world_hud_enabled", defaults.worldHudEnabled),
					getBoolean(source, "health_hud_enabled", defaults.healthHudEnabled),
					getBoolean(source, "hunger_hud_enabled", defaults.hungerHudEnabled),
					getBoolean(source, "armor_hud_enabled", defaults.armorHudEnabled),
					getBoolean(source, "oxygen_hud_enabled", defaults.oxygenHudEnabled),
					getBoolean(source, "season_hud_enabled", defaults.seasonHudEnabled)
				);
			}

		private JsonObject toConfigJson() {
			JsonObject root = new JsonObject();
			root.addProperty("world_hud_enabled", worldHudEnabled);
			root.addProperty("health_hud_enabled", healthHudEnabled);
				root.addProperty("hunger_hud_enabled", hungerHudEnabled);
				root.addProperty("armor_hud_enabled", armorHudEnabled);
				root.addProperty("oxygen_hud_enabled", oxygenHudEnabled);
				root.addProperty("season_hud_enabled", seasonHudEnabled);
				return root;
			}
	}
}

package madoku.craft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.config.StaticJsonSystem;
import madoku.craft.hunger.MadokuHunger;
import madoku.craft.mixin.client.GuiAccessor;
import madoku.craft.oxygen.MadokuOxygen;
import madoku.craft.season.MadokuSeason;
import madoku.craft.time.MadokuTime;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public final class MadokuHud {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuHud.class);
	private static final ResourceLocation HEART_EMPTY_TEXTURE = ResourceLocation.withDefaultNamespace("hud/heart/container");
	private static final ResourceLocation HEART_EMPTY_BLINKING_TEXTURE = ResourceLocation.withDefaultNamespace("hud/heart/container_blinking");
	private static final ResourceLocation HEART_EMPTY_HARDCORE_TEXTURE = ResourceLocation.withDefaultNamespace("hud/heart/container_hardcore");
	private static final ResourceLocation HEART_EMPTY_HARDCORE_BLINKING_TEXTURE = ResourceLocation.withDefaultNamespace("hud/heart/container_hardcore_blinking");
	private static final ResourceLocation ABSORBING_FULL_TEXTURE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full");
	private static final ResourceLocation ABSORBING_FULL_BLINKING_TEXTURE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full_blinking");
	private static final ResourceLocation ABSORBING_HALF_TEXTURE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_half");
	private static final ResourceLocation ABSORBING_HALF_BLINKING_TEXTURE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_half_blinking");
	private static final ResourceLocation ABSORBING_HARDCORE_FULL_TEXTURE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_hardcore_full");
	private static final ResourceLocation ABSORBING_HARDCORE_FULL_BLINKING_TEXTURE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_hardcore_full_blinking");
	private static final ResourceLocation ABSORBING_HARDCORE_HALF_TEXTURE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_hardcore_half");
	private static final ResourceLocation ABSORBING_HARDCORE_HALF_BLINKING_TEXTURE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_hardcore_half_blinking");
	private static final ResourceLocation FOOD_EMPTY_TEXTURE = ResourceLocation.withDefaultNamespace("hud/food_empty");
	private static final ResourceLocation FOOD_HALF_TEXTURE = ResourceLocation.withDefaultNamespace("hud/food_half");
	private static final ResourceLocation FOOD_FULL_TEXTURE = ResourceLocation.withDefaultNamespace("hud/food_full");
	private static final ResourceLocation FOOD_EMPTY_HUNGER_TEXTURE = ResourceLocation.withDefaultNamespace("hud/food_empty_hunger");
	private static final ResourceLocation FOOD_HALF_HUNGER_TEXTURE = ResourceLocation.withDefaultNamespace("hud/food_half_hunger");
	private static final ResourceLocation FOOD_FULL_HUNGER_TEXTURE = ResourceLocation.withDefaultNamespace("hud/food_full_hunger");
	private static final ResourceLocation ARMOR_EMPTY_TEXTURE = ResourceLocation.withDefaultNamespace("hud/armor_empty");
	private static final ResourceLocation ARMOR_HALF_TEXTURE = ResourceLocation.withDefaultNamespace("hud/armor_half");
	private static final ResourceLocation ARMOR_FULL_TEXTURE = ResourceLocation.withDefaultNamespace("hud/armor_full");
	private static final ResourceLocation OXYGEN_EMPTY_TEXTURE = ResourceLocation.withDefaultNamespace("hud/air_bursting");
	private static final ResourceLocation OXYGEN_POPPING_TEXTURE = ResourceLocation.withDefaultNamespace("hud/air_bursting");
	private static final ResourceLocation OXYGEN_FULL_TEXTURE = ResourceLocation.withDefaultNamespace("hud/air");
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
	private static final int STATUS_BAR_BOTTOM_OFFSET = 39;
	private static final int OXYGEN_BAR_ROW_OFFSET = 10;
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
	private static final long HUNGER_DISPLAY_STEP_TICKS = 10L;
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
	private static volatile long smoothedDisplayedHunger = -1L;
	private static volatile long nextHungerDisplayStepTick = Long.MIN_VALUE;
	private static volatile int smoothedDisplayMaxHunger = VANILLA_MAX_FOOD_LEVEL;
	private static volatile long lastHungerDisplayTarget = -1L;
	private static volatile boolean smoothUpFromPendingIncrease = false;
	private static volatile boolean smoothDownFromPendingDecrease = false;
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
		HudRenderCallback.EVENT.register((context, tickCounter) -> renderHud(context));
	}

	private static void renderHud(GuiGraphics context) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}

		renderWorldHud(context);
		renderHealthHud(context);
		renderHungerHud(context);
		renderArmorHud(context);
		renderOxygenHud(context);
	}

	private static void renderWorldHud(GuiGraphics context) {
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
			long dayTime = level.getDayTime();
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

	private static void renderHealthHud(GuiGraphics context) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		if (player == null || level == null || player.isSpectator() || player.isCreative() || !settings.healthHudEnabled) {
			return;
		}

		float health = roundToStep(player.getHealth(), HEALTH_STEP);
		float effectiveHealth = roundToStep(player.getHealth() + player.getAbsorptionAmount(), HEALTH_STEP);
		float maxHealth = roundToStep(Math.max(1.0F, player.getMaxHealth()), HEALTH_STEP);
		boolean hardcore = level.getLevelData().isHardcore();
		Gui gui = client.gui;
		int ticks = gui.getGuiTicks();
		boolean blinking = isBlinking(gui, ticks);

		int heartX = context.guiWidth() / 2 - 91;
		int heartY = statusBarRowY(context);

		if (player.hasEffect(MobEffects.REGENERATION)) {
			int regenIndex = ticks % Math.max(1, (int) Math.ceil(maxHealth + 5.0F));
			if (regenIndex == 0) {
				heartY -= 2;
			}
		}

		if (Math.round(health + player.getAbsorptionAmount()) <= 4) {
			heartY += player.getRandom().nextInt(2);
		}

		ResourceLocation containerTexture = selectContainerTexture(hardcore, blinking);
		ResourceLocation fillTexture = selectHeartTexture(player, health, maxHealth, hardcore, blinking);
		context.blitSprite(containerTexture, heartX, heartY, HEART_SIZE, HEART_SIZE);
		if (fillTexture != null) {
			context.blitSprite(fillTexture, heartX, heartY, HEART_SIZE, HEART_SIZE);
		}

		String healthText = "Health: " + formatHealth(effectiveHealth) + "/" + formatHealth(maxHealth);
		int textX = heartX + HEART_SIZE + HEART_TEXT_SPACING;
		int textY = heartY + 1;
		context.pose().pushPose();
		context.pose().scale(HEALTH_TEXT_SCALE, HEALTH_TEXT_SCALE, 1.0F);
		context.drawString(
			client.font,
			healthText,
			Math.round(textX / HEALTH_TEXT_SCALE),
			Math.round(textY / HEALTH_TEXT_SCALE),
			COLOR,
			true
		);
		context.pose().popPose();
	}

	private static void renderHungerHud(GuiGraphics context) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		if (player == null || level == null || player.isSpectator() || player.isCreative() || !settings.hungerHudEnabled) {
			return;
		}

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

		long targetDisplayedHunger = (long) currentHunger + (long) pendingHunger;
		long displayedHunger = targetDisplayedHunger;
		if (hasServerHunger) {
			long nowTick = level.getGameTime();
			if (smoothedDisplayedHunger < 0L || smoothedDisplayMaxHunger != maxHunger) {
				smoothedDisplayedHunger = currentHunger;
				smoothedDisplayMaxHunger = maxHunger;
				lastHungerDisplayTarget = targetDisplayedHunger;
				nextHungerDisplayStepTick = nowTick + HUNGER_DISPLAY_STEP_TICKS;
			}
			if (smoothedDisplayedHunger < currentHunger) {
				smoothedDisplayedHunger = currentHunger;
			}
			if (lastHungerDisplayTarget != targetDisplayedHunger) {
				lastHungerDisplayTarget = targetDisplayedHunger;
				if (smoothedDisplayedHunger != targetDisplayedHunger) {
					nextHungerDisplayStepTick = nowTick + HUNGER_DISPLAY_STEP_TICKS;
				}
			}
			if (targetDisplayedHunger < smoothedDisplayedHunger && !smoothDownFromPendingDecrease) {
				smoothedDisplayedHunger = targetDisplayedHunger;
				nextHungerDisplayStepTick = nowTick + HUNGER_DISPLAY_STEP_TICKS;
			}
			if (targetDisplayedHunger > smoothedDisplayedHunger && !smoothUpFromPendingIncrease) {
				smoothedDisplayedHunger = targetDisplayedHunger;
				nextHungerDisplayStepTick = nowTick + HUNGER_DISPLAY_STEP_TICKS;
			}
			if (targetDisplayedHunger != smoothedDisplayedHunger && nowTick >= nextHungerDisplayStepTick) {
				if (targetDisplayedHunger > smoothedDisplayedHunger && smoothUpFromPendingIncrease) {
					smoothedDisplayedHunger = Math.min(targetDisplayedHunger, smoothedDisplayedHunger + 1L);
				} else if (smoothDownFromPendingDecrease) {
					smoothedDisplayedHunger = Math.max(targetDisplayedHunger, smoothedDisplayedHunger - 1L);
				}
				nextHungerDisplayStepTick = nowTick + HUNGER_DISPLAY_STEP_TICKS;
			}
			if (smoothedDisplayedHunger >= targetDisplayedHunger) {
				smoothUpFromPendingIncrease = false;
			}
			if (smoothedDisplayedHunger <= targetDisplayedHunger) {
				smoothDownFromPendingDecrease = false;
			}
			displayedHunger = smoothedDisplayedHunger;
		} else {
			smoothedDisplayedHunger = -1L;
			nextHungerDisplayStepTick = Long.MIN_VALUE;
			smoothedDisplayMaxHunger = VANILLA_MAX_FOOD_LEVEL;
			lastHungerDisplayTarget = -1L;
			smoothUpFromPendingIncrease = false;
			smoothDownFromPendingDecrease = false;
		}

		String hungerText = "Hunger: " + displayedHunger + "/" + maxHunger;
		int foodX = computeFoodX(context, client, hungerText);
		int foodY = statusBarRowY(context);
		boolean hasHungerEffect = player.hasEffect(MobEffects.HUNGER);

		context.blitSprite(selectFoodContainerTexture(hasHungerEffect), foodX, foodY, FOOD_SIZE, FOOD_SIZE);
		ResourceLocation fillTexture = selectFoodFillTexture(hasHungerEffect, hungerPercent);
		if (fillTexture != null) {
			context.blitSprite(fillTexture, foodX, foodY, FOOD_SIZE, FOOD_SIZE);
		}

		int textX = foodX + FOOD_SIZE + FOOD_TEXT_SPACING;
		int textY = foodY + 1;
		context.pose().pushPose();
		context.pose().scale(HUNGER_TEXT_SCALE, HUNGER_TEXT_SCALE, 1.0F);
		context.drawString(
			client.font,
			hungerText,
			Math.round(textX / HUNGER_TEXT_SCALE),
			Math.round(textY / HUNGER_TEXT_SCALE),
			COLOR,
			true
			);
			context.pose().popPose();
		}

	private static void renderArmorHud(GuiGraphics context) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || player.isSpectator() || player.isCreative() || !settings.armorHudEnabled) {
			return;
		}

		int armorPieces = countArmorPieces(player);
		if (armorPieces <= 0) {
			return;
		}

		int armorX = context.guiWidth() / 2 - 91;
		int armorY = computeArmorY(context);

		context.blitSprite(ARMOR_EMPTY_TEXTURE, armorX, armorY, ARMOR_SIZE, ARMOR_SIZE);
		ResourceLocation fillTexture = selectArmorFillTexture(armorPieces);
		if (fillTexture != null) {
			context.blitSprite(fillTexture, armorX, armorY, ARMOR_SIZE, ARMOR_SIZE);
		}

		float armorPoints = roundToStep((float) Math.max(0.0d, player.getAttributeValue(Attributes.ARMOR)), ARMOR_STEP);
		String armorText = "Armor: " + formatHealth(armorPoints);
		int textX = armorX + ARMOR_SIZE + ARMOR_TEXT_SPACING;
		int textY = armorY + 1;
		context.pose().pushPose();
		context.pose().scale(ARMOR_TEXT_SCALE, ARMOR_TEXT_SCALE, 1.0F);
		context.drawString(
			client.font,
			armorText,
			Math.round(textX / ARMOR_TEXT_SCALE),
			Math.round(textY / ARMOR_TEXT_SCALE),
			COLOR,
			true
		);
			context.pose().popPose();
		}

	private static void renderOxygenHud(GuiGraphics context) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		if (player == null || level == null || player.isSpectator() || player.isCreative() || !settings.oxygenHudEnabled) {
			return;
		}

		updateOxygenState(player, level.getGameTime());

		boolean shouldRender = cachedAirSupply < cachedMaxAirSupply || player.isEyeInFluid(FluidTags.WATER);
		if (!shouldRender) {
			return;
		}

		String oxygenText = buildOxygenTextFromSeconds(cachedAirSupply, cachedMaxAirSupply);
		int oxygenX = computeOxygenX(context, client, oxygenText);
		int oxygenY = computeOxygenY(context);
		context.blitSprite(selectOxygenTexture(cachedOxygenPoints), oxygenX, oxygenY, OXYGEN_SIZE, OXYGEN_SIZE);

		int textX = oxygenX + OXYGEN_SIZE + OXYGEN_TEXT_SPACING;
		int textY = oxygenY + 1;
		context.pose().pushPose();
		context.pose().scale(OXYGEN_TEXT_SCALE, OXYGEN_TEXT_SCALE, 1.0F);
		context.drawString(
			client.font,
			oxygenText,
			Math.round(textX / OXYGEN_TEXT_SCALE),
			Math.round(textY / OXYGEN_TEXT_SCALE),
			COLOR,
			true
		);
		context.pose().popPose();
	}

	private static int lineOffset(Minecraft client, int lines) {
		int lineStep = Math.round((client.font.lineHeight + 4) * WORLD_HUD_SCALE);
		return WORLD_Y + (lineStep * lines);
	}

	private static void drawScaledString(GuiGraphics context, Minecraft client, String text, int x, int y, int color) {
		context.pose().pushPose();
		context.pose().translate(x, y, 0.0F);
		context.pose().scale(WORLD_HUD_SCALE, WORLD_HUD_SCALE, 1.0F);
		context.drawString(client.font, text, 0, 0, color, true);
		context.pose().popPose();
	}

	private static String twoDigits(int value) {
		return value < 10 ? "0" + value : Integer.toString(value);
	}

	private static String getBiomeDisplayName(LocalPlayer player, ClientLevel level) {
		return level.getBiome(player.blockPosition())
			.unwrapKey()
			.map(key -> biomeIdentifierToName(key.location()))
			.orElse("Unknown");
	}

	private static String biomeIdentifierToName(ResourceLocation biomeIdentifier) {
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

	private static ResourceLocation selectContainerTexture(boolean hardcore, boolean blinking) {
		if (!hardcore) {
			return blinking ? HEART_EMPTY_BLINKING_TEXTURE : HEART_EMPTY_TEXTURE;
		}
		return blinking ? HEART_EMPTY_HARDCORE_BLINKING_TEXTURE : HEART_EMPTY_HARDCORE_TEXTURE;
	}

	private static ResourceLocation selectFoodContainerTexture(boolean hasHungerEffect) {
		return hasHungerEffect ? FOOD_EMPTY_HUNGER_TEXTURE : FOOD_EMPTY_TEXTURE;
	}

	private static ResourceLocation selectFoodFillTexture(boolean hasHungerEffect, float hungerPercent) {
		if (hungerPercent <= 0.1F) {
			return null;
		}

		boolean half = hungerPercent < 0.9F;
		if (hasHungerEffect) {
			return half ? FOOD_HALF_HUNGER_TEXTURE : FOOD_FULL_HUNGER_TEXTURE;
		}
		return half ? FOOD_HALF_TEXTURE : FOOD_FULL_TEXTURE;
	}

	private static int computeFoodX(GuiGraphics context, Minecraft client, String hungerText) {
		int foodRightEdge = context.guiWidth() / 2 + FOOD_RIGHT_EDGE;
		int baselineWidth = getScaledTextWidth(client, HUNGER_BASELINE_TEXT, HUNGER_TEXT_SCALE);
		int currentWidth = getScaledTextWidth(client, hungerText, HUNGER_TEXT_SCALE);
		int baseX = foodRightEdge - FOOD_SIZE - (SECOND_LEFT_VANILLA_FOOD_SLOT_INDEX * 8) + FOOD_X_OFFSET_RIGHT;
		return baseX + (baselineWidth - currentWidth);
	}

	private static int computeOxygenX(GuiGraphics context, Minecraft client, String oxygenText) {
		int oxygenRightEdge = context.guiWidth() / 2 + OXYGEN_RIGHT_EDGE;
		int baselineWidth = getScaledTextWidth(client, OXYGEN_BASELINE_TEXT, OXYGEN_TEXT_SCALE);
		int currentWidth = getScaledTextWidth(client, oxygenText, OXYGEN_TEXT_SCALE);
		int baseX = oxygenRightEdge - OXYGEN_SIZE - (SECOND_LEFT_VANILLA_AIR_SLOT_INDEX * 8) + OXYGEN_X_OFFSET_RIGHT;
		return baseX + (baselineWidth - currentWidth);
	}

	private static int getScaledTextWidth(Minecraft client, String text, float scale) {
		return Math.round(client.font.width(text) * scale);
	}

	private static int statusBarRowY(GuiGraphics context) {
		return context.guiHeight() - STATUS_BAR_BOTTOM_OFFSET;
	}

	private static int computeOxygenY(GuiGraphics context) {
		return statusBarRowY(context) - OXYGEN_BAR_ROW_OFFSET;
	}

	private static int computeArmorY(GuiGraphics context) {
		return statusBarRowY(context) - ARMOR_ROW_SPACING;
	}

	private static ResourceLocation selectHeartTexture(LocalPlayer player, float health, float maxHealth, boolean hardcore, boolean blinking) {
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
			return ResourceLocation.withDefaultNamespace("hud/heart/withered_" + hardcorePrefix + fill + blinkingSuffix);
		}

		if (player.getAbsorptionAmount() > 0.0F) {
			return selectAbsorbingTexture(hardcore, half, blinking);
		}

		if (player.hasEffect(MobEffects.POISON)) {
			return ResourceLocation.withDefaultNamespace("hud/heart/poisoned_" + hardcorePrefix + fill + blinkingSuffix);
		}

		if (player.isFullyFrozen()) {
			return ResourceLocation.withDefaultNamespace("hud/heart/frozen_" + hardcorePrefix + fill + blinkingSuffix);
		}

		return ResourceLocation.withDefaultNamespace("hud/heart/" + hardcorePrefix + fill + blinkingSuffix);
	}

	private static ResourceLocation selectAbsorbingTexture(boolean hardcore, boolean half, boolean blinking) {
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

	private static ResourceLocation selectArmorFillTexture(int armorPieces) {
		if (armorPieces >= 4) {
			return ARMOR_FULL_TEXTURE;
		}
		if (armorPieces >= 2) {
			return ARMOR_HALF_TEXTURE;
		}
		return null;
	}

	private static ResourceLocation selectOxygenTexture(int oxygenPoints) {
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
			Path directory = StaticJsonSystem.getOrCreateGlobalSystemDirectory(HUD_CONFIG_FOLDER_NAME);
			Path configFile = resolveJsonFile(directory, HUD_CONFIG_FILE_NAME);
			JsonObject normalized = StaticJsonSystem.ensureManagedFile(configFile, defaults);
			Settings loaded = Settings.fromJson(normalized);
			StaticJsonSystem.writeManagedFile(configFile, loaded.toConfigJson(), defaults);
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
		serverSeason = season.trim();
		hasServerSeason = true;
	}

	public static void clearServerSeason() {
		serverSeason = "spring";
		hasServerSeason = false;
	}

	public static boolean hasServerSeason() {
		return hasServerSeason;
	}

	public static String getServerSeason() {
		return serverSeason;
	}

	public static void setServerHunger(int current, int pending, int max) {
		int normalizedCurrent = Math.max(0, current);
		int normalizedPending = Math.max(0, pending);
		if (normalizedCurrent < serverHungerCurrent) {
			// Immediate drops (e.g. hunger drained for health) should not be smoothed.
			smoothUpFromPendingIncrease = false;
			smoothDownFromPendingDecrease = false;
		} else if (normalizedPending > serverHungerPending) {
			smoothUpFromPendingIncrease = true;
			smoothDownFromPendingDecrease = false;
		} else if (normalizedPending < serverHungerPending) {
			smoothDownFromPendingDecrease = true;
		}
		serverHungerCurrent = normalizedCurrent;
		serverHungerPending = normalizedPending;
		serverHungerMax = Math.max(1, max);
		hasServerHunger = true;
	}

	public static void clearServerHunger() {
		serverHungerCurrent = 0;
		serverHungerPending = 0;
		serverHungerMax = VANILLA_MAX_FOOD_LEVEL;
		hasServerHunger = false;
		smoothedDisplayedHunger = -1L;
		nextHungerDisplayStepTick = Long.MIN_VALUE;
		smoothedDisplayMaxHunger = VANILLA_MAX_FOOD_LEVEL;
		lastHungerDisplayTarget = -1L;
		smoothUpFromPendingIncrease = false;
		smoothDownFromPendingDecrease = false;
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

	public static boolean isHealthHudEnabled() {
		return settings.healthHudEnabled;
	}

	public static boolean isHungerHudEnabled() {
		return settings.hungerHudEnabled;
	}

	public static boolean isArmorHudEnabled() {
		return settings.armorHudEnabled;
	}

	public static boolean isOxygenHudEnabled() {
		return settings.oxygenHudEnabled;
	}

	public static boolean isSeasonHudEnabled() {
		return settings.seasonHudEnabled;
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



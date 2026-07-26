package madoku.craft.hud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import madoku.craft.MadokuCraft;
import madoku.craft.api.season.SeasonBiomeClimateManager;
import madoku.craft.api.season.SeasonEnvironmentTransitionManager;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.pet.PetHolder;
import madoku.craft.pet.PetInventory;
import madoku.craft.pet.PetConfigManager;
import madoku.craft.pet.PetEntitiesManager;
import madoku.craft.pet.PetAbilitiesManager;
import madoku.craft.season.ClientSeasonalPrecipitationState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Locale;

public final class HudAPIManager {
	private static final Identifier MADOKU_ABILITY_HUD_ID = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_ability_hud");
	private static final Identifier ABILITY_SLOT_TEXTURE = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "textures/gui/interface/ability_slot.png");
	private static final RenderPipeline ABILITY_SLOT_PIPELINE = RenderPipelines.GUI_TEXTURED;
	private static final int ABILITY_SLOT_TEXTURE_SIZE = 16;
	private static final int WORLD_X = 4;
	private static final int WORLD_Y = 4;
	private static final float WORLD_HUD_SCALE = 0.8F;
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

	private HudAPIManager() {
	}

	public static void initialize() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_hud"), HudAPIManager::renderWorldHud);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, MADOKU_ABILITY_HUD_ID, HudAPIManager::renderAbilityHud);
	}

	public static void reset() {
	}

	static void renderWorldHud(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (!HudConfigManager.isEnabled()) return;
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (!MadokuHudManager.hasRenderablePlayer(client)) return;

		long day;
		int hour;
		int minute;
		if (HudPayloadManager.hasServerTime()) {
			day = HudPayloadManager.getServerDay();
			hour = HudPayloadManager.getServerHour();
			minute = HudPayloadManager.getServerMinute();
		} else {
			long dayTime = level.getOverworldClockTime();
			day = MadokuTimeManager.getDay(dayTime);
			int totalMinutes = MadokuTimeManager.getTotalMinutes(dayTime);
			hour = totalMinutes / 60;
			minute = totalMinutes % 60;
		}

		SeasonBiomeClimateManager.Climate climate = ClientSeasonalPrecipitationState.resolveClimate(level.getBiome(player.blockPosition()).value());
		int line = 0;
		if (HudConfigManager.isEnabled("day")) drawWorldLine(context, client, "Day", Long.toString(displayDay(day)), line++, COLOR);
		if (HudConfigManager.isEnabled("time")) drawWorldLine(context, client, "Time", hour + ":" + twoDigits(minute), line++, HudConfigManager.isColored("time") ? timeColor((hour * 60) + minute) : COLOR);
		if (HudConfigManager.isEnabled("season") && HudPayloadManager.hasServerSeason()) {
			String season = getSeasonDisplayText();
			drawWorldLine(context, client, "Season", season, line++, HudConfigManager.isColored("season") ? seasonColor(season, HudPayloadManager.getServerSeasonDay(), HudPayloadManager.getServerSeasonLengthDays()) : COLOR);
		}
		if (HudConfigManager.isEnabled("temperature")) drawWorldLine(context, client, "Temperature", formatClimate(climate.temperature()), line++, HudConfigManager.isColored("temperature") ? temperatureColor(climate.temperature()) : COLOR);
		if (HudConfigManager.isEnabled("humidity")) drawWorldLine(context, client, "Humidity", formatClimate(climate.humidity()), line++, HudConfigManager.isColored("humidity") ? humidityColor(climate.humidity()) : COLOR);
		if (HudConfigManager.isEnabled("biome")) drawWorldLine(context, client, "Biome", getBiomeDisplayName(player, level), line++, COLOR);
		if (HudConfigManager.isEnabled("difficulty")) drawWorldLine(context, client, "Difficulty", getDifficultyDisplayText(), line, HudConfigManager.isColored("difficulty") ? difficultyColor(HudPayloadManager.getServerDifficulty()) : COLOR);
	}

	private static void drawWorldLine(GuiGraphicsExtractor context, Minecraft client, String label, String value, int line, int valueColor) {
		context.pose().pushMatrix();
		context.pose().translate(WORLD_X, lineOffset(client, line));
		context.pose().scale(WORLD_HUD_SCALE, WORLD_HUD_SCALE);
		String prefix = label + ": ";
		context.text(client.font, prefix, 0, 0, COLOR, true);
		context.text(client.font, value, client.font.width(prefix), 0, valueColor, true);
		context.pose().popMatrix();
	}

	private static String formatClimate(double value) {
		return Integer.toString((int) Math.round(value));
	}

	private static int timeColor(int totalMinutes) {
		int minutes = Math.floorMod(totalMinutes, 24 * 60);
		if (minutes < 1 * 60) return 0xFF243B80;
		if (minutes < 5 * 60) return interpolateColor(0xFF243B80, 0xFFFFF2A6, (minutes - 1 * 60) / (4.0D * 60.0D));
		if (minutes < 7 * 60) return 0xFFFFF2A6;
		if (minutes < 11 * 60) return interpolateColor(0xFFFFF2A6, 0xFFFFB347, (minutes - 7 * 60) / (4.0D * 60.0D));
		if (minutes < 13 * 60) return 0xFFFFB347;
		if (minutes < 17 * 60) return interpolateColor(0xFFFFB347, 0xFF8EC8FF, (minutes - 13 * 60) / (4.0D * 60.0D));
		if (minutes < 19 * 60) return 0xFF8EC8FF;
	if (minutes < 23 * 60) return interpolateColor(0xFF8EC8FF, 0xFF243B80, (minutes - 19 * 60) / (4.0D * 60.0D));
		return 0xFF243B80;
	}

	private static int seasonColor(String season, int seasonDay, int seasonLengthDays) {
		String current = season == null ? "" : season.toLowerCase(Locale.ROOT);
		int currentColor = seasonPaletteColor(current);
		int nextColor = seasonPaletteColor(nextSeason(current));
		if (currentColor == COLOR || nextColor == COLOR) {
			return currentColor;
		}
		return interpolateColor(
			currentColor,
			nextColor,
			SeasonEnvironmentTransitionManager.resolveSeasonalTransitionProgress(seasonDay, seasonLengthDays));
	}

	private static int seasonPaletteColor(String season) {
		return switch (season) {
			case "spring" -> 0xFFFF9ECF;
			case "summer" -> 0xFFFFD34E;
			case "fall" -> 0xFFFF7043;
			case "winter" -> 0xFFB8E3FF;
			default -> COLOR;
		};
	}

	private static String nextSeason(String season) {
		return switch (season) {
			case "spring" -> "summer";
			case "summer" -> "fall";
			case "fall" -> "winter";
			case "winter" -> "spring";
			default -> "";
		};
	}

	private static int temperatureColor(double value) {
		return climateColor(value, 0xFF5AA9FF, 0xFF55C878, 0xFFFF5A5A);
	}

	private static int humidityColor(double value) {
		return climateColor(value, 0xFFFF5A5A, 0xFF55C878, 0xFF5AA9FF);
	}

	private static int climateColor(double value, int lowColor, int peakColor, int highColor) {
		double clamped = Math.max(0.0D, Math.min(100.0D, value));
		if (clamped <= 5.0D) return lowColor;
		if (clamped < 45.0D) return interpolateColor(lowColor, peakColor, (clamped - 5.0D) / 40.0D);
		if (clamped <= 55.0D) return peakColor;
		if (clamped < 95.0D) return interpolateColor(peakColor, highColor, (clamped - 55.0D) / 40.0D);
		return highColor;
	}

	private static int difficultyColor(int value) {
		if (value <= 5) return interpolateColor(0xFF55C878, 0xFFFFD34E, (value - 3.0D) / 2.0D);
		return interpolateColor(0xFFFFD34E, 0xFFFF5A5A, (value - 5.0D) / 3.0D);
	}

	private static int interpolateColor(int first, int second, double progress) {
		double clamped = Math.max(0.0D, Math.min(1.0D, progress));
		double smooth = clamped * clamped * (3.0D - (2.0D * clamped));
		int alpha = interpolateChannel((first >>> 24) & 0xFF, (second >>> 24) & 0xFF, smooth);
		int red = interpolateChannel((first >>> 16) & 0xFF, (second >>> 16) & 0xFF, smooth);
		int green = interpolateChannel((first >>> 8) & 0xFF, (second >>> 8) & 0xFF, smooth);
		int blue = interpolateChannel(first & 0xFF, second & 0xFF, smooth);
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	private static int interpolateChannel(int first, int second, double progress) {
		return (int) Math.round(first + ((second - first) * progress));
	}


	private static void renderAbilityHud(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (!HudConfigManager.isEnabled()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.gui.hud.isHidden() || player.isSpectator() || !PetConfigManager.isEnabled()) {
			return;
		}
		if (!(player instanceof PetHolder holder)) {
			return;
		}

		PetInventory inventory = holder.madokuCraft$getPetInventory();
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

	private static int lineOffset(Minecraft client, int lines) {
		int lineStep = Math.round((client.font.lineHeight + 4) * WORLD_HUD_SCALE);
		return WORLD_Y + (lineStep * lines);
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
		return Math.max(0L, rawDay);
	}

	private static String getDifficultyDisplayText() {
		if (!HudPayloadManager.hasServerDifficulty()) {
			return "1";
		}
		return Integer.toString(Math.max(1, HudPayloadManager.getServerDifficulty()));
	}

	private static String getSeasonDisplayText() {
		if (!HudPayloadManager.hasServerSeason()) {
			return "Unknown";
		}
		return capitalizeWord(HudPayloadManager.getServerSeason());
	}

	private static String capitalizeWord(String value) {
		if (value == null || value.isBlank()) {
			return "Unknown";
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
	}

	private static List<Integer> visibleAbilitySlots(PetInventory inventory) {
		List<Integer> visible = new java.util.ArrayList<>(PetEntitiesManager.SLOT_COUNT);
		for (int slot = 0; slot < Math.min(PetEntitiesManager.SLOT_COUNT, inventory.getContainerSize()); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack == null || stack.isEmpty() || !PetAbilitiesManager.hasAbility(stack)) {
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

		int totalCooldownTicks = PetAbilitiesManager.cooldownTicks(client.player, slot, stack);
		if (totalCooldownTicks <= 0 || slot < 0 || slot >= PetEntitiesManager.SLOT_COUNT) {
			return;
		}

		long remainingTicks = Math.max(0L, HudPayloadManager.getPetAbilityCooldownEndTick(slot) - client.level.getGameTime());
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
}

package madoku.craft.mixin.color;

import madoku.craft.java.color.ClientColorContext;
import madoku.craft.java.core.season.EnvironmentTransitionConfigAPIManager;
import madoku.craft.java.season.ClientSeasonalPrecipitationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeColors.class)
public final class BiomeTemperatureColorMixin {
	private static final double LIGHT_GREY_MAX_TEMPERATURE = 5.0D;
	private static final double PEAK_VIBRANCY_MIN_TEMPERATURE = 45.0D;
	private static final double PEAK_VIBRANCY_MAX_TEMPERATURE = 55.0D;
	private static final double DARK_GREY_MIN_TEMPERATURE = 95.0D;
	private static final double VIBRANCY_COLOR_INFLUENCE = 0.2D;
	private static final double LEAF_VIBRANCY_COLOR_INFLUENCE = 0.3D;
	private static final double SEASON_COLOR_MIN_INFLUENCE = 0.3D;
	private static final double SEASON_COLOR_MAX_INFLUENCE = 0.7D;
	private static final double LEAF_SEASON_COLOR_MIN_INFLUENCE = 0.5D;
	private static final double LEAF_SEASON_COLOR_MAX_INFLUENCE = 0.9D;
	private static final double BIOME_COLOR_INFLUENCE = 0.2D;
	private static final double LEAF_BIOME_COLOR_INFLUENCE = 0.3D;
	private static final double SODIUM_LEAF_EDGE_ORIGINAL_COLOR_INFLUENCE = 0.3D;
	private static final double SODIUM_NON_LEAF_EDGE_ORIGINAL_COLOR_INFLUENCE = 0.7D;
	private static final int WHITE_COLOR = 0xFFFFFFFF;
	private static final int LIGHT_GREY_COLOR = 0xFFD8D8D8;
	private static final int DARK_GREY_COLOR = 0xFF4A4A4A;
	@Inject(method = "getAverageGrassColor", at = @At("RETURN"), cancellable = true)
	private static void madokuCraft$transitionGrassColor(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		applyTemperatureColor(level, pos, cir);
	}

	@Inject(method = "getAverageFoliageColor", at = @At("RETURN"), cancellable = true)
	private static void madokuCraft$transitionFoliageColor(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		applyTemperatureColor(level, pos, cir);
	}

	private static void applyTemperatureColor(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (level == null || pos == null || !EnvironmentTransitionConfigAPIManager.getSettings().transitionColorEnabled()) {
			return;
		}

		Block block = level.getBlockState(pos).getBlock();
		Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
		Boolean forcedLeaves = ClientColorContext.forcedLeaves();
		boolean sampledTarget = isTransitionTarget(blockId);
		if (!ClientSeasonalPrecipitationState.isSynchronized()
			|| (forcedLeaves == null && !sampledTarget)) {
			return;
		}
		boolean leaves = forcedLeaves != null ? forcedLeaves : blockId.getPath().endsWith("_leaves");
		Minecraft client = Minecraft.getInstance();
		Biome biome = client.level == null ? null : client.level.getBiome(pos).value();
		int resolvedColor = temperatureColor(
			cir.getReturnValue(),
			ClientSeasonalPrecipitationState.resolveSeasonalTemperature(biome),
			leaves);
		if (forcedLeaves != null && !sampledTarget) {
			resolvedColor = blendColors(
				resolvedColor,
				cir.getReturnValue(),
				leaves ? SODIUM_LEAF_EDGE_ORIGINAL_COLOR_INFLUENCE : SODIUM_NON_LEAF_EDGE_ORIGINAL_COLOR_INFLUENCE);
		}
		cir.setReturnValue(resolvedColor);
	}

	private static boolean isTransitionTarget(Identifier blockId) {
		if (blockId == null || !"minecraft".equals(blockId.getNamespace())) {
			return false;
		}

		String path = blockId.getPath();
		if (path.endsWith("_leaves")) {
			return !"cherry_leaves".equals(path)
				&& !"pale_oak_leaves".equals(path)
				&& !"azalea_leaves".equals(path)
				&& !"flowering_azalea_leaves".equals(path);
		}

		return switch (path) {
			case "grass_block", "fern", "large_fern", "tall_grass", "short_grass", "bush", "vine", "lily_pad" -> true;
			default -> false;
		};
	}

	private static int temperatureColor(int biomeColor, double temperature, boolean leaves) {
		double value = Math.max(0.0D, Math.min(100.0D, temperature));
		int vibrancyColor = baseTemperatureColor(biomeColor, value);
		int seasonalColor = seasonalTemperatureColor(
			ClientSeasonalPrecipitationState.getSeason(),
			value,
			ClientSeasonalPrecipitationState.getSeasonDayValue(),
			ClientSeasonalPrecipitationState.getSeasonLengthDays(),
			leaves);
		return blendColorLayers(
			biomeColor,
			vibrancyColor,
			seasonalColor,
			seasonalColorInfluence(ClientSeasonalPrecipitationState.getSeason(), value, leaves),
			leaves);
	}

	private static int baseTemperatureColor(int biomeColor, double temperature) {
		if (temperature <= LIGHT_GREY_MAX_TEMPERATURE) {
			return LIGHT_GREY_COLOR;
		}
		if (temperature < PEAK_VIBRANCY_MIN_TEMPERATURE) {
			double progress = smoothTemperatureProgress(
				(temperature - LIGHT_GREY_MAX_TEMPERATURE)
					/ (PEAK_VIBRANCY_MIN_TEMPERATURE - LIGHT_GREY_MAX_TEMPERATURE));
			int desaturatedColor = adjustSaturation(biomeColor, progress);
			return blendColors(
				LIGHT_GREY_COLOR,
				desaturatedColor,
				progress);
		}
		if (temperature <= PEAK_VIBRANCY_MAX_TEMPERATURE) {
			return biomeColor;
		}
		if (temperature < DARK_GREY_MIN_TEMPERATURE) {
			double progress = smoothTemperatureProgress(
				(temperature - PEAK_VIBRANCY_MAX_TEMPERATURE)
					/ (DARK_GREY_MIN_TEMPERATURE - PEAK_VIBRANCY_MAX_TEMPERATURE));
			int desaturatedColor = adjustSaturation(biomeColor, 1.0D - progress);
			return blendColors(
				desaturatedColor,
				DARK_GREY_COLOR,
				progress);
		}
		return DARK_GREY_COLOR;
	}

	private static double smoothTemperatureProgress(double progress) {
		double clamped = Math.max(0.0D, Math.min(1.0D, progress));
		return clamped * clamped * (3.0D - (2.0D * clamped));
	}

	private static int adjustSaturation(int color, double saturationInfluence) {
		double red = ((color >>> 16) & 0xFF) / 255.0D;
		double green = ((color >>> 8) & 0xFF) / 255.0D;
		double blue = (color & 0xFF) / 255.0D;
		double maximum = Math.max(red, Math.max(green, blue));
		double minimum = Math.min(red, Math.min(green, blue));
		double delta = maximum - minimum;
		double saturation = maximum <= 0.0D ? 0.0D : delta / maximum;
		double hue = 0.0D;
		if (delta > 0.0D) {
			if (maximum == red) {
				hue = ((green - blue) / delta) % 6.0D;
			} else if (maximum == green) {
				hue = ((blue - red) / delta) + 2.0D;
			} else {
				hue = ((red - green) / delta) + 4.0D;
			}
			hue /= 6.0D;
			if (hue < 0.0D) hue += 1.0D;
		}
		double scaledSaturation = saturation * Math.max(0.0D, Math.min(1.0D, saturationInfluence));
		double channel = maximum * (1.0D - scaledSaturation);
		double chroma = maximum * scaledSaturation;
		double hueChannel = chroma * (1.0D - Math.abs((hue * 6.0D % 2.0D) - 1.0D));
		double outputRed;
		double outputGreen;
		double outputBlue;
		switch ((int) Math.floor(hue * 6.0D) % 6) {
			case 0 -> { outputRed = chroma; outputGreen = hueChannel; outputBlue = 0.0D; }
			case 1 -> { outputRed = hueChannel; outputGreen = chroma; outputBlue = 0.0D; }
			case 2 -> { outputRed = 0.0D; outputGreen = chroma; outputBlue = hueChannel; }
			case 3 -> { outputRed = 0.0D; outputGreen = hueChannel; outputBlue = chroma; }
			case 4 -> { outputRed = hueChannel; outputGreen = 0.0D; outputBlue = chroma; }
			default -> { outputRed = chroma; outputGreen = 0.0D; outputBlue = hueChannel; }
		}
		int redChannel = interpolateChannel(0, 255, outputRed + channel);
		int greenChannel = interpolateChannel(0, 255, outputGreen + channel);
		int blueChannel = interpolateChannel(0, 255, outputBlue + channel);
		return 0xFF000000 | (redChannel << 16) | (greenChannel << 8) | blueChannel;
	}

	private static int seasonalTemperatureColor(
		String season,
		double temperature,
		double seasonDay,
		int seasonLengthDays,
		boolean leaves
	) {
		int currentColor = seasonTemperatureTargetColor(season, temperature, leaves);
		int safeLength = Math.max(1, seasonLengthDays);
		double safeDay = Math.max(0.0D, Math.min(safeLength - 1, seasonDay));
		int peakStartDay = Math.max(0, (safeLength / 2) - 1);
		int peakEndDay = Math.min(safeLength - 1, safeLength / 2);
		double peakSpan = Math.max(1.0D, safeLength - 1.0D);
		if (safeDay < peakStartDay) {
			int previousColor = seasonTemperatureTargetColor(previousSeason(season), temperature, leaves);
			return interpolateColor(previousColor, currentColor, (safeDay + (safeLength - peakEndDay)) / peakSpan);
		}
		if (safeDay > peakEndDay) {
			int nextColor = seasonTemperatureTargetColor(nextSeason(season), temperature, leaves);
			return interpolateColor(currentColor, nextColor, (safeDay - peakEndDay) / peakSpan);
		}
		return currentColor;
	}

	private static int seasonTemperatureTargetColor(String season, double temperature, boolean leaves) {
		double normalizedTemperature = temperature / 100.0D;
		double lowTemperatureTint = lowTemperatureTint(normalizedTemperature);
		double highTemperatureTint = 1.0D - lowTemperatureTint;
		return switch (season == null ? "" : season.toLowerCase(java.util.Locale.ROOT)) {
			case "spring" -> leaves ? 0xFF0F6B35 : 0xFF168A3B;
			case "summer" -> interpolateColor(0xFF55A64A, leaves ? 0xFF7D9E25 : 0xFFD0C63A, highTemperatureTint);
			case "fall" -> leaves
				? interpolateColor(0xFFB51C2C, 0xFFE07A2F, highTemperatureTint)
				: interpolateColor(0xFFE07A2F, 0xFFF2A23A, highTemperatureTint);
			case "winter" -> WHITE_COLOR;
			default -> 0xFF808080;
		};
	}

	private static double seasonalColorInfluence(String season, double temperature, boolean leaves) {
		double normalizedTemperature = temperature / 100.0D;
		double lowTemperatureTint = lowTemperatureTint(normalizedTemperature);
		double strength = switch (season == null ? "" : season.toLowerCase(java.util.Locale.ROOT)) {
			case "spring", "summer" -> 1.0D - lowTemperatureTint;
			case "fall", "winter" -> lowTemperatureTint;
			default -> -1.0D;
		};
		if (strength < 0.0D) return 0.0D;
		double minimum = leaves ? LEAF_SEASON_COLOR_MIN_INFLUENCE : SEASON_COLOR_MIN_INFLUENCE;
		double maximum = leaves ? LEAF_SEASON_COLOR_MAX_INFLUENCE : SEASON_COLOR_MAX_INFLUENCE;
		return minimum + ((maximum - minimum) * strength);
	}

	private static int blendColorLayers(
		int biomeColor,
		int vibrancyColor,
		int seasonalColor,
		double seasonalInfluence,
		boolean leaves
	) {
		double biomeInfluence = leaves ? LEAF_BIOME_COLOR_INFLUENCE : BIOME_COLOR_INFLUENCE;
		double vibrancyInfluence = leaves ? LEAF_VIBRANCY_COLOR_INFLUENCE : VIBRANCY_COLOR_INFLUENCE;
		double totalInfluence = biomeInfluence + vibrancyInfluence + seasonalInfluence;
		int red = weightedChannel(biomeColor, vibrancyColor, seasonalColor, biomeInfluence, vibrancyInfluence, seasonalInfluence, totalInfluence, 16);
		int green = weightedChannel(biomeColor, vibrancyColor, seasonalColor, biomeInfluence, vibrancyInfluence, seasonalInfluence, totalInfluence, 8);
		int blue = weightedChannel(biomeColor, vibrancyColor, seasonalColor, biomeInfluence, vibrancyInfluence, seasonalInfluence, totalInfluence, 0);
		return 0xFF000000 | (red << 16) | (green << 8) | blue;
	}

	private static int weightedChannel(
		int first,
		int second,
		int third,
		double firstInfluence,
		double secondInfluence,
		double thirdInfluence,
		double totalInfluence,
		int shift
	) {
		double value = (((first >>> shift) & 0xFF) * firstInfluence)
			+ (((second >>> shift) & 0xFF) * secondInfluence)
			+ (((third >>> shift) & 0xFF) * thirdInfluence);
		return (int) Math.round(value / totalInfluence);
	}

	private static double lowTemperatureTint(double normalizedTemperature) {
		if (normalizedTemperature <= 0.10D) return 1.0D;
		if (normalizedTemperature >= 0.90D) return 0.0D;
		double progress = (normalizedTemperature - 0.10D) / 0.80D;
		double smooth = progress * progress * (3.0D - (2.0D * progress));
		return 1.0D - smooth;
	}

	private static String nextSeason(String season) {
		return switch (season == null ? "" : season.toLowerCase(java.util.Locale.ROOT)) {
			case "spring" -> "summer";
			case "summer" -> "fall";
			case "fall" -> "winter";
			case "winter" -> "spring";
			default -> "";
		};
	}

	private static String previousSeason(String season) {
		return switch (season == null ? "" : season.toLowerCase(java.util.Locale.ROOT)) {
			case "spring" -> "winter";
			case "summer" -> "spring";
			case "fall" -> "summer";
			case "winter" -> "fall";
			default -> "";
		};
	}

	private static int interpolateColor(int first, int second, double progress) {
		double clamped = Math.max(0.0D, Math.min(1.0D, progress));
		double smooth = clamped * clamped * (3.0D - (2.0D * clamped));
		int red = interpolateChannel((first >>> 16) & 0xFF, (second >>> 16) & 0xFF, smooth);
		int green = interpolateChannel((first >>> 8) & 0xFF, (second >>> 8) & 0xFF, smooth);
		int blue = interpolateChannel(first & 0xFF, second & 0xFF, smooth);
		return 0xFF000000 | (red << 16) | (green << 8) | blue;
	}

	private static int interpolateChannel(int first, int second, double progress) {
		return (int) Math.round(first + ((second - first) * progress));
	}

	private static int blendColors(int base, int overlay, double influence) {
		double clamped = Math.max(0.0D, Math.min(1.0D, influence));
		int alpha = interpolateChannel((base >>> 24) & 0xFF, (overlay >>> 24) & 0xFF, clamped);
		int red = interpolateChannel((base >>> 16) & 0xFF, (overlay >>> 16) & 0xFF, clamped);
		int green = interpolateChannel((base >>> 8) & 0xFF, (overlay >>> 8) & 0xFF, clamped);
		int blue = interpolateChannel(base & 0xFF, overlay & 0xFF, clamped);
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}
}


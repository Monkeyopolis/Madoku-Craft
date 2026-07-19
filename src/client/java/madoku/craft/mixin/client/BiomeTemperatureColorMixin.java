package madoku.craft.mixin.client;

import madoku.craft.api.season.EnvironmentTransitionConfigManager;
import madoku.craft.season.ClientSeasonalPrecipitationState;
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
	private static final double LEAF_COLOR_INFLUENCE = 0.75D;
	private static final double TEMPERATURE_BASE_INFLUENCE = 0.5D;
	private static final double SEASON_TINT_INFLUENCE = 0.6D;
	private static final int WHITE_COLOR = 0xFFFFFFFF;
	private static final int COLD_BASE_TINT = 0xFFA6B5C2;
	private static final int DRY_BASE_TINT = 0xFFC8B477;
	@Inject(method = "getAverageGrassColor", at = @At("RETURN"), cancellable = true)
	private static void madokuCraft$transitionGrassColor(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		applyTemperatureColor(level, pos, cir);
	}

	@Inject(method = "getAverageFoliageColor", at = @At("RETURN"), cancellable = true)
	private static void madokuCraft$transitionFoliageColor(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		applyTemperatureColor(level, pos, cir);
	}

	private static void applyTemperatureColor(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (level == null || pos == null || !EnvironmentTransitionConfigManager.getSettings().transitionColorEnabled()) {
			return;
		}

		Block block = level.getBlockState(pos).getBlock();
		Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
		if (!isTransitionTarget(blockId) || !ClientSeasonalPrecipitationState.isSynchronized()) {
			return;
		}
		boolean leaves = blockId.getPath().endsWith("_leaves");
		Minecraft client = Minecraft.getInstance();
		Biome biome = client.level == null ? null : client.level.getBiome(pos).value();
		int resolvedColor = temperatureColor(
			cir.getReturnValue(),
			ClientSeasonalPrecipitationState.resolveSeasonalTemperature(biome),
			leaves);
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
		int baseColor = baseTemperatureColor(biomeColor, value);
		return seasonTemperatureColor(baseColor, ClientSeasonalPrecipitationState.getSeason(), value, leaves);
	}

	private static int baseTemperatureColor(int biomeColor, double temperature) {
		int coldColor = blendColors(biomeColor, COLD_BASE_TINT, TEMPERATURE_BASE_INFLUENCE);
		int vibrantColor = biomeColor;
		int dryColor = blendColors(biomeColor, DRY_BASE_TINT, TEMPERATURE_BASE_INFLUENCE);
		if (temperature <= 30.0D) {
			return coldColor;
		}
		if (temperature <= 69.0D) {
			return interpolateColor(coldColor, vibrantColor, (temperature - 30.0D) / 39.0D);
		}
		return interpolateColor(vibrantColor, dryColor, (temperature - 69.0D) / 31.0D);
	}

	private static int seasonTemperatureColor(int baseColor, String season, double temperature, boolean leaves) {
		double normalizedTemperature = temperature / 100.0D;
		double tintInfluence = leaves ? LEAF_COLOR_INFLUENCE : SEASON_TINT_INFLUENCE;
		return switch (season == null ? "" : season.toLowerCase(java.util.Locale.ROOT)) {
			case "spring" -> blendColors(
				baseColor,
				leaves ? 0xFF0F6B35 : 0xFF168A3B,
				(1.0D - normalizedTemperature) * tintInfluence);
			case "summer" -> blendColors(
				baseColor,
				interpolateColor(0xFF55A64A, leaves ? 0xFF7D9E25 : 0xFFD0C63A, normalizedTemperature),
				normalizedTemperature * tintInfluence);
			case "fall" -> blendColors(
				baseColor,
				interpolateColor(leaves ? 0xFFB51C2C : 0xFFC42A2A, 0xFFE07A2F, normalizedTemperature),
				tintInfluence);
			case "winter" -> blendColors(baseColor, WHITE_COLOR, (1.0D - normalizedTemperature) * tintInfluence);
			default -> baseColor;
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

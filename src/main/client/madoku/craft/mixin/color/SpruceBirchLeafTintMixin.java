package madoku.craft.mixin.color;

import madoku.craft.java.color.ClientColorContext;
import madoku.craft.java.core.season.EnvironmentTransitionConfigAPIManager;
import madoku.craft.java.season.ClientSeasonalPrecipitationState;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(BlockColors.class)
public final class SpruceBirchLeafTintMixin {
	private static final int SPRUCE_LEAF_DEFAULT_COLOR = 0xFF619961;
	private static final int BIRCH_LEAF_DEFAULT_COLOR = 0xFF80A755;
	private static final BlockTintSource FOLIAGE_TINT = BlockTintSources.foliage();
	private static final BlockTintSource SPRUCE_LEAF_TINT = dynamicLeafTint(SPRUCE_LEAF_DEFAULT_COLOR);
	private static final BlockTintSource BIRCH_LEAF_TINT = dynamicLeafTint(BIRCH_LEAF_DEFAULT_COLOR);

	@Redirect(
		method = "createDefault",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/color/block/BlockColors;register(Ljava/util/List;[Lnet/minecraft/world/level/block/Block;)V"
		)
	)
	private static void madokuCraft$registerDynamicLeafTint(
		BlockColors colors,
		List<BlockTintSource> layers,
		Block[] blocks
	) {
		if (blocks.length == 1 && blocks[0] == Blocks.SPRUCE_LEAVES) {
			colors.register(List.of(SPRUCE_LEAF_TINT), blocks);
			return;
		}
		if (blocks.length == 1 && blocks[0] == Blocks.BIRCH_LEAVES) {
			colors.register(List.of(BIRCH_LEAF_TINT), blocks);
			return;
		}
		colors.register(layers, blocks);
	}

	@Inject(method = "getTintSources", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$useSeasonalLeafTint(BlockState state, CallbackInfoReturnable<List<BlockTintSource>> cir) {
		if (state == null
			|| !EnvironmentTransitionConfigAPIManager.getSettings().transitionColorEnabled()
			|| !ClientSeasonalPrecipitationState.isSynchronized()) {
			return;
		}

		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if ("spruce_leaves".equals(path)
			|| "birch_leaves".equals(path)
			|| "vine".equals(path)
			|| "lily_pad".equals(path)) {
			cir.setReturnValue(List.of(FOLIAGE_TINT));
		}
	}

	private static BlockTintSource dynamicLeafTint(int defaultColor) {
		return new BlockTintSource() {
			@Override
			public int color(BlockState state) {
				return defaultColor;
			}

			@Override
			public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
				if (!isSeasonalTintEnabled()) {
					return defaultColor;
				}
				ClientColorContext.force(true);
				try {
					return FOLIAGE_TINT.colorInWorld(state, level, pos);
				} finally {
					ClientColorContext.clear();
				}
			}
		};
	}

	private static boolean isSeasonalTintEnabled() {
		return EnvironmentTransitionConfigAPIManager.getSettings().transitionColorEnabled()
			&& ClientSeasonalPrecipitationState.isSynchronized();
	}
}


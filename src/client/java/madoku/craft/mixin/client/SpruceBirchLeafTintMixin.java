package madoku.craft.mixin.client;

import madoku.craft.api.season.EnvironmentTransitionConfigManager;
import madoku.craft.season.ClientSeasonalPrecipitationState;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(BlockColors.class)
public final class SpruceBirchLeafTintMixin {
	@Inject(method = "getTintSources", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$useSeasonalLeafTint(BlockState state, CallbackInfoReturnable<List<BlockTintSource>> cir) {
		if (state == null
			|| !EnvironmentTransitionConfigManager.getSettings().transitionColorEnabled()
			|| !ClientSeasonalPrecipitationState.isSynchronized()) {
			return;
		}

		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if ("spruce_leaves".equals(path)
			|| "birch_leaves".equals(path)
			|| "vine".equals(path)
			|| "lily_pad".equals(path)) {
			cir.setReturnValue(List.of(BlockTintSources.foliage()));
		}
	}
}

package madoku.craft.mixin.core;

import madoku.craft.java.ecosystem.EcosystemBlockStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelEcosystemBlockChangeMixin {
	@Inject(
		method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
		at = @At("HEAD")
	)
	private void madokuCraft$onServerBlockChanged(
		BlockPos position,
		BlockState state,
		int flags,
		int recursionLeft,
		CallbackInfoReturnable<Boolean> cir
	) {
		if ((Object) this instanceof ServerLevel serverLevel) {
			BlockState oldState = serverLevel.getBlockState(position);
			if (oldState != state
				&& (EcosystemBlockStateManager.hasProperties(oldState)
					|| EcosystemBlockStateManager.hasProperties(state))) {
				EcosystemBlockStateManager.onBlockChanged(serverLevel, position, oldState, state);
			}
		}
	}
}

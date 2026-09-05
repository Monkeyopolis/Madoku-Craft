package madoku.craft.mixin.core;

import madoku.craft.java.ecosystem.EcosystemBlockStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateEcosystemRandomTickMixin {
	@Inject(method = "randomTick", at = @At("HEAD"))
	private void madokuCraft$runEcosystemRandomTick(
		ServerLevel level,
		BlockPos position,
		RandomSource random,
		CallbackInfo ci
	) {
		EcosystemBlockStateManager.onRandomTick(level, (BlockState) (Object) this, position, random);
	}
}

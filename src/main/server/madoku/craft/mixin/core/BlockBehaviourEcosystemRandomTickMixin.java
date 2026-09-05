package madoku.craft.mixin.core;

import madoku.craft.java.ecosystem.EcosystemBlockStateManager;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourEcosystemRandomTickMixin {
	@Inject(method = "isRandomlyTicking", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$includeEcosystemEligibility(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (EcosystemBlockStateManager.shouldRandomTick(state)) {
			cir.setReturnValue(true);
		}
	}
}

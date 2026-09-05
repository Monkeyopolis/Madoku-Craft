package madoku.craft.mixin.core;

import java.util.function.Function;

import madoku.craft.java.ecosystem.EcosystemBlockPropertiesAccess;
import madoku.craft.java.ecosystem.EcosystemBlockStateManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StateDefinition.Builder.class)
public abstract class StateDefinitionBuilderEcosystemMixin<O, S extends StateHolder<O, S>> {
	@Shadow @Final private O owner;

	@Shadow
	public abstract StateDefinition.Builder<O, S> add(net.minecraft.world.level.block.state.properties.Property<?>... properties);

	@Inject(method = "create", at = @At("HEAD"))
	private void madokuCraft$addEcosystemProperties(
		Function<O, S> factory,
		StateDefinition.Factory<O, S> stateFactory,
		CallbackInfoReturnable<StateDefinition<O, S>> cir
	) {
		if (!(owner instanceof Block block)) {
			return;
		}
		BlockBehaviour.Properties properties = block.properties();
		if (properties instanceof EcosystemBlockPropertiesAccess access
			&& access.madokuCraft$hasEcosystemStateProperties()) {
			add(EcosystemBlockStateManager.MADOKU_CHECKED, EcosystemBlockStateManager.MADOKU_ELIGIBLE);
		}
	}
}

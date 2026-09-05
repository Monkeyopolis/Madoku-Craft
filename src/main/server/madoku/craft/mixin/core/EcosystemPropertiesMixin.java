package madoku.craft.mixin.core;

import madoku.craft.java.ecosystem.EcosystemBlockPropertiesAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockBehaviour.Properties.class)
public abstract class EcosystemPropertiesMixin implements EcosystemBlockPropertiesAccess {
	@Unique
	private boolean madokuCraft$ecosystemStateProperties;

	@Override
	public void madokuCraft$setEcosystemStateProperties(boolean enabled) {
		madokuCraft$ecosystemStateProperties = enabled;
	}

	@Override
	public boolean madokuCraft$hasEcosystemStateProperties() {
		return madokuCraft$ecosystemStateProperties;
	}
}

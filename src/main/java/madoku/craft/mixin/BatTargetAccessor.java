package madoku.craft.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ambient.Bat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Bat.class)
public interface BatTargetAccessor {
	@Accessor("targetPosition")
	void madokuCraft$setTargetPosition(BlockPos targetPosition);
}

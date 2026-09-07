package madoku.craft.mixin.inventory;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Slot.class)
public interface SlotAccessor {
	@Mutable
	@Accessor("x")
	void madokuCraft$setX(int x);

	@Mutable
	@Accessor("y")
	void madokuCraft$setY(int y);

	@Mutable
	@Accessor("index")
	void madokuCraft$setIndex(int index);
}

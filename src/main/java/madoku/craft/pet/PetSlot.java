package madoku.craft.pet;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class PetSlot extends Slot {
	public PetSlot(Container container, int slot, int x, int y) {
		super(container, slot, x, y);
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return PetEntitiesManager.isValid(stack);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}
}


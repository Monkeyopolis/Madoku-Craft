package madoku.craft.pet;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class PlayerEntitiesSlot extends Slot {
	public PlayerEntitiesSlot(Container container, int slot, int x, int y) {
		super(container, slot, x, y);
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return PlayerEntitiesSystem.isValidPlayerEntity(stack);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}
}


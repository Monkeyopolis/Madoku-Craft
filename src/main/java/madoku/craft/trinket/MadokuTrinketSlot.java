package madoku.craft.trinket;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class MadokuTrinketSlot extends Slot {
	public MadokuTrinketSlot(Container container, int slot, int x, int y) {
		super(container, slot, x, y);
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return MadokuTrinkets.isValidTrinket(stack);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}
}

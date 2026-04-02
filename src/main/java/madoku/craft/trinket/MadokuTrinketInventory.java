package madoku.craft.trinket;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

public final class MadokuTrinketInventory extends SimpleContainer {
	public MadokuTrinketInventory() {
		super(MadokuTrinkets.SLOT_COUNT);
	}

	public void copyFrom(MadokuTrinketInventory other) {
		for (int slot = 0; slot < getContainerSize(); slot++) {
			setItem(slot, other == null ? ItemStack.EMPTY : other.getItem(slot).copy());
		}
		setChanged();
	}
}

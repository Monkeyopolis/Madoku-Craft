package madoku.craft.pet;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

public final class PlayerEntitiesInventory extends SimpleContainer {
	private Runnable changeListener = () -> {};

	public PlayerEntitiesInventory() {
		super(PlayerEntitiesSystem.SLOT_COUNT);
	}

	public void setChangeListener(Runnable changeListener) {
		this.changeListener = changeListener == null ? () -> {} : changeListener;
	}

	@Override
	public void setChanged() {
		super.setChanged();
		changeListener.run();
	}

	public void copyFrom(PlayerEntitiesInventory other) {
		for (int slot = 0; slot < getContainerSize(); slot++) {
			setItem(slot, other == null ? ItemStack.EMPTY : other.getItem(slot).copy());
		}
		setChanged();
	}
}

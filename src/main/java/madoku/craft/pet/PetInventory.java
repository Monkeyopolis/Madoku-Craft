package madoku.craft.pet;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

public final class PetInventory extends SimpleContainer {
	private Runnable changeListener = () -> {};
	private int suppressedChangeDepth;

	public PetInventory() {
		super(PetEntitiesManager.SLOT_COUNT);
	}

	public void setChangeListener(Runnable changeListener) {
		this.changeListener = changeListener == null ? () -> {} : changeListener;
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (suppressedChangeDepth <= 0) {
			changeListener.run();
		}
	}

	public void runBulkUpdate(Runnable action) {
		suppressedChangeDepth++;
		try {
			if (action != null) {
				action.run();
			}
		} finally {
			suppressedChangeDepth = Math.max(0, suppressedChangeDepth - 1);
		}
		setChanged();
	}

	public void copyFrom(PetInventory other) {
		runBulkUpdate(() -> {
			for (int slot = 0; slot < getContainerSize(); slot++) {
				setItem(slot, other == null ? ItemStack.EMPTY : other.getItem(slot).copy());
			}
		});
	}
}


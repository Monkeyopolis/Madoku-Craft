package madoku.craft.java.pet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Public contract and shared component types for managed pet inventories. */
public final class PetComponentsAPIManager {
	private static final PetComponentsProvider UNAVAILABLE_PROVIDER = new PetComponentsProvider() { };
	private static volatile PetComponentsProvider provider = UNAVAILABLE_PROVIDER;

	private PetComponentsAPIManager() { }
	public static void registerProvider(PetComponentsProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Pet components provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static boolean isManaged(Entity entity) { return provider.isManaged(entity); }
	public static boolean isMob(Entity entity) { return provider.isMob(entity); }
	public static void dropAll(ServerPlayer player) { provider.dropAll(player); }
	public static int countPets(Player player) { return provider.countPets(player); }

	public interface PetHolder {
		PetInventory madokuCraft$getPetInventory();
	}

	public static final class PetInventory extends SimpleContainer {
		private Runnable changeListener = () -> { };
		private int suppressedChangeDepth;

		public PetInventory() { super(PetEntitiesAPIManager.SLOT_COUNT); }
		public void setChangeListener(Runnable changeListener) { this.changeListener = changeListener == null ? () -> { } : changeListener; }
		@Override public void setChanged() {
			super.setChanged();
			if (suppressedChangeDepth <= 0) changeListener.run();
		}
		public void runBulkUpdate(Runnable action) {
			suppressedChangeDepth++;
			try {
				if (action != null) action.run();
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

	public static final class PetSlot extends Slot {
		public PetSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }
		@Override public boolean mayPlace(ItemStack stack) { return PetEntitiesAPIManager.isValid(stack); }
		@Override public int getMaxStackSize() { return 1; }
	}
}

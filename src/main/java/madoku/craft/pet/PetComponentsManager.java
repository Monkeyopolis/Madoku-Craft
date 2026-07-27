package madoku.craft.pet;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Mob;
import madoku.craft.pet.PetConfigManager.PetRule;

/** Owns the managed-pet component boundary: identity, sound state, and entity checks. */
public final class PetComponentsManager {
	private PetComponentsManager() {
	}

	public static void initialize() {
	}

	public static boolean isManaged(Entity entity) {
		return MadokuPetManager.isManagedPet(entity) || (entity != null && PetPayloadManager.hasSoundState(entity.getUUID()));
	}

	public static float soundVolume(Entity entity, float baseVolume) {
		PetRule rule = PetConfigManager.resolvePetRule(entity);
		return Math.max(0.0F, baseVolume * (rule == null ? 1.0F : rule.soundVolumeMultiplier));
	}

	public static int ambientSoundInterval(Entity entity, int baseInterval) {
		PetRule rule = PetConfigManager.resolvePetRule(entity);
		return Math.max(20, baseInterval * Math.max(1, rule == null ? 1 : rule.ambientSoundIntervalMultiplier));
	}

	public static void clear() {
		PetPayloadManager.clearSoundState();
	}

	public static boolean isMob(Entity entity) {
		return entity instanceof Mob && isManaged(entity);
	}

	public static final class PetInventory extends SimpleContainer {
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

	public interface PetHolder {
		PetInventory madokuCraft$getPetInventory();
	}

	public static final class PetSlot extends Slot {
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
}

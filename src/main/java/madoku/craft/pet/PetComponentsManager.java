package madoku.craft.pet;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owns the managed-pet component boundary and entity checks. */
public final class PetComponentsManager {
	private static final Map<UUID, ItemStack[]> PENDING_RESPAWN_PET_INVENTORIES = new ConcurrentHashMap<>();
	private PetComponentsManager() {
	}

	public static void initialize() {
	}

	static void reset() {
		PENDING_RESPAWN_PET_INVENTORIES.clear();
	}

	public static boolean isManaged(Entity entity) {
		return PetEntitiesManager.isManaged(entity);
	}

	public static boolean isMob(Entity entity) {
		return entity instanceof Mob && isManaged(entity);
	}

	static PetInventory petInventory(Player player) {
		return player instanceof PetHolder holder ? holder.madokuCraft$getPetInventory() : null;
	}

	public static void dropAll(ServerPlayer player) {
		PetInventory inventory = petInventory(player);
		if (player == null || inventory == null) {
			return;
		}
		List<Integer> occupiedSlots = new ArrayList<>(inventory.getContainerSize());
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (!inventory.getItem(slot).isEmpty()) {
				occupiedSlots.add(slot);
			}
		}
		if (occupiedSlots.isEmpty()) {
			return;
		}
		for (int i = occupiedSlots.size() - 1; i > 0; i--) {
			int j = player.getRandom().nextInt(i + 1);
			int temp = occupiedSlots.get(i);
			occupiedSlots.set(i, occupiedSlots.get(j));
			occupiedSlots.set(j, temp);
		}
		int dropCount = occupiedSlots.size();
		for (int index = 0; index < dropCount; index++) {
			int slot = occupiedSlots.get(index);
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				player.drop(stack, true, false);
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
		inventory.setChanged();
	}

	public static int countPets(Player player) {
		PetInventory inventory = petInventory(player);
		if (inventory == null) {
			return 0;
		}
		int count = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (PetConfigManager.isValidPet(inventory.getItem(slot))) {
				count++;
			}
		}
		return count;
	}

	static void clearPendingRespawnPetInventory(UUID playerId) {
		if (playerId != null) {
			PENDING_RESPAWN_PET_INVENTORIES.remove(playerId);
		}
	}

	static void copyToNewPlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
		PetInventory oldInventory = petInventory(oldPlayer);
		PetInventory newInventory = petInventory(newPlayer);
		if (oldInventory == null || newInventory == null) return;
		newInventory.copyFrom(oldInventory);
		PetHudManager.markAbilityHudDirty(newPlayer.getUUID());
	}

	static void cachePendingRespawnPetInventory(ServerPlayer player) {
		if (player == null) return;
		PetInventory inventory = petInventory(player);
		if (inventory == null) {
			clearPendingRespawnPetInventory(player.getUUID());
			return;
		}
		ItemStack[] snapshot = new ItemStack[PetEntitiesManager.SLOT_COUNT];
		for (int slot = 0; slot < PetEntitiesManager.SLOT_COUNT; slot++) {
			ItemStack stack = slot < inventory.getContainerSize() ? inventory.getItem(slot) : ItemStack.EMPTY;
			snapshot[slot] = stack == null ? ItemStack.EMPTY : stack.copy();
		}
		PENDING_RESPAWN_PET_INVENTORIES.put(player.getUUID(), snapshot);
	}

	static boolean applyPendingRespawnPetInventory(ServerPlayer newPlayer, UUID previousPlayerId) {
		if (newPlayer == null || previousPlayerId == null) return false;
		PetInventory inventory = petInventory(newPlayer);
		ItemStack[] snapshot = PENDING_RESPAWN_PET_INVENTORIES.remove(previousPlayerId);
		if (inventory == null || snapshot == null) return false;
		inventory.runBulkUpdate(() -> {
			for (int slot = 0; slot < PetEntitiesManager.SLOT_COUNT && slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = slot < snapshot.length && snapshot[slot] != null ? snapshot[slot].copy() : ItemStack.EMPTY;
				inventory.setItem(slot, stack);
			}
		});
		PetHudManager.markAbilityHudDirty(newPlayer.getUUID());
		return true;
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

package madoku.craft.mixin.pet;

import madoku.craft.pet.PetComponentsManager.PetHolder;
import madoku.craft.pet.PetComponentsManager.PetInventory;
import madoku.craft.pet.PetComponentsManager.PetSlot;
import madoku.craft.pet.PetEntitiesManager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuPetSlotsMixin extends AbstractContainerMenu {
	protected InventoryMenuPetSlotsMixin(MenuType<?> menuType, int containerId) {
		super(menuType, containerId);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void madokuCraft$addPlayerEntitySlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
		PetInventory petInventory = ((PetHolder) owner).madokuCraft$getPetInventory();
		for (int slot = 0; slot < PetEntitiesManager.SLOT_COUNT; slot++) {
			this.addSlot(new PetSlot(petInventory, slot, PetEntitiesManager.SLOT_X, PetEntitiesManager.SLOT_YS[slot]));
		}
	}

	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$handlePlayerEntityQuickMove(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
		if (slotIndex < 0 || slotIndex >= this.slots.size()) {
			return;
		}

		Slot slot = this.slots.get(slotIndex);
		if (slot == null || !slot.hasItem()) {
			return;
		}

		if (slotIndex >= PetEntitiesManager.FIRST_SLOT_INDEX && slotIndex < PetEntitiesManager.FIRST_SLOT_INDEX + PetEntitiesManager.SLOT_COUNT) {
			cir.setReturnValue(madokuCraft$quickMove(player, slot, 9, PetEntitiesManager.FIRST_SLOT_INDEX));
			return;
		}

		if (slotIndex >= 9 && slotIndex < PetEntitiesManager.FIRST_SLOT_INDEX && PetEntitiesManager.isValid(slot.getItem())) {
			ItemStack moved = madokuCraft$quickMove(
				player,
				slot,
				PetEntitiesManager.FIRST_SLOT_INDEX,
				PetEntitiesManager.FIRST_SLOT_INDEX + PetEntitiesManager.SLOT_COUNT
			);
			if (!moved.isEmpty()) {
				cir.setReturnValue(moved);
			}
		}
	}

	@Unique
	private ItemStack madokuCraft$quickMove(Player player, Slot slot, int startIndex, int endIndex) {
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (!this.moveItemStackTo(stack, startIndex, endIndex, false)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		if (stack.getCount() == copy.getCount()) {
			return ItemStack.EMPTY;
		}

		slot.onTake(player, stack);
		return copy;
	}
}



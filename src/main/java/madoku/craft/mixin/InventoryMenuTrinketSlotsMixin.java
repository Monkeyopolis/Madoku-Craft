package madoku.craft.mixin;

import madoku.craft.trinket.MadokuTrinketHolder;
import madoku.craft.trinket.MadokuTrinketInventory;
import madoku.craft.trinket.MadokuTrinketSlot;
import madoku.craft.trinket.MadokuTrinkets;
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
public abstract class InventoryMenuTrinketSlotsMixin extends AbstractContainerMenu {
	protected InventoryMenuTrinketSlotsMixin(MenuType<?> menuType, int containerId) {
		super(menuType, containerId);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void madokuCraft$addTrinketSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
		MadokuTrinketInventory trinketInventory = ((MadokuTrinketHolder) owner).madokuCraft$getTrinketInventory();
		for (int slot = 0; slot < MadokuTrinkets.SLOT_COUNT; slot++) {
			this.addSlot(new MadokuTrinketSlot(trinketInventory, slot, MadokuTrinkets.SLOT_X, MadokuTrinkets.SLOT_YS[slot]));
		}
	}

	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$handleTrinketQuickMove(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
		if (slotIndex < 0 || slotIndex >= this.slots.size()) {
			return;
		}

		Slot slot = this.slots.get(slotIndex);
		if (slot == null || !slot.hasItem()) {
			return;
		}

		if (slotIndex >= MadokuTrinkets.FIRST_SLOT_INDEX && slotIndex < MadokuTrinkets.FIRST_SLOT_INDEX + MadokuTrinkets.SLOT_COUNT) {
			cir.setReturnValue(madokuCraft$quickMove(player, slot, 9, MadokuTrinkets.FIRST_SLOT_INDEX));
			return;
		}

		if (slotIndex >= 9 && slotIndex < MadokuTrinkets.FIRST_SLOT_INDEX && MadokuTrinkets.isValidTrinket(slot.getItem())) {
			ItemStack moved = madokuCraft$quickMove(player, slot, MadokuTrinkets.FIRST_SLOT_INDEX, MadokuTrinkets.FIRST_SLOT_INDEX + MadokuTrinkets.SLOT_COUNT);
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

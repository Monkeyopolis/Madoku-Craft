package madoku.craft.mixin.core;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import madoku.craft.java.core.smithing.SmithingAPIManager;

/** Routes SmithingMenu shift-clicks through Madoku's physical slot roles. */
@Mixin(ItemCombinerMenu.class)
public abstract class SmithingItemCombinerMenuMixin extends AbstractContainerMenu {
	protected SmithingItemCombinerMenuMixin(MenuType<?> menuType, int containerId) {
		super(menuType, containerId);
	}

	@Inject(method = "quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$quickMoveIntoSmithingSlots(
		Player player,
		int slotIndex,
		CallbackInfoReturnable<ItemStack> cir
	) {
		if (!((Object) this instanceof SmithingMenu)
			|| !SmithingAPIManager.acceptsExtendedItems()
			|| slotIndex < 0
			|| slotIndex >= this.slots.size()) return;

		Slot sourceSlot = this.slots.get(slotIndex);
		ItemStack source = sourceSlot.getItem();
		if (source.isEmpty()) return;
		ItemStack original = source.copy();
		boolean moved = false;

		if (slotIndex >= SmithingMenu.RESULT_SLOT + 1) {
			if (this.slots.get(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty()
				&& SmithingAPIManager.isManagedBase(source)) {
				moved = this.moveItemStackTo(source, SmithingMenu.TEMPLATE_SLOT, SmithingMenu.TEMPLATE_SLOT + 1, false);
			}
			if (!moved && this.slots.get(SmithingMenu.BASE_SLOT).getItem().isEmpty()
				&& (SmithingAPIManager.isTemplateItem(source) || source.is(Items.EXPERIENCE_BOTTLE))) {
				moved = this.moveItemStackTo(source, SmithingMenu.BASE_SLOT, SmithingMenu.BASE_SLOT + 1, false);
			}
			if (!moved && this.slots.get(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty()
				&& this.slots.get(SmithingMenu.ADDITIONAL_SLOT).mayPlace(source)) {
				moved = this.moveItemStackTo(source, SmithingMenu.ADDITIONAL_SLOT, SmithingMenu.ADDITIONAL_SLOT + 1, false);
			}
		} else if (slotIndex <= SmithingMenu.ADDITIONAL_SLOT) {
			moved = this.moveItemStackTo(source, SmithingMenu.RESULT_SLOT + 1, this.slots.size(), false);
		}

		if (!moved || source.getCount() == original.getCount()) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}
		if (source.isEmpty()) sourceSlot.setByPlayer(ItemStack.EMPTY);
		else sourceSlot.setChanged();
		sourceSlot.onTake(player, source);
		cir.setReturnValue(original);
	}
}

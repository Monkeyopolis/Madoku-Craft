package madoku.craft.mixin.core;

import madoku.craft.java.core.smithing.SmithingAPIManager;
import madoku.craft.mixin.inventory.SlotAccessor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin extends net.minecraft.world.inventory.ItemCombinerMenu {
	@Shadow @Final private Level level;

	protected SmithingMenuMixin() {
		super(null, 0, null, null, null);
	}

	@Inject(
		method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;Lnet/minecraft/world/level/Level;)V",
		at = @At("TAIL")
	)
	private void madokuCraft$extendInputSlots(
		int containerId,
		net.minecraft.world.entity.player.Inventory inventory,
		net.minecraft.world.inventory.ContainerLevelAccess access,
		Level level,
		CallbackInfo ci
	) {
		if (!SmithingAPIManager.acceptsExtendedItems()) {
			return;
		}

		SmithingMenu menu = (SmithingMenu) (Object) this;
		// Keep vanilla slot positions. Madoku changes which input role each physical slot serves.
		replaceSlot(menu, SmithingMenu.TEMPLATE_SLOT, SmithingMenu.TEMPLATE_SLOT_X_PLACEMENT, originalSlot(menu, SmithingMenu.TEMPLATE_SLOT));
		replaceSlot(menu, SmithingMenu.BASE_SLOT, SmithingMenu.BASE_SLOT_X_PLACEMENT, originalSlot(menu, SmithingMenu.BASE_SLOT));
		replaceSlot(menu, SmithingMenu.ADDITIONAL_SLOT, SmithingMenu.ADDITIONAL_SLOT_X_PLACEMENT, originalSlot(menu, SmithingMenu.ADDITIONAL_SLOT));
	}

	@Inject(method = "createResult()V", at = @At("TAIL"))
	private void madokuCraft$applyEnchantSmithingResult(CallbackInfo ci) {
		SmithingMenu menu = (SmithingMenu) (Object) this;
		SmithingAPIManager.prepareSwappedRecipeResult(menu, level);
		SmithingAPIManager.applyCustomResult(menu);
	}

	private void replaceSlot(SmithingMenu menu, int slotIndex, int x, Slot original) {
		Slot replacement = new Slot(original.container, slotIndex, x, SmithingMenu.SLOT_Y_PLACEMENT) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				boolean accepted;
				if (!SmithingAPIManager.acceptsExtendedItems()) {
					accepted = original.mayPlace(stack);
				} else if (slotIndex == SmithingMenu.TEMPLATE_SLOT) {
					accepted = SmithingAPIManager.isManagedBase(stack);
				} else if (slotIndex == SmithingMenu.BASE_SLOT) {
					accepted = SmithingAPIManager.isTemplateItem(stack) || stack.is(Items.EXPERIENCE_BOTTLE);
				} else if (slotIndex == SmithingMenu.ADDITIONAL_SLOT) {
					accepted = stack.is(Items.NETHERITE_INGOT)
						|| SmithingAPIManager.isAllowedAdditional(menu, stack)
						|| (SmithingAPIManager.isTemplateItem(menu.getSlot(SmithingMenu.BASE_SLOT).getItem())
							&& original.mayPlace(stack));
				} else {
					accepted = original.mayPlace(stack);
				}
				return accepted;
			}

			@Override
			public int getMaxStackSize() {
				return SmithingAPIManager.acceptsExtendedItems() ? 1 : original.getMaxStackSize();
			}
		};
		((SlotAccessor) (Object) replacement).madokuCraft$setIndex(original.index);
		this.slots.set(slotIndex, replacement);
	}

	private Slot originalSlot(SmithingMenu menu, int slotIndex) {
		return this.slots.get(slotIndex);
	}
}

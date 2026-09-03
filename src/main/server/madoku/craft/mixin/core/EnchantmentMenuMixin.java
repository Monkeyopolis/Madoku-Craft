package madoku.craft.mixin.core;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import madoku.craft.java.core.enchant.EnchantConfigAPIManager;
import madoku.craft.java.core.enchant.EnchantTableAPIManager;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin extends AbstractContainerMenu {
	protected EnchantmentMenuMixin(MenuType<?> menuType, int containerId) {
		super(menuType, containerId);
	}

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
	private void madokuCraft$replaceInputSlot(
		int containerId,
		net.minecraft.world.entity.player.Inventory inventory,
		net.minecraft.world.inventory.ContainerLevelAccess access,
		CallbackInfo ci
	) {
		Slot original = this.slots.get(0);
		this.slots.set(0, new Slot(original.container, 0, 15, 47) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return !EnchantConfigAPIManager.isEnchantmentTableEnabled() || EnchantTableAPIManager.isAcceptedInput(stack);
			}

		});
	}
	@Inject(method = "slotsChanged(Lnet/minecraft/world/Container;)V", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$updateEnchantChoices(Container container, CallbackInfo ci) {
		if (!EnchantTableAPIManager.isAcceptedInput(((EnchantmentMenu) (Object) this).getSlot(0).getItem())) {
			if (madokuCraft$isEnabled()) {
				EnchantTableAPIManager.updateChoices((EnchantmentMenu) (Object) this);
				ci.cancel();
			}
			return;
		}
		if (madokuCraft$isEnabled()) {
			EnchantTableAPIManager.updateChoices((EnchantmentMenu) (Object) this);
			ci.cancel();
		}
	}

	@Inject(method = "clickMenuButton(Lnet/minecraft/world/entity/player/Player;I)Z", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$handleEnchantButton(Player player, int button, CallbackInfoReturnable<Boolean> cir) {
		if (!madokuCraft$isEnabled()) return;
		cir.setReturnValue(EnchantTableAPIManager.handleButton((EnchantmentMenu) (Object) this, player, button));
	}

	@Inject(method = "quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$moveFullInputStack(
		Player player,
		int slotIndex,
		CallbackInfoReturnable<ItemStack> cir
	) {
		if (!madokuCraft$isEnabled() || slotIndex < 2 || slotIndex >= this.slots.size()) return;

		Slot sourceSlot = this.slots.get(slotIndex);
		ItemStack source = sourceSlot.getItem();
		if (!EnchantTableAPIManager.isAcceptedInput(source)) return;

		ItemStack original = source.copy();
		if (!this.moveItemStackTo(source, 0, 1, false)) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}

		if (source.isEmpty()) {
			sourceSlot.setByPlayer(ItemStack.EMPTY);
		} else {
			sourceSlot.setChanged();
		}
		if (source.getCount() == original.getCount()) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}

		sourceSlot.onTake(player, source);
		cir.setReturnValue(original);
	}

	private static boolean madokuCraft$isEnabled() {
		return madoku.craft.java.core.enchant.EnchantConfigAPIManager.isEnchantmentTableEnabled();
	}
}


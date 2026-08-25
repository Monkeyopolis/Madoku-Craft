package madoku.craft.mixin.itemstack;

import madoku.craft.items.ItemsCategoriesManager;
import madoku.craft.items.ItemsStacksManager;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemInstance.class)
public interface ItemStackMaxSizeMixin {
	@Inject(method = "getMaxStackSize", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$adjustMaxStackSize(CallbackInfoReturnable<Integer> cir) {
		if (!((Object) this instanceof ItemStack stack)) {
			return;
		}
		int original = cir.getReturnValue();
		int adjusted = ItemsStacksManager.adjustStackLimit(original);
		adjusted = ItemsCategoriesManager.applySingleStackRule(stack, adjusted);
		if (adjusted != original) {
			cir.setReturnValue(adjusted);
		}
	}
}


package madoku.craft.mixin.itemstack;

import madoku.craft.java.items.ItemsCategoriesAPIManager;
import madoku.craft.java.items.ItemsStacksAPIManager;
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
		int adjusted = ItemsStacksAPIManager.adjustStackLimit(original);
		adjusted = ItemsCategoriesAPIManager.applySingleStackRule(stack, adjusted);
		if (adjusted != original) {
			cir.setReturnValue(adjusted);
		}
	}
}


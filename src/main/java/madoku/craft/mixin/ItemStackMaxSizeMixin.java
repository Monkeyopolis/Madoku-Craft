package madoku.craft.mixin;

import madoku.craft.item.system.MadokuItem;
import madoku.craft.itemstack.system.MadokuItemStack;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMaxSizeMixin {
	@Inject(method = "getMaxStackSize", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$adjustMaxStackSize(CallbackInfoReturnable<Integer> cir) {
		if (!((Object) this instanceof ItemStack stack)) {
			return;
		}
		int original = cir.getReturnValue();
		int adjusted = MadokuItemStack.adjustStackLimit(original);
		adjusted = MadokuItem.applySingleStackRule(stack, adjusted);
		if (adjusted != original) {
			cir.setReturnValue(adjusted);
		}
	}
}

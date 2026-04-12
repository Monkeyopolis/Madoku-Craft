package madoku.craft.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemCost.class)
public abstract class ItemCostEggVariantsMixin {
	@Inject(method = "test", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$acceptAlternateEggs(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		ItemCost cost = (ItemCost) (Object) this;
		if (cost.itemStack().is(Items.EGG)
			&& (stack.is(Items.BLUE_EGG) || stack.is(Items.BROWN_EGG))
			&& cost.components().test(stack)) {
			cir.setReturnValue(true);
		}
	}
}

package madoku.craft.mixin.itemstack;

import madoku.craft.items.ItemsStacksManager;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Container.class)
public interface ContainerStackLimitMixin {
	@Inject(method = "getMaxStackSize()I", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$raiseContainerStackLimit(CallbackInfoReturnable<Integer> cir) {
		int original = cir.getReturnValue();
		int adjusted = ItemsStacksManager.adjustStackLimit(original);
		if (adjusted != original) {
			cir.setReturnValue(adjusted);
		}
	}

	@Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$allowOversizedStackRetention(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		int currentCount = stack.getCount();
		int allowed = cir.getReturnValue();
		if (currentCount > allowed) {
			cir.setReturnValue(currentCount);
		}
	}
}


package madoku.craft.mixin.itemstack;

import madoku.craft.core.enchant.EnchantBooksAPIManager;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reconciles configured Unbreaking whenever an item's enchantment component changes. */
@Mixin(ItemStack.class)
public abstract class ItemStackUnbreakingMixin {
	@Inject(
		method = "<init>(Lnet/minecraft/core/Holder;ILnet/minecraft/core/component/PatchedDataComponentMap;)V",
		at = @At("RETURN")
	)
	private void madokuCraft$reconcileUnbreakingOnConstruction(
		Holder<Item> item,
		int count,
		PatchedDataComponentMap components,
		CallbackInfo callbackInfo
	) {
		EnchantBooksAPIManager.reconcileConfiguredUnbreaking((ItemStack) (Object) this);
	}

	@Inject(
		method = "set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;",
		at = @At("RETURN")
	)
	private <T> void madokuCraft$reconcileUnbreakingOnSet(
		DataComponentType<T> componentType,
		T value,
		CallbackInfoReturnable<T> callbackInfo
	) {
		if (componentType == DataComponents.ENCHANTMENTS) {
			EnchantBooksAPIManager.reconcileConfiguredUnbreaking((ItemStack) (Object) this);
		}
	}

	@Inject(
		method = "remove(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;",
		at = @At("RETURN")
	)
	private <T> void madokuCraft$reconcileUnbreakingOnRemove(
		DataComponentType<? extends T> componentType,
		CallbackInfoReturnable<T> callbackInfo
	) {
		if (componentType == DataComponents.ENCHANTMENTS) {
			EnchantBooksAPIManager.reconcileConfiguredUnbreaking((ItemStack) (Object) this);
		}
	}
}


package madoku.craft.mixin;

import madoku.craft.item.system.MadokuItem;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class ItemStackProfileDefaultsMixin {
	@Inject(
		method = "<init>(Lnet/minecraft/world/level/ItemLike;ILnet/minecraft/core/component/PatchedDataComponentMap;)V",
		at = @At("RETURN")
	)
	private void madokuCraft$applyConfiguredProfilesToFreshStacks(
		ItemLike itemLike,
		int count,
		PatchedDataComponentMap components,
		CallbackInfo ci
	) {
		MadokuItem.applyProfilesToFreshStack((ItemStack) (Object) this);
	}
}

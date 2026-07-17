package madoku.craft.mixin;

import madoku.craft.rarity.MadokuRarity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public class ItemStackDurabilityLoreMixin {
	@Inject(method = "setDamageValue", at = @At("TAIL"))
	private void madokuCraft$updateDurabilityLore(int damage, CallbackInfo ci) {
		MadokuRarity.updateDurabilityLore((ItemStack) (Object) this);
	}
}


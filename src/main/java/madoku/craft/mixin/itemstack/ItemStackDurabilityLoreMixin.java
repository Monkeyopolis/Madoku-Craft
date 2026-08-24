package madoku.craft.mixin.itemstack;

import madoku.craft.api.rarity.MadokuRarityManager;
import madoku.craft.pet.PetHudManager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public class ItemStackDurabilityLoreMixin {
	@Inject(method = "getTooltipLines", at = @At("HEAD"))
	private void madokuCraft$refreshPetLore(
		net.minecraft.world.item.Item.TooltipContext context,
		net.minecraft.world.entity.player.Player player,
		net.minecraft.world.item.TooltipFlag flag,
		CallbackInfoReturnable<java.util.List<net.minecraft.network.chat.Component>> cir
	) {
		PetHudManager.applySupportedPetLore((ItemStack) (Object) this);
	}

	@Inject(method = "setDamageValue", at = @At("TAIL"))
	private void madokuCraft$updateDurabilityLore(int damage, CallbackInfo ci) {
		MadokuRarityManager.updateDurabilityLore((ItemStack) (Object) this);
	}
}


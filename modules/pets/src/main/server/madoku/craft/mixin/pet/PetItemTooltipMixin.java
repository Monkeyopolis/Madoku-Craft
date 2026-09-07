package madoku.craft.mixin.pet;

import madoku.craft.java.pet.PetHudAPIManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Keeps pet-specific tooltip lore owned by the Pets module. */
@Mixin(ItemStack.class)
public final class PetItemTooltipMixin {
	@Inject(method = "getTooltipLines", at = @At("HEAD"))
	private void madokuCraft$refreshPetLore(
		Item.TooltipContext context,
		net.minecraft.world.entity.player.Player player,
		TooltipFlag flag,
		CallbackInfoReturnable<List<Component>> callbackInfo
	) {
		PetHudAPIManager.applySupportedPetLore((ItemStack) (Object) this);
	}
}

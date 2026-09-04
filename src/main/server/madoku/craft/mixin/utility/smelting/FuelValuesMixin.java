package madoku.craft.mixin.utility.smelting;

import madoku.craft.java.items.ItemsAPIManager;
import madoku.craft.java.utility.smelting.SmeltingAPIManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FuelValues;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FuelValues.class)
public abstract class FuelValuesMixin {
	@Inject(method = "isFuel", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$restrictFuelToConfiguredFuelItems(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (!SmeltingAPIManager.isEnabled() || !ItemsAPIManager.isEnabled()) {
			return;
		}

		boolean configuredFuel = ItemsAPIManager.isConfiguredFuel(stack);
		if (configuredFuel) {
			cir.setReturnValue(true);
		}
	}
}


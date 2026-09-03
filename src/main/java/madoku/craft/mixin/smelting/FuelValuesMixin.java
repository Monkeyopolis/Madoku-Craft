package madoku.craft.mixin.smelting;

import madoku.craft.items.MadokuItemsManager;
import madoku.craft.smelting.system.SmeltingAPIManager;
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
		if (!SmeltingAPIManager.isEnabled() || !MadokuItemsManager.isEnabled()) {
			return;
		}

		boolean configuredFuel = MadokuItemsManager.isConfiguredFuel(stack);
		if (configuredFuel) {
			cir.setReturnValue(true);
		}
	}
}


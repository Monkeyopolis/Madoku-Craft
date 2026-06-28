package madoku.craft.mixin;

import madoku.craft.debug.MadokuDebug;
import madoku.craft.item.system.MadokuItem;
import madoku.craft.smelting.system.MadokuSmeltingManager;
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
		if (!MadokuSmeltingManager.isEnabled() || !MadokuItem.isEnabled()) {
			return;
		}

		boolean configuredFuel = MadokuItem.isConfiguredFuel(stack);
		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.SMELTING, "smelting.fuel_gate")) {
			MadokuDebug.event("smelting.fuel_gate", MadokuDebug.Domain.SMELTING)
				.side(MadokuDebug.Side.SERVER)
				.subject("fuel:" + (stack == null || stack.isEmpty() ? "empty" : stack.getItem()))
				.field("item", stack == null || stack.isEmpty() ? "empty" : stack.getItem().toString())
				.field("configured_fuel", configuredFuel)
				.log();
		}
		if (configuredFuel) {
			cir.setReturnValue(true);
		}
	}
}


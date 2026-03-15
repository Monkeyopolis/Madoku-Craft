package madoku.craft.mixin;

import madoku.craft.item.system.MadokuItem;
import madoku.craft.smelting.system.MadokuSmeltingManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceCookTimeMixin {
	@Inject(method = "isFuel", at = @At("HEAD"), cancellable = true)
	private static void madokuCraft$restrictFuelToConfiguredFuelItems(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (!MadokuSmeltingManager.isEnabled() || !MadokuItem.isEnabled()) {
			return;
		}

		cir.setReturnValue(MadokuItem.isConfiguredFuel(stack));
	}

	@Inject(method = "getTotalCookTime", at = @At("RETURN"), cancellable = true)
	private static void madokuCraft$adjustCookTime(
		Level world,
		AbstractFurnaceBlockEntity furnace,
		CallbackInfoReturnable<Integer> cir
	) {
		if (!MadokuSmeltingManager.isEnabled()) {
			return;
		}

		int original = cir.getReturnValue();
		int configured = MadokuSmeltingManager.getCookTimeTicks(furnace, original);
		if (configured > 0 && configured != original) {
			cir.setReturnValue(configured);
		}
	}

	@Inject(method = "getBurnDuration", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$adjustFuelDuration(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		int original = cir.getReturnValue();
		int itemConfigured = MadokuItem.adjustFuelTicks(stack, original);
		AbstractFurnaceBlockEntity self = (AbstractFurnaceBlockEntity) (Object) this;
		int adjusted = MadokuSmeltingManager.getAdjustedFuelTicks(self, stack, itemConfigured);
		if (adjusted != original) {
			cir.setReturnValue(adjusted);
		}
	}
}

package madoku.craft.mixin.smelting;

import madoku.craft.items.ItemsCategoriesManager;
import madoku.craft.smelting.system.MadokuSmeltingManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceCookTimeMixin {
	@Inject(method = "getTotalCookTime", at = @At("RETURN"), cancellable = true)
	private static void madokuCraft$adjustCookTime(
		ServerLevel world,
		AbstractFurnaceBlockEntity furnace,
		CallbackInfoReturnable<Integer> cir
	) {
		if (!MadokuSmeltingManager.isEnabled() || furnace == null) {
			return;
		}

		int original = cir.getReturnValue();
		int configured = MadokuSmeltingManager.getCookTimeTicks(furnace, original);
		if (configured > 0 && configured != original) {
			cir.setReturnValue(configured);
		}
	}

	@Inject(method = "getBurnDuration", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$adjustFuelDuration(
		FuelValues fuelValues,
		ItemStack stack,
		CallbackInfoReturnable<Integer> cir
	) {
		if (!MadokuSmeltingManager.isEnabled() || stack == null || stack.isEmpty()) {
			return;
		}

		int original = cir.getReturnValue();
		int itemConfigured = ItemsCategoriesManager.adjustFuelTicks(stack, original);
		AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) (Object) this;
		int adjusted = MadokuSmeltingManager.getAdjustedFuelTicks(furnace, stack, itemConfigured);
		if (adjusted > 0 && adjusted != original) {
			cir.setReturnValue(adjusted);
		}
	}
}


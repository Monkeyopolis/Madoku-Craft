package madoku.craft.mixin;

import madoku.craft.itemstack.system.MadokuItemStack;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public abstract class PlayerInventoryDeathDropMixin {
	@Inject(method = "dropAll", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$dropConfiguredPercentage(CallbackInfo ci) {
		if (MadokuItemStack.handleInventoryDrop((Inventory) (Object) this)) {
			ci.cancel();
		}
	}
}


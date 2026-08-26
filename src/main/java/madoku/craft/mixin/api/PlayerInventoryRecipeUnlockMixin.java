package madoku.craft.mixin.api;

import madoku.craft.api.recipes.MadokuRecipesManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public abstract class PlayerInventoryRecipeUnlockMixin {
	@Inject(method = "setChanged", at = @At("TAIL"))
	private void madokuCraft$unlockRecipesFromInventoryChange(CallbackInfo ci) {
		Inventory inventory = (Inventory) (Object) this;
		if (inventory.player instanceof ServerPlayer player) {
			MadokuRecipesManager.onPlayerInventoryChanged(player);
		}
	}
}

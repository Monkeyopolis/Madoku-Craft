package madoku.craft.mixin.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import madoku.craft.java.core.recipes.RecipesAPIManager;

@Mixin(Inventory.class)
public abstract class PlayerInventoryRecipeUnlockMixin {
	@Inject(method = "setChanged", at = @At("TAIL"))
	private void madokuCraft$unlockRecipesFromInventoryChange(CallbackInfo ci) {
		Inventory inventory = (Inventory) (Object) this;
		if (inventory.player instanceof ServerPlayer player) {
			RecipesAPIManager.onPlayerInventoryChanged(player);
		}
	}
}

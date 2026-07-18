package madoku.craft.mixin.client;

import madoku.craft.MadokuHud;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerFoodMixin {
	@Redirect(
		method = "handleSetHealth",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/food/FoodData;setFoodLevel(I)V"
		)
	)
	private void madokuCraft$ignoreVanillaFoodUpdateWhenHudDisabled(FoodData foodData, int foodLevel) {
		if (!MadokuHud.shouldIgnoreVanillaFoodUpdate()) {
			foodData.setFoodLevel(foodLevel);
		}
	}
}

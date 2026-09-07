package madoku.craft.mixin.attributes;

import madoku.craft.java.hud.HudPayloadManager;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Hud.class)
public abstract class HudFoodLevelMixin {
	@Redirect(
		method = "extractFood",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/food/FoodData;getFoodLevel()I"
		)
	)
	private int madokuCraft$normalizeFoodLevelForVanillaBars(FoodData foodData) {
		if (!HudPayloadManager.hasServerHunger()) {
			return Math.max(0, Math.min(20, foodData.getFoodLevel()));
		}

		int maxHunger = Math.max(1, HudPayloadManager.getServerHungerMax());
		int currentHunger = Math.max(0, Math.min(maxHunger, HudPayloadManager.getServerHungerCurrent()));
		return Math.max(0, Math.min(20, Math.round(currentHunger * 20.0F / maxHunger)));
	}
}

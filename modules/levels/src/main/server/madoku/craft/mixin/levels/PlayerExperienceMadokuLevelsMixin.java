package madoku.craft.mixin.levels;

import madoku.craft.java.levels.LevelsPlayerAPIManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerExperienceMadokuLevelsMixin {
	@Inject(method = "giveExperiencePoints", at = @At("HEAD"))
	private void madokuCraft$gainMadokuLevelsXp(int experience, CallbackInfo callbackInfo) {
		if (experience <= 0 || !((Object) this instanceof ServerPlayer serverPlayer)) {
			return;
		}

		LevelsPlayerAPIManager.addXp(serverPlayer, experience);
	}
}



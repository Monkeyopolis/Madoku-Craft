package madoku.craft.mixin;

import madoku.craft.time.MadokuTime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public abstract class PlayerSleepTickMixin {
	@Redirect(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;isDay()Z"
		)
	)
	private boolean madokuCraft$keepSleepingUntilConfiguredDayStart(Level level) {
		if (level == null || level.isClientSide() || !MadokuTime.isEnabled()) {
			return level != null && level.isDay();
		}

		return MadokuTime.isDaytime(level.getDayTime());
	}
}

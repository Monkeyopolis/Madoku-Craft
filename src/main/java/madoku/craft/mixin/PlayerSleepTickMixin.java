package madoku.craft.mixin;

import madoku.craft.api.time.TimeSleepManager;
import net.minecraft.world.attribute.BedRule;
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
			target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"
		)
	)
	private boolean madoku$keepSleepingUntilConfiguredDayStart(BedRule bedRule, Level level) {
		// In multiplayer, only the server should decide bed sleep timing.
		if (level.isClientSide()) {
			return bedRule.canSleep(level);
		}
		return TimeSleepManager.shouldKeepSleepingWhileForwarding(bedRule, level, (Player) (Object) this);
	}
}


package madoku.craft.mixin.core;

import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import madoku.craft.java.core.time.TimeAPIManager;

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
		return TimeAPIManager.shouldKeepSleepingWhileForwarding(bedRule, level, (Player) (Object) this);
	}
}


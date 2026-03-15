package madoku.craft.mixin;

import madoku.craft.time.MadokuSleep;
import madoku.craft.time.MadokuTime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ServerPlayer.class, priority = 500)
public abstract class ServerPlayerSleepTimeMixin {
	@Redirect(
		method = "startSleepInBed",
		require = 0,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;isDay()Z"
		)
	)
	private boolean madokuCraft$applyConfiguredSleepTime(Level level, BlockPos sleepingPos) {
		if (level == null || !MadokuTime.isEnabled()) {
			return level != null && level.isDay();
		}

		ServerPlayer player = (ServerPlayer) (Object) this;
		return !MadokuSleep.canStartSleeping(player);
	}
}

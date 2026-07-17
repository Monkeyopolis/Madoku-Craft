package madoku.craft.mixin;

import madoku.craft.api.time.TimeClockManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ServerLevel.class)
public abstract class ServerLevelTimeMixin {
	@ModifyArg(
		method = "tickTime",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"
		),
		index = 0
	)
	private long madokuCraft$applyConfiguredWorldTimeRate(long vanillaNextDayTime) {
		return TimeClockManager.resolveNextWorldTime((ServerLevel) (Object) this, vanillaNextDayTime);
	}
}

package madoku.craft.mixin;

import madoku.craft.api.time.SleepManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSleepTimeMixin {
	@Redirect(
		method = "startSleepInBed",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"
		)
	)
	private boolean madoku$applyConfiguredSleepTime(BedRule bedRule, Level level, BlockPos sleepingPos) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		return SleepManager.shouldAllowBedSleepByTime(bedRule, level, player);
	}
}


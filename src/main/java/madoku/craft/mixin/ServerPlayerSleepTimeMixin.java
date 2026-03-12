package madoku.craft.mixin;

import madoku.craft.clock.MadokuClock;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.time.MadokuSleep;
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
		boolean allowed = MadokuSleep.shouldAllowBedSleepByTime(bedRule, level, player);
		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.SLEEP, "sleep.bed_attempt")) {
			MadokuDebug.event("sleep.bed_attempt", MadokuDebug.Domain.SLEEP)
				.side(level.isClientSide() ? MadokuDebug.Side.CLIENT : MadokuDebug.Side.SERVER)
				.tick(MadokuClock.getGameplayTicks())
				.world(level.dimension().toString())
				.subject("player:" + player.getUUID())
				.field("allowed", allowed)
				.field("sleep_window", MadokuSleep.canStartSleeping(player))
				.field("can_set_spawn", bedRule.canSetSpawn(level))
				.field("vanilla_can_sleep", bedRule.canSleep(level))
				.field("bed_pos", sleepingPos)
				.log();
		}
		return allowed;
	}
}

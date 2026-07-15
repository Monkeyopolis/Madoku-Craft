package madoku.craft.mixin;

import madoku.craft.api.time.TimeConfigManager;
import madoku.craft.api.time.TimeSleepManager;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
		return TimeSleepManager.shouldAllowBedSleepByTime(bedRule, level, player);
	}

	@Inject(
		method = "startSleepInBed",
		at = @At("RETURN")
	)
	private void madoku$recordSleepStart(BlockPos sleepingPos, CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
		if (cir.getReturnValue() != null && cir.getReturnValue().right().isPresent()) {
		TimeSleepManager.onSleepStarted((ServerPlayer) (Object) this);
		}
	}

	@Inject(
		method = "startSleepInBed",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/attribute/BedRule;asProblem()Lnet/minecraft/world/entity/player/Player$BedSleepingProblem;",
			shift = At.Shift.BEFORE
		),
		cancellable = true
	)
	private void madoku$replaceNightOnlySleepMessage(BlockPos sleepingPos, CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
		if (TimeConfigManager.isThunderstormBypassEnabled()) {
			return;
		}
		cir.setReturnValue(Either.left(new Player.BedSleepingProblem(
			Component.translatable("message.madoku-craft.sleep.not_possible")
		)));
	}

}


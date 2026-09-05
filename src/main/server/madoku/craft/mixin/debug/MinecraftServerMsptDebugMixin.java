package madoku.craft.mixin.debug;

import madoku.craft.java.debug.MadokuMsptDebug;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary timing hooks for the server-wide tick phases. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMsptDebugMixin {
	@Inject(method = "processPacketsAndTick", at = @At("HEAD"))
	private void madokuCraft$beginFrame(boolean tick, CallbackInfo ci) {
		MadokuMsptDebug.beginFrame((MinecraftServer) (Object) this);
	}

	@Inject(method = "processPacketsAndTick", at = @At("RETURN"))
	private void madokuCraft$endFrame(boolean tick, CallbackInfo ci) {
		MadokuMsptDebug.endFrame((MinecraftServer) (Object) this);
	}

	@Inject(method = "processPacketsAndTick", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/network/PacketProcessor;processQueuedPackets()V"
	))
	private void madokuCraft$beginPacketProcessing(boolean tick, CallbackInfo ci) {
		MadokuMsptDebug.beginSection("vanilla.packet_processing");
	}

	@Inject(method = "processPacketsAndTick", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/network/PacketProcessor;processQueuedPackets()V",
		shift = At.Shift.AFTER
	))
	private void madokuCraft$endPacketProcessing(boolean tick, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tickServer", at = @At("HEAD"))
	private void madokuCraft$beginServerTick(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.beginServerTick();
	}

	@Inject(method = "tickServer", at = @At("RETURN"))
	private void madokuCraft$endServerTick(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.endServerTick();
	}

	@Inject(method = "tickChildren", at = @At("HEAD"))
	private void madokuCraft$beginServerChildrenTick(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.beginSection("vanilla.server_children");
	}

	@Inject(method = "tickChildren", at = @At("RETURN"))
	private void madokuCraft$endServerChildrenTick(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tickConnection", at = @At("HEAD"))
	private void madokuCraft$beginConnectionTick(CallbackInfo ci) {
		MadokuMsptDebug.beginSection("vanilla.connection");
	}

	@Inject(method = "tickConnection", at = @At("RETURN"))
	private void madokuCraft$endConnectionTick(CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tickChildren", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/server/ServerFunctionManager;tick()V"
	))
	private void madokuCraft$beginFunctionTick(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.beginSection("vanilla.command_functions");
	}

	@Inject(method = "tickChildren", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/server/ServerFunctionManager;tick()V",
		shift = At.Shift.AFTER
	))
	private void madokuCraft$endFunctionTick(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tickChildren", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/world/clock/ServerClockManager;tick()V"
	))
	private void madokuCraft$beginClockTick(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.beginSection("vanilla.server_clocks");
	}

	@Inject(method = "tickChildren", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/world/clock/ServerClockManager;tick()V",
		shift = At.Shift.AFTER
	))
	private void madokuCraft$endClockTick(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tickChildren", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/server/players/PlayerList;tick()V"
	))
	private void madokuCraft$beginPlayerTick(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.beginSection("vanilla.players");
	}

	@Inject(method = "tickChildren", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/server/players/PlayerList;tick()V",
		shift = At.Shift.AFTER
	))
	private void madokuCraft$endPlayerTick(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tickChildren", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/gametest/framework/GameTestTicker;tick()V"
	))
	private void madokuCraft$beginGameTestTick(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.beginSection("vanilla.game_tests");
	}

	@Inject(method = "tickChildren", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/gametest/framework/GameTestTicker;tick()V",
		shift = At.Shift.AFTER
	))
	private void madokuCraft$endGameTestTick(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tickChildren", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/server/notifications/ServerActivityMonitor;tick()V"
	))
	private void madokuCraft$beginActivityMonitorTick(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.beginSection("vanilla.activity_monitor");
	}

	@Inject(method = "tickChildren", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/server/notifications/ServerActivityMonitor;tick()V",
		shift = At.Shift.AFTER
	))
	private void madokuCraft$endActivityMonitorTick(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}
}

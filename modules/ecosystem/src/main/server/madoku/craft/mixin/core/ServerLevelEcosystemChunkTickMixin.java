package madoku.craft.mixin.core;

import madoku.craft.java.ecosystem.EcosystemChunkTickAPIManager;
import madoku.craft.java.ecosystem.EcosystemMsptMonitor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives ecosystem systems one callback for each active vanilla chunk tick. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelEcosystemChunkTickMixin {
	@Inject(method = "tickChunk", at = @At("HEAD"))
	private void madokuCraft$beginMsptChunkTick(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
		EcosystemMsptMonitor.beginChunkTick((ServerLevel) (Object) this, chunk, randomTickSpeed);
	}

	@Inject(
		method = "tickChunk",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
			ordinal = 0,
			shift = At.Shift.AFTER
		)
	)
	private void madokuCraft$beginMsptRandomTickPass(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
		EcosystemMsptMonitor.beginRandomTickPass();
	}

	@Inject(
		method = "tickChunk",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"
		)
	)
	private void madokuCraft$recordMsptBlockRandomTick(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
		EcosystemMsptMonitor.recordBlockRandomTick();
	}

	@Inject(
		method = "tickChunk",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"
		)
	)
	private void madokuCraft$recordMsptFluidRandomTick(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
		EcosystemMsptMonitor.recordFluidRandomTick();
	}

	@Inject(
		method = "tickChunk",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V",
			ordinal = 1,
			shift = At.Shift.BEFORE
		)
	)
	private void madokuCraft$finishMsptRandomTickPass(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
		EcosystemMsptMonitor.finishRandomTickPass();
	}

	@Inject(method = "tickChunk", at = @At("TAIL"))
	private void madokuCraft$dispatchChunkTick(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
		try {
			EcosystemChunkTickAPIManager.dispatch((ServerLevel) (Object) this, chunk);
		} finally {
			EcosystemMsptMonitor.finishChunkTick();
		}
	}
}

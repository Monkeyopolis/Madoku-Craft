package madoku.craft.mixin.debug;

import madoku.craft.java.debug.MadokuMsptDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** Temporary timing hooks for the important per-dimension server phases. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMsptDebugMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void madokuCraft$beginLevelTick(BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.beginLevelSection((ServerLevel) (Object) this, "tick");
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void madokuCraft$endLevelTick(BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tickTime", at = @At("HEAD"))
	private void madokuCraft$beginTimeTick(CallbackInfo ci) {
		MadokuMsptDebug.beginLevelSection((ServerLevel) (Object) this, "time");
	}

	@Inject(method = "tickTime", at = @At("RETURN"))
	private void madokuCraft$endTimeTick(CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "advanceWeatherCycle", at = @At("HEAD"))
	private void madokuCraft$beginWeatherTick(CallbackInfo ci) {
		MadokuMsptDebug.beginLevelSection((ServerLevel) (Object) this, "weather");
	}

	@Inject(method = "advanceWeatherCycle", at = @At("RETURN"))
	private void madokuCraft$endWeatherTick(CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tickCustomSpawners", at = @At("HEAD"))
	private void madokuCraft$beginCustomSpawners(boolean spawnEnemies, CallbackInfo ci) {
		MadokuMsptDebug.beginLevelSection((ServerLevel) (Object) this, "custom_spawners");
	}

	@Inject(method = "tickCustomSpawners", at = @At("RETURN"))
	private void madokuCraft$endCustomSpawners(boolean spawnEnemies, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tickChunk", at = @At("HEAD"))
	private void madokuCraft$beginRandomChunkTick(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
		MadokuMsptDebug.beginRandomChunk((ServerLevel) (Object) this, chunk);
	}

	@Inject(method = "tickChunk", at = @At("RETURN"))
	private void madokuCraft$endRandomChunkTick(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tickNonPassenger", at = @At("HEAD"))
	private void madokuCraft$beginEntityTick(Entity entity, CallbackInfo ci) {
		MadokuMsptDebug.beginEntity((ServerLevel) (Object) this, entity);
	}

	@Inject(method = "tickNonPassenger", at = @At("RETURN"))
	private void madokuCraft$endEntityTick(Entity entity, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tickBlock", at = @At("HEAD"))
	private void madokuCraft$beginScheduledBlockTick(BlockPos pos, Block block, CallbackInfo ci) {
		MadokuMsptDebug.beginScheduledBlock((ServerLevel) (Object) this, block, pos);
	}

	@Inject(method = "tickBlock", at = @At("RETURN"))
	private void madokuCraft$endScheduledBlockTick(BlockPos pos, Block block, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tickFluid", at = @At("HEAD"))
	private void madokuCraft$beginScheduledFluidTick(BlockPos pos, Fluid fluid, CallbackInfo ci) {
		MadokuMsptDebug.beginScheduledFluid((ServerLevel) (Object) this, fluid, pos);
	}

	@Inject(method = "tickFluid", at = @At("RETURN"))
	private void madokuCraft$endScheduledFluidTick(BlockPos pos, Fluid fluid, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tick", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V",
		ordinal = 0
	))
	private void madokuCraft$beginBlockTickQueue(BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.beginLevelSection((ServerLevel) (Object) this, "block_tick_queue");
	}

	@Inject(method = "tick", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V",
		ordinal = 0,
		shift = At.Shift.AFTER
	))
	private void madokuCraft$endBlockTickQueue(BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tick", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V",
		ordinal = 1
	))
	private void madokuCraft$beginFluidTickQueue(BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.beginLevelSection((ServerLevel) (Object) this, "fluid_tick_queue");
	}

	@Inject(method = "tick", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V",
		ordinal = 1,
		shift = At.Shift.AFTER
	))
	private void madokuCraft$endFluidTickQueue(BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tick", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/world/entity/raid/Raids;tick(Lnet/minecraft/server/level/ServerLevel;)V"
	))
	private void madokuCraft$beginRaidTick(BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.beginLevelSection((ServerLevel) (Object) this, "raids");
	}

	@Inject(method = "tick", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/world/entity/raid/Raids;tick(Lnet/minecraft/server/level/ServerLevel;)V",
		shift = At.Shift.AFTER
	))
	private void madokuCraft$endRaidTick(BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tick", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/server/level/ServerLevel;runBlockEvents()V"
	))
	private void madokuCraft$beginBlockEvents(BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.beginLevelSection((ServerLevel) (Object) this, "block_events");
	}

	@Inject(method = "tick", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/server/level/ServerLevel;runBlockEvents()V",
		shift = At.Shift.AFTER
	))
	private void madokuCraft$endBlockEvents(BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tick", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"
	))
	private void madokuCraft$beginEntityList(BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.beginLevelSection((ServerLevel) (Object) this, "entity_list");
	}

	@Inject(method = "tick", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V",
		shift = At.Shift.AFTER
	))
	private void madokuCraft$endEntityList(BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}

	@Inject(method = "tick", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;tick()V"
	))
	private void madokuCraft$beginEntityManagement(BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.beginLevelSection((ServerLevel) (Object) this, "entity_management");
	}

	@Inject(method = "tick", at = @At(
		value = "INVOKE",
		target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;tick()V",
		shift = At.Shift.AFTER
	))
	private void madokuCraft$endEntityManagement(BooleanSupplier haveTime, CallbackInfo ci) {
		MadokuMsptDebug.endSection();
	}
}

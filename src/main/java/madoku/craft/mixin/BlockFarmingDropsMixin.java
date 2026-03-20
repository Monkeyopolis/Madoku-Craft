package madoku.craft.mixin;

import madoku.craft.clock.MadokuClock;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.farming.system.MadokuFarming;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockFarmingDropsMixin {
	@Inject(
		method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void madokuCraft$overrideCropHarvestDrops(
		BlockState state,
		Level level,
		BlockPos pos,
		CallbackInfo ci
	) {
		emitDropProbe("dropResources(level, pos)", level instanceof ServerLevel serverLevel ? serverLevel : null, pos, state, null, null);
		overrideCropHarvestDrops(state, level instanceof ServerLevel serverLevel ? serverLevel : null, pos, ci);
	}

	@Inject(
		method = "playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;",
		at = @At("HEAD")
	)
	private static void madokuCraft$snapshotCropHarvest(
		Level level,
		BlockPos pos,
		BlockState state,
		Player player,
		CallbackInfoReturnable<BlockState> cir
	) {
		if (level instanceof ServerLevel serverLevel) {
			MadokuFarming.prepareCropHarvest(serverLevel, pos, state);
		}
	}

	@Inject(
		method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void madokuCraft$overrideCropHarvestDrops(
		BlockState state,
		LevelAccessor level,
		BlockPos pos,
		BlockEntity blockEntity,
		CallbackInfo ci
	) {
		emitDropProbe("dropResources(levelAccessor, pos, blockEntity)", level instanceof ServerLevel serverLevel ? serverLevel : null, pos, state, blockEntity, null);
		overrideCropHarvestDrops(state, level instanceof ServerLevel serverLevel ? serverLevel : null, pos, ci);
	}

	@Inject(
		method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void madokuCraft$overrideCropHarvestDrops(
		BlockState state,
		Level level,
		BlockPos pos,
		BlockEntity blockEntity,
		net.minecraft.world.entity.Entity entity,
		ItemStack tool,
		CallbackInfo ci
	) {
		emitDropProbe("dropResources(level, pos, blockEntity, entity, tool)", level instanceof ServerLevel serverLevel ? serverLevel : null, pos, state, blockEntity, tool);
		overrideCropHarvestDrops(state, level instanceof ServerLevel serverLevel ? serverLevel : null, pos, ci);
	}

	private static void overrideCropHarvestDrops(
		BlockState state,
		ServerLevel level,
		BlockPos pos,
		CallbackInfo ci
	) {
		if (!MadokuFarming.isEnabled() || !MadokuFarming.isManagedHarvestState(level, pos, state) || !MadokuFarming.hasPendingHarvest(level, pos, state)) {
			return;
		}

		MadokuFarming.prepareCropHarvest(level, pos, state);
		RandomSource random = level == null ? RandomSource.create() : level.getRandom();
		int count = MadokuFarming.calculateCropHarvestCount(level, pos, state, random);
		if (count <= 0) {
			return;
		}

		Item harvestItem = MadokuFarming.getCropHarvestItem(level, pos, state);
		if (harvestItem == null) {
			return;
		}

		Block.popResource(level, pos, new ItemStack(harvestItem, count));
		Item secondaryHarvestItem = MadokuFarming.getCropSecondaryHarvestItem(level, pos, state);
		int secondaryCount = MadokuFarming.calculateCropSecondaryHarvestCount(level, pos, state, random);
		if (secondaryHarvestItem != null && secondaryCount > 0) {
			Block.popResource(level, pos, new ItemStack(secondaryHarvestItem, secondaryCount));
		}
		MadokuFarming.completeCropHarvest(level, pos, state);
		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.FARMING, "farming.crop_drops")) {
			MadokuDebug.event("farming.crop_drops", MadokuDebug.Domain.FARMING)
				.side(MadokuDebug.Side.SERVER)
				.tick(MadokuClock.getGameplayTicks())
				.world(level == null ? "" : level.dimension().toString())
				.subject(pos == null ? "crop" : "crop:" + pos.getX() + "," + pos.getY() + "," + pos.getZ())
				.field("count", Integer.toString(count))
				.field("state", state.getBlock().toString())
				.field("max_age", Boolean.toString(MadokuFarming.isCropHarvestReady(level, pos, state)))
				.log();
		}

		ci.cancel();
	}

	private static void emitDropProbe(
		String source,
		ServerLevel level,
		BlockPos pos,
		BlockState state,
		BlockEntity blockEntity,
		ItemStack tool
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.FARMING, "farming.crop_drop_probe")) {
			return;
		}

		boolean managed = MadokuFarming.isManagedCrop(level, pos, state);
		boolean ready = managed && MadokuFarming.isCropHarvestReady(level, pos, state);
		MadokuDebug.event("farming.crop_drop_probe", MadokuDebug.Domain.FARMING)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuClock.getGameplayTicks())
			.world(level == null ? "" : level.dimension().toString())
			.subject(source)
			.field("managed", Boolean.toString(managed))
			.field("ready", Boolean.toString(ready))
			.field("state", state == null ? "unknown" : state.getBlock().toString())
			.field("pos", pos == null ? "unknown" : pos.getX() + "," + pos.getY() + "," + pos.getZ())
			.field("block_entity", Boolean.toString(blockEntity != null))
			.field("tool", tool == null ? "unknown" : tool.toString())
			.log();
	}
}

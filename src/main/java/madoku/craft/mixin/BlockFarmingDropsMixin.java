package madoku.craft.mixin;

import madoku.craft.debug.MadokuDebug;
import madoku.craft.farming.system.MadokuFarming;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(Block.class)
public abstract class BlockFarmingDropsMixin {
	@Inject(
		method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
		at = @At("RETURN"),
		cancellable = true
	)
	private static void madokuCraft$overrideCropHarvestDrops(
		BlockState state,
		ServerLevel level,
		BlockPos pos,
		BlockEntity blockEntity,
		Entity entity,
		ItemStack tool,
		CallbackInfoReturnable<List<ItemStack>> cir
	) {
		if (!MadokuFarming.isEnabled() || !MadokuFarming.isManagedCrop(level, pos, state) || !MadokuFarming.isCropHarvestReady(level, pos, state)) {
			return;
		}

		RandomSource random = level == null ? RandomSource.create() : level.getRandom();
		int count = MadokuFarming.calculateCropHarvestCount(level, pos, state, random);
		if (count <= 0) {
			return;
		}

		Item harvestItem = MadokuFarming.getCropHarvestItem(level, pos, state);
		if (harvestItem == null) {
			return;
		}

		List<ItemStack> drops = new ArrayList<>(1);
		drops.add(new ItemStack(harvestItem, count));
		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.FARMING, "farming.crop_drops")) {
			MadokuDebug.event("farming.crop_drops", MadokuDebug.Domain.FARMING)
				.side(MadokuDebug.Side.SERVER)
				.tick(madoku.craft.clock.MadokuClock.getGameplayTicks())
				.world(level == null ? "" : level.dimension().toString())
				.subject(pos == null ? "crop" : "crop:" + pos.getX() + "," + pos.getY() + "," + pos.getZ())
				.field("count", Integer.toString(count))
				.field("state", state.getBlock().toString())
				.field("max_age", Boolean.toString(MadokuFarming.isCropHarvestReady(level, pos, state)))
				.log();
		}
		cir.setReturnValue(drops);
	}
}

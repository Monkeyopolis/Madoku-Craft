package madoku.craft.mixin.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import madoku.craft.core.data.MadokuChunkDataManager;
import madoku.craft.core.helper.MadokuBlockDropContextManager;

@Mixin(Block.class)
public abstract class BlockPlayerDestroyDropContextMixin {
	@Inject(
		method = "playerDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V",
		at = @At("HEAD")
	)
	private void madokuCraft$beginPlayerDestroyDropContext(
		Level level,
		Player player,
		BlockPos pos,
		BlockState state,
		BlockEntity blockEntity,
		ItemStack tool,
		CallbackInfo ci
	) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			MadokuBlockDropContextManager.begin(serverLevel, serverPlayer, pos, state);
		} else {
			MadokuBlockDropContextManager.end();
		}
	}

	@Inject(
		method = "playerDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V",
		at = @At("RETURN")
	)
	private void madokuCraft$endPlayerDestroyDropContext(
		Level level,
		Player player,
		BlockPos pos,
		BlockState state,
		BlockEntity blockEntity,
		ItemStack tool,
		CallbackInfo ci
	) {
		MadokuBlockDropContextManager.end();
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer) {
			MadokuChunkDataManager.removePlayerPlacedBlock(serverLevel, pos);
		}
	}
}

package madoku.craft.mixin;

import madoku.craft.farming.system.MadokuFarming;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackFarmingMixin {
	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$handleFarmingItemUse(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		ItemStack stack = (ItemStack) (Object) this;
		if (!MadokuFarming.isEnabled() || context == null || stack.isEmpty()) {
			return;
		}

		Level level = context.getLevel();
		if (level == null) {
			return;
		}

		BlockPos pos = context.getClickedPos();
		if (pos == null) {
			return;
		}

		BlockState state = level.getBlockState(pos);
		if (stack.is(Items.BONE_MEAL) && MadokuFarming.isManagedCrop(state)) {
			cir.setReturnValue(InteractionResult.FAIL);
			return;
		}

		if (stack.is(Items.BONE_MEAL) && MadokuFarming.isFarmland(state)) {
			if (level instanceof ServerLevel serverLevel && MadokuFarming.isFertilized(serverLevel, pos)) {
				if (context.getPlayer() != null) {
					context.getPlayer().displayClientMessage(Component.literal("Farmland is already fertilized."), true);
				}
				cir.setReturnValue(InteractionResult.FAIL);
				return;
			}

			if (level.isClientSide()) {
				cir.setReturnValue(InteractionResult.SUCCESS);
				return;
			}

			MadokuFarming.fertilizeSoil((ServerLevel) level, pos);
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}

		if (MadokuFarming.isCropPlantItem(stack) && MadokuFarming.isFarmland(state) && !MadokuFarming.canPlantCrop(stack)) {
			if (level instanceof ServerLevel && context.getPlayer() != null) {
				context.getPlayer().displayClientMessage(Component.literal(MadokuFarming.getCropSeasonBlockedMessage(stack)), true);
			}
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "useOn", at = @At("RETURN"))
	private void madokuCraft$trackCropPlanting(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		ItemStack stack = (ItemStack) (Object) this;
		if (!MadokuFarming.isEnabled() || context == null || stack.isEmpty() || !MadokuFarming.isCropPlantItem(stack)) {
			return;
		}

		InteractionResult result = cir.getReturnValue();
		if (result != InteractionResult.SUCCESS && result != InteractionResult.CONSUME) {
			return;
		}

		Level level = context.getLevel();
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		BlockPos soilPos = context.getClickedPos();
		if (soilPos == null || !MadokuFarming.isFarmland(level.getBlockState(soilPos))) {
			return;
		}

		if (!MadokuFarming.canPlantCrop(stack)) {
			return;
		}

		BlockState cropState = level.getBlockState(soilPos.above());
		if (!MadokuFarming.isManagedCrop(cropState)) {
			return;
		}

		MadokuFarming.registerCropPlanting(serverLevel, soilPos, stack);
		MadokuFarming.trackCrop(serverLevel, soilPos.above(), cropState);
	}
}

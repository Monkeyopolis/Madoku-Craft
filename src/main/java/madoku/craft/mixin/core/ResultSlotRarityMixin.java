package madoku.craft.mixin.core;

import madoku.craft.core.recipes.RecipesAPIManager;
import madoku.craft.items.MadokuItemsManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ResultSlot.class)
public class ResultSlotRarityMixin {
	@Unique
	private List<ItemStack> madokuCraft$pendingCraftExtras = List.of();

	@Inject(method = "onTake", at = @At("HEAD"))
	private void madokuCraft$applyCraftRarity(Player player, ItemStack stack, CallbackInfo ci) {
		madokuCraft$pendingCraftExtras = List.of();
		if (player instanceof ServerPlayer serverPlayer) {
			MadokuItemsManager.applyConfiguredItemLevel(stack, 1);
			madokuCraft$pendingCraftExtras = RecipesAPIManager.applyCraftedRarity(serverPlayer, stack);
		}
	}

	@Inject(method = "onTake", at = @At("TAIL"))
	private void madokuCraft$deliverCraftExtras(Player player, ItemStack stack, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer serverPlayer) || madokuCraft$pendingCraftExtras.isEmpty()) {
			return;
		}
		RecipesAPIManager.deliverCraftExtras(serverPlayer, madokuCraft$pendingCraftExtras);
		madokuCraft$pendingCraftExtras = List.of();
	}
}

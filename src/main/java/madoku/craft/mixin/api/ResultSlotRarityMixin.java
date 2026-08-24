package madoku.craft.mixin.api;

import madoku.craft.api.rarity.MadokuRarityManager;
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
			madokuCraft$pendingCraftExtras = MadokuRarityManager.applyCraftedRarity(serverPlayer, stack);
		}
	}

	@Inject(method = "onTake", at = @At("TAIL"))
	private void madokuCraft$deliverCraftExtras(Player player, ItemStack stack, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer serverPlayer) || madokuCraft$pendingCraftExtras.isEmpty()) {
			return;
		}
		MadokuRarityManager.deliverCraftExtras(serverPlayer, madokuCraft$pendingCraftExtras);
		madokuCraft$pendingCraftExtras = List.of();
	}
}

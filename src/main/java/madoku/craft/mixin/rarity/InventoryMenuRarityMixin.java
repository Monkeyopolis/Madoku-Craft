package madoku.craft.mixin.rarity;

import madoku.craft.rarity.MadokuRarity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuRarityMixin {
	@Inject(method = "quickMoveStack", at = @At("HEAD"))
	private void madokuCraft$applyShiftCraftRarity(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
		if (slotIndex != 0 || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		Slot resultSlot = ((AbstractContainerMenu) (Object) this).getSlot(slotIndex);
		if (resultSlot == null || !resultSlot.hasItem()) {
			return;
		}

		List<ItemStack> extras = MadokuRarity.applyCraftedRarity(serverPlayer, resultSlot.getItem());
		MadokuRarity.deliverCraftExtras(serverPlayer, extras);
	}
}



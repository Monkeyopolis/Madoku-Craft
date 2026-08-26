package madoku.craft.mixin.api;

import madoku.craft.pet.PetHagManager;
import madoku.craft.api.rarity.MadokuRarityManager;
import madoku.craft.items.MadokuItemsManager;
import net.minecraft.server.commands.GiveCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GiveCommand.class)
public class ItemInputRarityMixin {
	@Redirect(
		method = "giveItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"
		)
	)
	private static boolean madokuCraft$applyRarityToEachCommandStack(
		Inventory inventory,
		ItemStack stack
	) {
		if (inventory != null && inventory.player instanceof ServerPlayer serverPlayer) {
			MadokuItemsManager.applyConfiguredItemLevel(stack, 1);
			MadokuRarityManager.applyGeneratedRarity(stack, serverPlayer.getRandom(), serverPlayer);
		}
		PetHagManager.applyLore(stack);
		return inventory.add(stack);
	}
}

package madoku.craft.mixin.core;

import madoku.craft.pet.PetHagAPIManager;
import madoku.craft.pet.PetEntitiesAPIManager;
import madoku.craft.core.rarity.RarityAPIManager;
import madoku.craft.items.ItemsAPIManager;
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
			if (PetEntitiesAPIManager.isPetItem(stack)) {
				RarityAPIManager.Tier rarity = RarityAPIManager.fromString(PetHagAPIManager.rarity(stack));
				RarityAPIManager.applyConfiguredRarity(
					stack,
					rarity == null ? RarityAPIManager.Tier.COMMON : rarity
				);
			} else {
				ItemsAPIManager.applyConfiguredItemLevel(stack, 1);
				RarityAPIManager.applyGeneratedRarity(stack, serverPlayer.getRandom(), serverPlayer);
			}
		}
		PetHagAPIManager.applyLore(stack);
		return inventory.add(stack);
	}
}

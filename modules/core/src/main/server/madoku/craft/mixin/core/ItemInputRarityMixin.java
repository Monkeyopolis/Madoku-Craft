package madoku.craft.mixin.core;

import madoku.craft.java.core.iteminput.ItemInputFeatureAPIManager;
import madoku.craft.java.core.rarity.RarityAPIManager;
import net.minecraft.server.commands.GiveCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies Core rarity behavior to stacks created through /give. */
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
			if (ItemInputFeatureAPIManager.isPetItem(stack)) {
				RarityAPIManager.Tier rarity = ItemInputFeatureAPIManager.petRarity(stack);
				RarityAPIManager.applyConfiguredRarity(
					stack,
					rarity == null ? RarityAPIManager.Tier.COMMON : rarity
				);
			} else {
				ItemInputFeatureAPIManager.applyItemLevel(stack, 1);
				RarityAPIManager.applyGeneratedRarity(stack, serverPlayer.getRandom(), serverPlayer);
			}
			ItemInputFeatureAPIManager.applyPetLore(stack);
		}
		return inventory.add(stack);
	}
}

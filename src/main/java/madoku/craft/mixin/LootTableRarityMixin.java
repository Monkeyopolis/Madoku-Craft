package madoku.craft.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import madoku.craft.pet.PlayerEntitiesSystem;
import madoku.craft.rarity.MadokuRarity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LootTable.class)
public class LootTableRarityMixin {
	@Inject(
		method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
		at = @At("RETURN")
	)
	private void madokuCraft$applyRarityToGeneratedLoot(
		LootContext lootContext,
		CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir
	) {
		ObjectArrayList<ItemStack> stacks = cir.getReturnValue();
		if (stacks == null || stacks.isEmpty()) {
			return;
		}

		RandomSource random = lootContext == null ? null : lootContext.getRandom();
		for (ItemStack stack : stacks) {
			MadokuRarity.applyGeneratedRarity(stack, random);
			PlayerEntitiesSystem.applySupportedSpawnEggLore(stack);
		}
	}
}

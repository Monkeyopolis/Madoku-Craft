package madoku.craft.mixin.core;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import madoku.craft.attributes.LuckAPIManager;
import madoku.craft.core.rarity.RarityAPIManager;
import madoku.craft.pet.PetHagAPIManager;
import madoku.craft.items.ItemsAPIManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

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

		for (ItemStack stack : stacks) {
			madokuCraft$applyGeneratedLoot(lootContext, stack);
		}
	}

	/**
	 * Container loot normally uses getRandomItemsRaw directly, so wrap its vanilla
	 * consumer to apply item level/lore before the stack enters the container.
	 */
	@ModifyVariable(
		method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V",
		at = @At("HEAD"),
		argsOnly = true
	)
	private Consumer<ItemStack> madokuCraft$wrapRawLootConsumer(
		Consumer<ItemStack> currentConsumer,
		LootContext lootContext,
		Consumer<ItemStack> consumer
	) {
		if (currentConsumer == null) {
			return null;
		}
		return stack -> {
			madokuCraft$applyGeneratedLoot(lootContext, stack);
			currentConsumer.accept(stack);
		};
	}

	private static void madokuCraft$applyGeneratedLoot(LootContext lootContext, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		RandomSource random = lootContext == null ? null : lootContext.getRandom();
		ItemsAPIManager.applyGeneratedItemLevel(stack, random);
		RarityAPIManager.applyGeneratedRarity(stack, random,
			LuckAPIManager.resolveLootPlayer(lootContext));
		PetHagAPIManager.applyLore(stack);
	}
}

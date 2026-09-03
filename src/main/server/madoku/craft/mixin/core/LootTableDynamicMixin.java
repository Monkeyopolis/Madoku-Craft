package madoku.craft.mixin.core;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import madoku.craft.java.attributes.LuckAPIManager;
import madoku.craft.java.core.loot.LootTableAPIManager;
import madoku.craft.java.core.rarity.RarityAPIManager;
import madoku.craft.java.pet.PetHagAPIManager;
import madoku.craft.java.items.ItemsAPIManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Consumer;

@Mixin(LootTable.class)
public class LootTableDynamicMixin {
	@Inject(
		method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$overrideWithDynamicLoot(
		LootContext lootContext,
		CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir
	) {
		List<ItemStack> generated = LootTableAPIManager.generateManagedLootForContext(lootContext);
		if (generated == null) {
			return;
		}
		ObjectArrayList<ItemStack> resolved = new ObjectArrayList<>(generated);
		applyRarity(lootContext, resolved);
		cir.setReturnValue(resolved);
	}

	@Inject(
		method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$overrideRawDynamicLoot(
		LootContext lootContext,
		Consumer<ItemStack> consumer,
		CallbackInfo ci
	) {
		List<ItemStack> generated = LootTableAPIManager.generateManagedLootForContext(lootContext);
		if (generated == null) {
			return;
		}
		ObjectArrayList<ItemStack> resolved = new ObjectArrayList<>(generated);
		applyRarity(lootContext, resolved);
		for (ItemStack stack : resolved) {
			if (stack != null && !stack.isEmpty()) {
				consumer.accept(stack);
			}
		}
		ci.cancel();
	}

	private static void applyRarity(LootContext lootContext, ObjectArrayList<ItemStack> stacks) {
		if (stacks == null || stacks.isEmpty()) {
			return;
		}

		RandomSource random = lootContext == null ? null : lootContext.getRandom();
		for (ItemStack stack : stacks) {
			ItemsAPIManager.applyGeneratedItemLevel(stack, random);
			RarityAPIManager.applyGeneratedRarity(stack, random,
				LuckAPIManager.resolveLootPlayer(lootContext));
			PetHagAPIManager.applyLore(stack);
		}
	}
}


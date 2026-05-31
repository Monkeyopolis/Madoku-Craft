package madoku.craft.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import madoku.craft.loot.system.MadokuLootTableManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

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
		List<ItemStack> generated = MadokuLootTableManager.generateManagedLootForContext(lootContext);
		if (generated == null) {
			return;
		}
		cir.setReturnValue(new ObjectArrayList<>(generated));
	}
}



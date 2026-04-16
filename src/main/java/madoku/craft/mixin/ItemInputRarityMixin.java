package madoku.craft.mixin;

import madoku.craft.rarity.MadokuRarity;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemInput.class)
public class ItemInputRarityMixin {
	@Inject(
		method = "createItemStack",
		at = @At("RETURN")
	)
	private void madokuCraft$applyRarityToCommandStacks(
		int count,
		boolean checkOverstack,
		CallbackInfoReturnable<ItemStack> cir
	) {
		MadokuRarity.applyGeneratedRarity(cir.getReturnValue(), RandomSource.create());
	}
}

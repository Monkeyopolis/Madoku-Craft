package madoku.craft.mixin.itemstack;

import com.mojang.serialization.Codec;
import madoku.craft.itemstack.system.MadokuItemStack;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemStack.class)
public abstract class ItemStackCodecMixin {
	@Redirect(
		method = "lambda$static$1",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/util/ExtraCodecs;intRange(II)Lcom/mojang/serialization/Codec;"
		)
	)
	private static Codec<Integer> madokuCraft$extendItemStackCountCodec(int min, int max) {
		return Codec.INT.flatXmap(
			value -> MadokuItemStack.validateCodecCount(min, max, value),
			value -> MadokuItemStack.validateCodecCount(min, max, value)
		);
	}
}



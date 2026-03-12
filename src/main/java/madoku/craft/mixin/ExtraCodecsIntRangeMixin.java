package madoku.craft.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import madoku.craft.itemstack.system.MadokuItemStack;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ExtraCodecs.class)
public abstract class ExtraCodecsIntRangeMixin {
	@Overwrite
	public static Codec<Integer> intRange(int min, int max) {
		int upper = MadokuItemStack.shouldExtendCodecRange(min, max)
			? MadokuItemStack.getCodecUpperBound(max)
			: max;

		return Codec.INT.flatXmap(
			value -> {
				if (value < min) {
					return DataResult.error(() -> "Value must be within range [" + min + ";" + upper + "]: " + value);
				}

				if (value > upper) {
					return DataResult.success(upper);
				}

				return DataResult.success(value);
			},
			value -> value.compareTo(min) >= 0 && value.compareTo(upper) <= 0
				? DataResult.success(value)
				: DataResult.error(() -> "Value must be within range [" + min + ";" + upper + "]: " + value)
		);
	}
}

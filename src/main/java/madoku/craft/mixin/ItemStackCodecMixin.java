package madoku.craft.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import madoku.craft.itemstack.system.MadokuItemStack;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(ItemStack.class)
public abstract class ItemStackCodecMixin {
	@Shadow @Final @Mutable private static MapCodec<ItemStack> MAP_CODEC;
	@Shadow @Final @Mutable public static Codec<ItemStack> CODEC;
	@Shadow @Final @Mutable public static Codec<ItemStack> STRICT_CODEC;
	@Shadow @Final @Mutable public static StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_STREAM_CODEC;
	@Shadow @Final @Mutable public static StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_UNTRUSTED_STREAM_CODEC;
	@Shadow @Final @Mutable public static StreamCodec<RegistryFriendlyByteBuf, ItemStack> STREAM_CODEC;
	@Shadow @Final @Mutable public static StreamCodec<RegistryFriendlyByteBuf, java.util.List<ItemStack>> OPTIONAL_LIST_STREAM_CODEC;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void madokuCraft$replaceStackCodecs(CallbackInfo ci) {
		Codec<Integer> countCodec = Codec.INT.flatXmap(
			value -> MadokuItemStack.validateCodecCount(1, 99, value),
			value -> MadokuItemStack.validateCodecCount(1, 99, value)
		);

		MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Item.CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
			countCodec.fieldOf("count").orElse(1).forGetter(ItemStack::getCount),
			DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ItemStack::getComponentsPatch)
		).apply(instance, (Holder<Item> item, Integer count, DataComponentPatch patch) -> new ItemStack(item, count, patch)));

		CODEC = Codec.lazyInitialized(() -> MAP_CODEC.codec());
		STRICT_CODEC = CODEC.validate(ItemStack::validateStrict);

		OPTIONAL_STREAM_CODEC = madokuCraft$createOptionalStreamCodec(DataComponentPatch.STREAM_CODEC);
		OPTIONAL_UNTRUSTED_STREAM_CODEC = madokuCraft$createOptionalStreamCodec(DataComponentPatch.DELIMITED_STREAM_CODEC);
		STREAM_CODEC = madokuCraft$validatedStreamCodec(OPTIONAL_STREAM_CODEC);
		OPTIONAL_LIST_STREAM_CODEC = OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.collection(ArrayList::new));
	}

	private static StreamCodec<RegistryFriendlyByteBuf, ItemStack> madokuCraft$createOptionalStreamCodec(
		StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> patchCodec
	) {
		return new StreamCodec<>() {
			@Override
			public ItemStack decode(RegistryFriendlyByteBuf buf) {
				int count = buf.readVarInt();
				if (count <= 0) {
					return ItemStack.EMPTY;
				}
				Holder<Item> item = Item.STREAM_CODEC.decode(buf);
				DataComponentPatch patch = patchCodec.decode(buf);
				return new ItemStack(item, count, patch);
			}

			@Override
			public void encode(RegistryFriendlyByteBuf buf, ItemStack stack) {
				if (stack.isEmpty()) {
					buf.writeVarInt(0);
					return;
				}
				buf.writeVarInt(stack.getCount());
				Item.STREAM_CODEC.encode(buf, stack.getItemHolder());
				patchCodec.encode(buf, stack.getComponentsPatch());
			}
		};
	}

	private static StreamCodec<RegistryFriendlyByteBuf, ItemStack> madokuCraft$validatedStreamCodec(
		StreamCodec<RegistryFriendlyByteBuf, ItemStack> codec
	) {
		return new StreamCodec<>() {
			@Override
			public ItemStack decode(RegistryFriendlyByteBuf buf) {
				ItemStack stack = codec.decode(buf);
				if (!stack.isEmpty()) {
					CODEC.encodeStart(buf.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), stack)
						.getOrThrow(error -> new IllegalArgumentException("Invalid ItemStack for stream codec: " + error));
				}
				return stack;
			}

			@Override
			public void encode(RegistryFriendlyByteBuf buf, ItemStack stack) {
				codec.encode(buf, stack);
			}
		};
	}
}

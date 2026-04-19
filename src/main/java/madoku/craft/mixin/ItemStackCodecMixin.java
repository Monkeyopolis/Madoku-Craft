package madoku.craft.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import madoku.craft.itemstack.system.MadokuItemStack;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Optional;

@Mixin(ItemStack.class)
public abstract class ItemStackCodecMixin {
	@Shadow @Final @Mutable public static Codec<ItemStack> CODEC;
	@Shadow @Final @Mutable public static Codec<ItemStack> SINGLE_ITEM_CODEC;
	@Shadow @Final @Mutable public static Codec<ItemStack> STRICT_CODEC;
	@Shadow @Final @Mutable public static Codec<ItemStack> STRICT_SINGLE_ITEM_CODEC;
	@Shadow @Final @Mutable public static Codec<ItemStack> OPTIONAL_CODEC;
	@Shadow @Final @Mutable public static Codec<ItemStack> SIMPLE_ITEM_CODEC;
	@Shadow @Final @Mutable public static StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_STREAM_CODEC;
	@Shadow @Final @Mutable public static StreamCodec<RegistryFriendlyByteBuf, ItemStack> STREAM_CODEC;
	@Shadow @Final @Mutable public static StreamCodec<RegistryFriendlyByteBuf, java.util.List<ItemStack>> OPTIONAL_LIST_STREAM_CODEC;
	@Shadow @Final @Mutable public static StreamCodec<RegistryFriendlyByteBuf, java.util.List<ItemStack>> LIST_STREAM_CODEC;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void madokuCraft$replaceStackCodecs(CallbackInfo ci) {
		Codec<Integer> countCodec = Codec.INT.flatXmap(
			value -> MadokuItemStack.validateCodecCount(1, 99, value),
			value -> MadokuItemStack.validateCodecCount(1, 99, value)
		);

		CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(instance -> instance.group(
			ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
			countCodec.fieldOf("count").orElse(1).forGetter(ItemStack::getCount),
			DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ItemStack::getComponentsPatch)
		).apply(instance, ItemStack::new)));
		SINGLE_ITEM_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(instance -> instance.group(
			ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
			DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ItemStack::getComponentsPatch)
		).apply(instance, (item, patch) -> new ItemStack(item, 1, patch))));
		STRICT_CODEC = CODEC.validate(ItemStackCodecMixin::madokuCraft$validateStrict);
		STRICT_SINGLE_ITEM_CODEC = SINGLE_ITEM_CODEC.validate(ItemStackCodecMixin::madokuCraft$validateStrict);
		OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC).xmap(
			optional -> optional.orElse(ItemStack.EMPTY),
			stack -> stack.isEmpty() ? Optional.empty() : Optional.of(stack)
		);
		SIMPLE_ITEM_CODEC = ItemStack.ITEM_NON_AIR_CODEC.xmap(
			item -> new ItemStack(item, 1),
			ItemStack::getItemHolder
		);

		OPTIONAL_STREAM_CODEC = madokuCraft$createOptionalStreamCodec(DataComponentPatch.STREAM_CODEC);
		STREAM_CODEC = ItemStack.validatedStreamCodec(OPTIONAL_STREAM_CODEC);
		OPTIONAL_LIST_STREAM_CODEC = OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.collection(ArrayList::new));
		LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.collection(ArrayList::new));
	}

	private static StreamCodec<RegistryFriendlyByteBuf, ItemStack> madokuCraft$createOptionalStreamCodec(
		StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> patchCodec
	) {
		StreamCodec<RegistryFriendlyByteBuf, net.minecraft.core.Holder<net.minecraft.world.item.Item>> itemCodec =
			ByteBufCodecs.holderRegistry(Registries.ITEM);
		return new StreamCodec<>() {
			@Override
			public ItemStack decode(RegistryFriendlyByteBuf buf) {
				int count = buf.readVarInt();
				if (count <= 0) {
					return ItemStack.EMPTY;
				}
				net.minecraft.core.Holder<net.minecraft.world.item.Item> item = itemCodec.decode(buf);
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
				itemCodec.encode(buf, stack.getItemHolder());
				patchCodec.encode(buf, stack.getComponentsPatch());
			}
		};
	}

	private static DataResult<ItemStack> madokuCraft$validateStrict(ItemStack stack) {
		DataResult<net.minecraft.util.Unit> components = ItemStack.validateComponents(stack.getComponents());
		if (components.isError()) {
			return components.map(ignored -> stack);
		}
		if (stack.getCount() > stack.getMaxStackSize()) {
			return DataResult.error(() -> "Item stack with stack size of " + stack.getCount() + " was larger than maximum: " + stack.getMaxStackSize());
		}
		return DataResult.success(stack);
	}
}

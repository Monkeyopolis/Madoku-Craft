package madoku.craft.items;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ItemsPayloadManager(String snapshot) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ItemsPayloadManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "item_profile_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ItemsPayloadManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			ItemsPayloadManager::snapshot,
			ItemsPayloadManager::new
		);

	@Override
	public Type<ItemsPayloadManager> type() {
		return TYPE;
	}
}

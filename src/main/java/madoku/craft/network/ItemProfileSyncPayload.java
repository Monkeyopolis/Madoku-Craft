package madoku.craft.network;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ItemProfileSyncPayload(String snapshot) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ItemProfileSyncPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "item_profile_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ItemProfileSyncPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			ItemProfileSyncPayload::snapshot,
			ItemProfileSyncPayload::new
		);

	@Override
	public Type<ItemProfileSyncPayload> type() {
		return TYPE;
	}
}


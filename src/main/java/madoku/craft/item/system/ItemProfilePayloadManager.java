package madoku.craft.item.system;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ItemProfilePayloadManager(String snapshot) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ItemProfilePayloadManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "item_profile_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ItemProfilePayloadManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			ItemProfilePayloadManager::snapshot,
			ItemProfilePayloadManager::new
		);

	@Override
	public Type<ItemProfilePayloadManager> type() {
		return TYPE;
	}
}

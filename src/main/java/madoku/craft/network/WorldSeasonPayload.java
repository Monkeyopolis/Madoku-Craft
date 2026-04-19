package madoku.craft.network;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WorldSeasonPayload(String season) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<WorldSeasonPayload> TYPE =
		new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MadokuCraft.MOD_ID, "world_season"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WorldSeasonPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			WorldSeasonPayload::season,
			WorldSeasonPayload::new
		);

	@Override
	public Type<WorldSeasonPayload> type() {
		return TYPE;
	}
}


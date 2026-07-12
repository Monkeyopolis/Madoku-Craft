package madoku.craft.network;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record WorldSeasonPayload(String season, double temperatureOffset, double humidityOffset) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<WorldSeasonPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "world_season"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WorldSeasonPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			WorldSeasonPayload::season,
			ByteBufCodecs.DOUBLE,
			WorldSeasonPayload::temperatureOffset,
			ByteBufCodecs.DOUBLE,
			WorldSeasonPayload::humidityOffset,
			WorldSeasonPayload::new
		);

	@Override
	public Type<WorldSeasonPayload> type() {
		return TYPE;
	}
}


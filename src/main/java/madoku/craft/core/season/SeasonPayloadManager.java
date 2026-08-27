package madoku.craft.core.season;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SeasonPayloadManager(
	String season,
	double temperatureOffset,
	double humidityOffset,
	String weatherCondition,
	int seasonDay,
	int seasonLengthDays
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SeasonPayloadManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "world_season"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SeasonPayloadManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			SeasonPayloadManager::season,
			ByteBufCodecs.DOUBLE,
			SeasonPayloadManager::temperatureOffset,
			ByteBufCodecs.DOUBLE,
			SeasonPayloadManager::humidityOffset,
			ByteBufCodecs.STRING_UTF8,
			SeasonPayloadManager::weatherCondition,
			ByteBufCodecs.VAR_INT,
			SeasonPayloadManager::seasonDay,
			ByteBufCodecs.VAR_INT,
			SeasonPayloadManager::seasonLengthDays,
			SeasonPayloadManager::new
		);

	@Override
	public Type<SeasonPayloadManager> type() {
		return TYPE;
	}
}

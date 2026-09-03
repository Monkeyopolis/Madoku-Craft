package madoku.craft.core.season;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SeasonPayloadAPIManager(
	String season,
	double temperatureOffset,
	double humidityOffset,
	String weatherCondition,
	int seasonDay,
	int seasonLengthDays
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SeasonPayloadAPIManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "world_season"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SeasonPayloadAPIManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			SeasonPayloadAPIManager::season,
			ByteBufCodecs.DOUBLE,
			SeasonPayloadAPIManager::temperatureOffset,
			ByteBufCodecs.DOUBLE,
			SeasonPayloadAPIManager::humidityOffset,
			ByteBufCodecs.STRING_UTF8,
			SeasonPayloadAPIManager::weatherCondition,
			ByteBufCodecs.VAR_INT,
			SeasonPayloadAPIManager::seasonDay,
			ByteBufCodecs.VAR_INT,
			SeasonPayloadAPIManager::seasonLengthDays,
			SeasonPayloadAPIManager::new
		);

	@Override
	public Type<SeasonPayloadAPIManager> type() {
		return TYPE;
	}
}


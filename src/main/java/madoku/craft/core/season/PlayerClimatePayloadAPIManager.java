package madoku.craft.core.season;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-authoritative climate values for the local player HUD. */
public record PlayerClimatePayloadAPIManager(double temperature, double humidity) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PlayerClimatePayloadAPIManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "player_climate"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerClimatePayloadAPIManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.DOUBLE,
			PlayerClimatePayloadAPIManager::temperature,
			ByteBufCodecs.DOUBLE,
			PlayerClimatePayloadAPIManager::humidity,
			PlayerClimatePayloadAPIManager::new
		);

	@Override
	public Type<PlayerClimatePayloadAPIManager> type() {
		return TYPE;
	}
}


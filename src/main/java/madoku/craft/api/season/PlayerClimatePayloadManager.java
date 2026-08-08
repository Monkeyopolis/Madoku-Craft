package madoku.craft.api.season;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-authoritative climate values for the local player HUD. */
public record PlayerClimatePayloadManager(double temperature, double humidity) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PlayerClimatePayloadManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "player_climate"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerClimatePayloadManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.DOUBLE,
			PlayerClimatePayloadManager::temperature,
			ByteBufCodecs.DOUBLE,
			PlayerClimatePayloadManager::humidity,
			PlayerClimatePayloadManager::new
		);

	@Override
	public Type<PlayerClimatePayloadManager> type() {
		return TYPE;
	}
}

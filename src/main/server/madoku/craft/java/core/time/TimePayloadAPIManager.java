package madoku.craft.java.core.time;

import madoku.craft.java.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TimePayloadAPIManager(long day, int hour, int minute) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TimePayloadAPIManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "world_time"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TimePayloadAPIManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_LONG,
			TimePayloadAPIManager::day,
			ByteBufCodecs.VAR_INT,
			TimePayloadAPIManager::hour,
			ByteBufCodecs.VAR_INT,
			TimePayloadAPIManager::minute,
			TimePayloadAPIManager::new
		);

	@Override
	public Type<TimePayloadAPIManager> type() {
		return TYPE;
	}
}


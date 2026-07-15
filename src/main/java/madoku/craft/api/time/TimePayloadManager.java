package madoku.craft.api.time;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TimePayloadManager(long day, int hour, int minute) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TimePayloadManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "world_time"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TimePayloadManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_LONG,
			TimePayloadManager::day,
			ByteBufCodecs.VAR_INT,
			TimePayloadManager::hour,
			ByteBufCodecs.VAR_INT,
			TimePayloadManager::minute,
			TimePayloadManager::new
		);

	@Override
	public Type<TimePayloadManager> type() {
		return TYPE;
	}
}

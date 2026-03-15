package madoku.craft.network;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WorldTimePayload(long day, int hour, int minute) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<WorldTimePayload> TYPE =
		new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MadokuCraft.MOD_ID, "world_time"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WorldTimePayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_LONG,
			WorldTimePayload::day,
			ByteBufCodecs.VAR_INT,
			WorldTimePayload::hour,
			ByteBufCodecs.VAR_INT,
			WorldTimePayload::minute,
			WorldTimePayload::new
		);

	@Override
	public Type<WorldTimePayload> type() {
		return TYPE;
	}
}

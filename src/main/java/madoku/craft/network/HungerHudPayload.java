package madoku.craft.network;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HungerHudPayload(int current, int pending, int max) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<HungerHudPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "hunger_hud"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HungerHudPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT,
			HungerHudPayload::current,
			ByteBufCodecs.VAR_INT,
			HungerHudPayload::pending,
			ByteBufCodecs.VAR_INT,
			HungerHudPayload::max,
			HungerHudPayload::new
		);

	@Override
	public Type<HungerHudPayload> type() {
		return TYPE;
	}
}

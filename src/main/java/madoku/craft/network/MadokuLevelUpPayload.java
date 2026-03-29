package madoku.craft.network;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MadokuLevelUpPayload(String statId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MadokuLevelUpPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_level_up"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MadokuLevelUpPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			MadokuLevelUpPayload::statId,
			MadokuLevelUpPayload::new
		);

	@Override
	public Type<MadokuLevelUpPayload> type() {
		return TYPE;
	}
}

package madoku.craft.network;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PetSoundStatePayload(String petUuid, String itemId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PetSoundStatePayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "pet_sound_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PetSoundStatePayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			PetSoundStatePayload::petUuid,
			ByteBufCodecs.STRING_UTF8,
			PetSoundStatePayload::itemId,
			PetSoundStatePayload::new
		);

	@Override
	public Type<PetSoundStatePayload> type() {
		return TYPE;
	}
}

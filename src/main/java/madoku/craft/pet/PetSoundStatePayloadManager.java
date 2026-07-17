package madoku.craft.pet;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PetSoundStatePayloadManager(String petUuid, String itemId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PetSoundStatePayloadManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "pet_sound_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PetSoundStatePayloadManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			PetSoundStatePayloadManager::petUuid,
			ByteBufCodecs.STRING_UTF8,
			PetSoundStatePayloadManager::itemId,
			PetSoundStatePayloadManager::new
		);

	@Override
	public Type<PetSoundStatePayloadManager> type() {
		return TYPE;
	}
}

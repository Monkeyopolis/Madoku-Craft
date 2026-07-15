package madoku.craft.levels;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record LevelUpPayloadManager(String statId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<LevelUpPayloadManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_level_up"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LevelUpPayloadManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			LevelUpPayloadManager::statId,
			LevelUpPayloadManager::new
		);

	@Override
	public Type<LevelUpPayloadManager> type() {
		return TYPE;
	}
}

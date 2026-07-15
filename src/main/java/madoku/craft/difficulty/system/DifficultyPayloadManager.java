package madoku.craft.difficulty.system;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DifficultyPayloadManager(int level) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DifficultyPayloadManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "world_difficulty"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DifficultyPayloadManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT,
			DifficultyPayloadManager::level,
			DifficultyPayloadManager::new
		);

	@Override
	public Type<DifficultyPayloadManager> type() {
		return TYPE;
	}
}

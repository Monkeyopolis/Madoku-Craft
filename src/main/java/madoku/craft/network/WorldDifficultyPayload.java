package madoku.craft.network;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record WorldDifficultyPayload(int level) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<WorldDifficultyPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "world_difficulty"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WorldDifficultyPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT,
			WorldDifficultyPayload::level,
			WorldDifficultyPayload::new
		);

	@Override
	public Type<WorldDifficultyPayload> type() {
		return TYPE;
	}
}

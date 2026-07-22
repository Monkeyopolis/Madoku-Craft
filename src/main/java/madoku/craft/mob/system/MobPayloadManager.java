package madoku.craft.mob.system;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Network payload owned by the mob subsystem for regional difficulty HUD state. */
public record MobPayloadManager(int level) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MobPayloadManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "world_difficulty"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MobPayloadManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT,
			MobPayloadManager::level,
			MobPayloadManager::new
		);

	@Override
	public Type<MobPayloadManager> type() {
		return TYPE;
	}
}

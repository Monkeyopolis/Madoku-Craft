package madoku.craft.network;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MadokuLevelsPayload(
	String username,
	int level,
	int currentXp,
	int requiredXp,
	int availablePoints,
	int maxStatLevel,
	boolean useAttributesContainer,
	String visibleStats,
	String statLevels
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MadokuLevelsPayload> TYPE =
		new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_levels"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MadokuLevelsPayload> CODEC =
		new StreamCodec<>() {
			@Override
			public MadokuLevelsPayload decode(RegistryFriendlyByteBuf buf) {
				return new MadokuLevelsPayload(
					ByteBufCodecs.STRING_UTF8.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.BOOL.decode(buf),
					ByteBufCodecs.STRING_UTF8.decode(buf),
					ByteBufCodecs.STRING_UTF8.decode(buf)
				);
			}

			@Override
			public void encode(RegistryFriendlyByteBuf buf, MadokuLevelsPayload payload) {
				ByteBufCodecs.STRING_UTF8.encode(buf, payload.username());
				ByteBufCodecs.VAR_INT.encode(buf, payload.level());
				ByteBufCodecs.VAR_INT.encode(buf, payload.currentXp());
				ByteBufCodecs.VAR_INT.encode(buf, payload.requiredXp());
				ByteBufCodecs.VAR_INT.encode(buf, payload.availablePoints());
				ByteBufCodecs.VAR_INT.encode(buf, payload.maxStatLevel());
				ByteBufCodecs.BOOL.encode(buf, payload.useAttributesContainer());
				ByteBufCodecs.STRING_UTF8.encode(buf, payload.visibleStats());
				ByteBufCodecs.STRING_UTF8.encode(buf, payload.statLevels());
			}
		};

	@Override
	public Type<MadokuLevelsPayload> type() {
		return TYPE;
	}
}


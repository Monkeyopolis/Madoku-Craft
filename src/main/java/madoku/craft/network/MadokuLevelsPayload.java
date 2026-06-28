package madoku.craft.network;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

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
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_levels"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MadokuLevelsPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			MadokuLevelsPayload::username,
			ByteBufCodecs.VAR_INT,
			MadokuLevelsPayload::level,
			ByteBufCodecs.VAR_INT,
			MadokuLevelsPayload::currentXp,
			ByteBufCodecs.VAR_INT,
			MadokuLevelsPayload::requiredXp,
			ByteBufCodecs.VAR_INT,
			MadokuLevelsPayload::availablePoints,
			ByteBufCodecs.VAR_INT,
			MadokuLevelsPayload::maxStatLevel,
			ByteBufCodecs.BOOL,
			MadokuLevelsPayload::useAttributesContainer,
			ByteBufCodecs.STRING_UTF8,
			MadokuLevelsPayload::visibleStats,
			ByteBufCodecs.STRING_UTF8,
			MadokuLevelsPayload::statLevels,
			MadokuLevelsPayload::new
		);

	@Override
	public Type<MadokuLevelsPayload> type() {
		return TYPE;
	}
}


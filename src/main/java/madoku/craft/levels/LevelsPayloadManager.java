package madoku.craft.levels;

import madoku.craft.MadokuCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record LevelsPayloadManager(
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
	public static final CustomPacketPayload.Type<LevelsPayloadManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_levels"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LevelsPayloadManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			LevelsPayloadManager::username,
			ByteBufCodecs.VAR_INT,
			LevelsPayloadManager::level,
			ByteBufCodecs.VAR_INT,
			LevelsPayloadManager::currentXp,
			ByteBufCodecs.VAR_INT,
			LevelsPayloadManager::requiredXp,
			ByteBufCodecs.VAR_INT,
			LevelsPayloadManager::availablePoints,
			ByteBufCodecs.VAR_INT,
			LevelsPayloadManager::maxStatLevel,
			ByteBufCodecs.BOOL,
			LevelsPayloadManager::useAttributesContainer,
			ByteBufCodecs.STRING_UTF8,
			LevelsPayloadManager::visibleStats,
			ByteBufCodecs.STRING_UTF8,
			LevelsPayloadManager::statLevels,
			LevelsPayloadManager::new
		);

	@Override
	public Type<LevelsPayloadManager> type() {
		return TYPE;
	}
}

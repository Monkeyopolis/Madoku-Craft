package madoku.craft.levels;

import madoku.craft.MadokuCraft;
import madoku.craft.levels.MadokuLevelsManager.LevelStat;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Owns Madoku Levels network payloads and builds synchronized player snapshots. */
public final class LevelsPayloadManager {
	private LevelsPayloadManager() { }

	public static void initialize() {
		ServerPlayNetworking.registerGlobalReceiver(LevelUpPayload.TYPE, (payload, context) ->
			MadokuLevelsManager.handleLevelUpRequest(context.player(), payload.statId()));
	}

	public static void reset() { }

	public static Payload createPayload(ServerPlayer player) {
		LevelsPlayerManager.PlayerState state = LevelsPlayerManager.state(player);
		return new Payload(
			player.getName().getString(),
			Math.min(state.level(), LevelsPlayerManager.maxPlayerLevel()),
			state.level() >= LevelsPlayerManager.maxPlayerLevel() ? 0 : state.currentXp(),
			LevelsPlayerManager.requiredXpForLevel(state.level()),
			state.availablePoints(),
			encodeMaxStatLevels(),
			MadokuLevelsManager.useAttributesContainer(),
			LevelStat.encodeVisibleStats(LevelStat.visibleStats()),
			LevelStat.encodeLevels(stateLevels(state))
		);
	}

	private static java.util.Map<LevelStat, Integer> stateLevels(LevelsPlayerManager.PlayerState state) {
		java.util.EnumMap<LevelStat, Integer> levels = LevelStat.createDefaultLevels();
		for (LevelStat stat : LevelStat.values()) levels.put(stat, state.statLevel(stat));
		return levels;
	}

	private static String encodeMaxStatLevels() {
		StringBuilder builder = new StringBuilder();
		for (LevelStat stat : LevelStat.values()) {
			if (builder.length() > 0) builder.append(';');
			builder.append(stat.id()).append('=').append(stat.maxLevel());
		}
		return builder.toString();
	}

	public record Payload(
		String username,
		int level,
		int currentXp,
		int requiredXp,
		int availablePoints,
		String maxStatLevels,
		boolean useAttributesContainer,
		String visibleStats,
		String statLevels
	) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Payload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_levels"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Payload::username,
			ByteBufCodecs.VAR_INT, Payload::level,
			ByteBufCodecs.VAR_INT, Payload::currentXp,
			ByteBufCodecs.VAR_INT, Payload::requiredXp,
			ByteBufCodecs.VAR_INT, Payload::availablePoints,
			ByteBufCodecs.STRING_UTF8, Payload::maxStatLevels,
			ByteBufCodecs.BOOL, Payload::useAttributesContainer,
			ByteBufCodecs.STRING_UTF8, Payload::visibleStats,
			ByteBufCodecs.STRING_UTF8, Payload::statLevels,
			Payload::new
		);

		@Override public Type<Payload> type() { return TYPE; }
	}

	public record LevelUpPayload(String statId) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<LevelUpPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_level_up"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LevelUpPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, LevelUpPayload::statId, LevelUpPayload::new
		);

		@Override public Type<LevelUpPayload> type() { return TYPE; }
	}
}

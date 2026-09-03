package madoku.craft.java.levels;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import madoku.craft.java.MadokuCraft;

/** Public contract for Madoku Levels network payloads. */
public final class LevelsPayloadAPIManager {
	private static final LevelsPayloadProvider UNAVAILABLE_PROVIDER = new LevelsPayloadProvider() { };
	private static volatile LevelsPayloadProvider provider = UNAVAILABLE_PROVIDER;

	private LevelsPayloadAPIManager() {
	}

	public static void registerProvider(LevelsPayloadProvider candidate) { if (candidate == null) throw new IllegalArgumentException("Levels payload provider must not be null."); provider = candidate; }
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static Payload createPayload(ServerPlayer player) { return provider.createPayload(player); }

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
		public static final Type<Payload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_levels"));
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
		public static final Type<LevelUpPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_level_up"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LevelUpPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, LevelUpPayload::statId, LevelUpPayload::new
		);

		@Override public Type<LevelUpPayload> type() { return TYPE; }
	}
}

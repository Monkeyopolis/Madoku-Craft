package madoku.craft.java.levels;

import madoku.craft.java.core.data.DataSaveParticipant;
import madoku.craft.java.core.data.DataSaveParticipantAPIManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Public contract for Madoku Levels player progression and persistence. */
public final class LevelsPlayerAPIManager {
	private static final LevelsPlayerProvider UNAVAILABLE_PROVIDER = new LevelsPlayerProvider() { };
	private static volatile LevelsPlayerProvider provider = UNAVAILABLE_PROVIDER;
	private static final DataSaveParticipant DATA_SAVE_PARTICIPANT = new DataSaveParticipant() {
		@Override public String id() { return "levels.players"; }
		@Override public void autosavePersistedData(MinecraftServer server) { LevelsPlayerAPIManager.autosavePersistedData(server); }
		@Override public void savePersistedData(MinecraftServer server) { LevelsPlayerAPIManager.savePersistedData(server); }
	};

	private LevelsPlayerAPIManager() {
	}

	public static void registerProvider(LevelsPlayerProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Levels player provider must not be null.");
		provider = candidate;
		DataSaveParticipantAPIManager.register(DATA_SAVE_PARTICIPANT);
	}
	public static void unregisterProvider() {
		provider = UNAVAILABLE_PROVIDER;
		DataSaveParticipantAPIManager.unregister(DATA_SAVE_PARTICIPANT.id());
	}
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static LevelsPlayerState state(ServerPlayer player) { return provider.state(player); }
	public static void addXp(ServerPlayer player, int xpAmount) { provider.addXp(player, xpAmount); }
	public static void upgradeStat(ServerPlayer player, String statId) { provider.upgradeStat(player, statId); }
	public static int getPlayerHungerBonusPoints(ServerPlayer player) { return provider.getPlayerHungerBonusPoints(player); }
	public static void loadPersistedData(MinecraftServer server) { provider.loadPersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { provider.autosavePersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { provider.savePersistedData(server); }
	public static void flushDirtySyncs(MinecraftServer server) { provider.flushDirtySyncs(server); }
	public static int maxPlayerLevel() { return provider.maxPlayerLevel(); }
	public static int requiredXpForLevel(int level) { return provider.requiredXpForLevel(level); }
}

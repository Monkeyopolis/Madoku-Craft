package madoku.craft.java.levels;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Public contract for Madoku Levels player progression and persistence. */
public final class LevelsPlayerAPIManager {
	private static final LevelsPlayerProvider UNAVAILABLE_PROVIDER = new LevelsPlayerProvider() { };
	private static volatile LevelsPlayerProvider provider = UNAVAILABLE_PROVIDER;

	private LevelsPlayerAPIManager() {
	}

	public static void registerProvider(LevelsPlayerProvider candidate) { if (candidate == null) throw new IllegalArgumentException("Levels player provider must not be null."); provider = candidate; }
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static LevelsPlayerManager.PlayerState state(ServerPlayer player) { return provider.state(player); }
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

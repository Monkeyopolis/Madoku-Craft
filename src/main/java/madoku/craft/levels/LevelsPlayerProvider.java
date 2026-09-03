package madoku.craft.levels;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Provider contract for Madoku Levels player progression. */
public interface LevelsPlayerProvider {
	default void initialize() { }
	default void reset() { }
	default LevelsPlayerManager.PlayerState state(ServerPlayer player) { return null; }
	default void addXp(ServerPlayer player, int xpAmount) { }
	default void upgradeStat(ServerPlayer player, String statId) { }
	default int getPlayerHungerBonusPoints(ServerPlayer player) { return 0; }
	default void loadPersistedData(MinecraftServer server) { }
	default void autosavePersistedData(MinecraftServer server) { }
	default void savePersistedData(MinecraftServer server) { }
	default void flushDirtySyncs(MinecraftServer server) { }
	default int maxPlayerLevel() { return 0; }
	default int requiredXpForLevel(int level) { return 0; }
}

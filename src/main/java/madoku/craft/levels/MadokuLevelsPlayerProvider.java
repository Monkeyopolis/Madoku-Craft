package madoku.craft.levels;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Built-in provider for Madoku Levels player progression. */
public final class MadokuLevelsPlayerProvider implements LevelsPlayerProvider {
	@Override public void initialize() { LevelsPlayerManager.initialize(); }
	@Override public void reset() { LevelsPlayerManager.reset(); }
	@Override public LevelsPlayerManager.PlayerState state(ServerPlayer player) { return LevelsPlayerManager.state(player); }
	@Override public void addXp(ServerPlayer player, int xpAmount) { LevelsPlayerManager.addXp(player, xpAmount); }
	@Override public void upgradeStat(ServerPlayer player, String statId) { LevelsPlayerManager.upgradeStat(player, statId); }
	@Override public int getPlayerHungerBonusPoints(ServerPlayer player) { return LevelsPlayerManager.getPlayerHungerBonusPoints(player); }
	@Override public void loadPersistedData(MinecraftServer server) { LevelsPlayerManager.loadPersistedData(server); }
	@Override public void autosavePersistedData(MinecraftServer server) { LevelsPlayerManager.autosavePersistedData(server); }
	@Override public void savePersistedData(MinecraftServer server) { LevelsPlayerManager.savePersistedData(server); }
	@Override public void flushDirtySyncs(MinecraftServer server) { LevelsPlayerManager.flushDirtySyncs(server); }
	@Override public int maxPlayerLevel() { return LevelsPlayerManager.maxPlayerLevel(); }
	@Override public int requiredXpForLevel(int level) { return LevelsPlayerManager.requiredXpForLevel(level); }
}

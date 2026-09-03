package madoku.craft.levels;

import net.minecraft.server.level.ServerPlayer;

/** Built-in provider for Madoku Levels attribute application. */
public final class MadokuLevelsAttributesProvider implements LevelsAttributesProvider {
	@Override public void initialize() { LevelsAttributesManager.initialize(); }
	@Override public void reset() { LevelsAttributesManager.reset(); }
	@Override public void applyPlayerAttributes(ServerPlayer player) { LevelsAttributesManager.applyPlayerAttributes(player); }
	@Override public int hungerBonusPoints(ServerPlayer player, int level) { return LevelsAttributesManager.hungerBonusPoints(player, level); }
	@Override public double valueAtLevel(ServerPlayer player, LevelStat stat, int level) { return LevelsAttributesManager.valueAtLevel(player, stat, level); }
}

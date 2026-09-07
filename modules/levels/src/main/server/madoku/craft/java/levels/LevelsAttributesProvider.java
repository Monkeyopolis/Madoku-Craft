package madoku.craft.java.levels;

import net.minecraft.server.level.ServerPlayer;

/** Provider contract for Madoku Levels attribute application. */
public interface LevelsAttributesProvider {
	default void initialize() { }
	default void reset() { }
	default void applyPlayerAttributes(ServerPlayer player) { }
	default int hungerBonusPoints(ServerPlayer player, int level) { return 0; }
	default double valueAtLevel(ServerPlayer player, LevelStat stat, int level) { return 0.0d; }
}

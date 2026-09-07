package madoku.craft.java.levels;

import java.util.EnumMap;

/** Public snapshot/value object shared by the Levels API and its runtime. */
public final class LevelsPlayerState {
	int level;
	int currentXp;
	int requiredXp;
	int availablePoints;
	final EnumMap<LevelStat, Integer> statLevels;

	LevelsPlayerState(int level, int currentXp, int requiredXp, int availablePoints, EnumMap<LevelStat, Integer> statLevels) {
		this.level = level;
		this.currentXp = currentXp;
		this.requiredXp = requiredXp;
		this.availablePoints = availablePoints;
		this.statLevels = statLevels;
	}

	static LevelsPlayerState defaults(int requiredXp) {
		return new LevelsPlayerState(1, 0, requiredXp, 1, LevelStat.createDefaultLevels());
	}

	public int level() { return level; }
	public int currentXp() { return currentXp; }
	public int requiredXp() { return requiredXp; }
	public int availablePoints() { return availablePoints; }
	public int statLevel(LevelStat stat) { return statLevels.getOrDefault(stat, LevelStat.DEFAULT_LEVEL); }
}
